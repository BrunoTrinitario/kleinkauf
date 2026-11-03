package com.kleinkauf.product.services;

import com.kleinkauf.common.audit.AuditEvent;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.audit.AuditSuppression;
import com.kleinkauf.product.dto.csv.ImportResultDto;
import com.kleinkauf.product.dto.csv.ImportResultDto.RowError;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductMetaData;
import com.kleinkauf.product.repositories.ProductMetaDataRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.util.ArgentineNumbers;
import com.kleinkauf.product.util.Slugs;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
Importar y exportar productos en CSV (solo administrador).
- Formato: separador ';', numeros en formato argentino (ver ArgentineNumbers), UTF-8 con BOM
  para que Excel en espanol lo abra bien con doble clic.
- Columnas: datos basicos, precio, costo y metadatos. No incluye stock: se maneja con movimientos.
- Importar: el nombre identifica al producto. Si existe se actualiza y una celda vacia no cambia el dato;
  si no existe se crea con stock 0. Es todo o nada: si hay algun error no se importa nada.
 */
@Service
public class ProductCsvService {

    public static final int MAX_ROWS = 5000;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    // Largo de las columnas de texto (varchar(255) por defecto en Hibernate)
    static final int MAX_TEXT = 255;
    // Precio y costo: numeric(12,2)
    static final int MAX_PRICE_INTEGER_DIGITS = 10;
    static final int MAX_PRICE_DECIMALS = 2;

    private static final char BOM = '﻿';

    enum Column {
        NOMBRE, CATEGORIA, DESCRIPCION, PRECIO, COSTO, MATERIAL, COLOR, ESTILO, ALTO, ANCHO, LARGO, PESO;

        String header() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Set<Column> TEXT = Set.of(
            Column.NOMBRE, Column.CATEGORIA, Column.DESCRIPCION, Column.MATERIAL, Column.COLOR, Column.ESTILO);
    private static final Set<Column> PRICES = Set.of(Column.PRECIO, Column.COSTO);
    private static final Set<Column> METADATA = Set.of(
            Column.MATERIAL, Column.COLOR, Column.ESTILO, Column.ALTO, Column.ANCHO, Column.LARGO, Column.PESO);

    private static final Map<String, Column> BY_HEADER = Arrays.stream(Column.values())
            .collect(Collectors.toMap(Column::header, Function.identity()));

    // Registro a mano de lo que no es un cambio de una entidad @Audited (docs/auditoria.md)
    @Autowired
    private AuditLog auditLog;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductMetaDataRepository productMetaDataRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CatalogCacheService catalogCache;

    public byte[] exportCsv() {
        // Lectura sensible: el CSV trae los costos
        auditLog.read("ProductExport", null);
        StringBuilder out = new StringBuilder().append(BOM);
        try (CSVPrinter printer = new CSVPrinter(out, format())) {
            printer.printRecord(headers());
            for (Product p : productRepository.findAllByOrderByNameAsc()) {
                printer.printRecord(toRow(p, p.getMetaData()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] template() {
        StringBuilder out = new StringBuilder().append(BOM);
        try (CSVPrinter printer = new CSVPrinter(out, format())) {
            printer.printRecord(headers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public ImportResultDto importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return failed(0, "The file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            return failed(0, "The file exceeds 5 MB");
        }

        List<CSVRecord> records;
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == BOM) {
                content = content.substring(1);
            }
            try (CSVParser parser = CSVParser.parse(content, format())) {
                records = parser.getRecords();
            }
        } catch (IOException | UncheckedIOException e) {
            return failed(0, "The file is not a valid CSV: " + e.getMessage());
        }

        if (records.isEmpty()) {
            return failed(0, "The file has no header row");
        }

        List<RowError> errors = new ArrayList<>();
        Map<Column, Integer> columns = readHeader(records.get(0), errors);
        if (!errors.isEmpty()) {
            return new ImportResultDto(0, 0, errors);
        }

        List<CSVRecord> data = records.subList(1, records.size());
        if (data.size() > MAX_ROWS) {
            return failed(0, "The file has more than " + MAX_ROWS + " products");
        }

        Map<String, Product> existing = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getName, Function.identity(), (a, b) -> a));
        Set<String> seen = new HashSet<>();
        List<ParsedRow> rows = new ArrayList<>();
        for (CSVRecord record : data) {
            ParsedRow row = parseRow(record, columns, existing, seen, errors);
            if (row != null) rows.add(row);
        }
        if (!errors.isEmpty()) {
            return new ImportResultDto(0, 0, errors);
        }

        // Todo o nada: una sola transaccion. En la auditoria va una entrada con el resumen, no una por producto
        int[] counts = new int[2];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AuditSuppression.run(() -> {
                for (ParsedRow row : rows) {
                    if (row.existing() == null) {
                        create(row);
                        counts[0]++;
                    } else {
                        update(row);
                        counts[1]++;
                    }
                }
            });
            auditLog.record(AuditEvent.IMPORT, "Product", null, Map.of(
                    "created", AuditLog.change(null, counts[0]),
                    "updated", AuditLog.change(null, counts[1])));
        });
        catalogCache.invalidate();
        return new ImportResultDto(counts[0], counts[1], List.of());
    }

    private Map<Column, Integer> readHeader(CSVRecord header, List<RowError> errors) {
        Map<Column, Integer> columns = new EnumMap<>(Column.class);
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i);
            if (raw.isBlank()) continue;
            Column column = BY_HEADER.get(normalize(raw));
            if (column == null) {
                errors.add(new RowError(1, "Unknown column: '" + raw + "'"));
            } else if (columns.containsKey(column)) {
                errors.add(new RowError(1, "Duplicated column: '" + raw + "'"));
            } else {
                columns.put(column, i);
            }
        }
        if (!columns.containsKey(Column.NOMBRE)) {
            errors.add(new RowError(1, "The 'nombre' column is required"));
        }
        return columns;
    }

    private ParsedRow parseRow(CSVRecord record, Map<Column, Integer> columns, Map<String, Product> existing,
                               Set<String> seen, List<RowError> errors) {
        int line = (int) record.getRecordNumber();
        int errorsBefore = errors.size();

        Map<Column, String> texts = new EnumMap<>(Column.class);
        Map<Column, BigDecimal> numbers = new EnumMap<>(Column.class);
        for (Map.Entry<Column, Integer> entry : columns.entrySet()) {
            Column column = entry.getKey();
            String value = entry.getValue() < record.size() ? record.get(entry.getValue()).trim() : "";
            if (value.isEmpty()) continue;

            if (TEXT.contains(column)) {
                if (value.length() > MAX_TEXT) {
                    errors.add(new RowError(line, "'" + column.header() + "' has more than " + MAX_TEXT + " characters"));
                } else {
                    texts.put(column, value);
                }
                continue;
            }
            try {
                BigDecimal number = ArgentineNumbers.parse(value);
                if (number.signum() < 0) {
                    errors.add(new RowError(line, "'" + column.header() + "' must be zero or greater"));
                } else if (PRICES.contains(column) && (number.scale() > MAX_PRICE_DECIMALS
                        || number.precision() - number.scale() > MAX_PRICE_INTEGER_DIGITS)) {
                    errors.add(new RowError(line, "'" + column.header() + "' must have up to "
                            + MAX_PRICE_INTEGER_DIGITS + " digits and " + MAX_PRICE_DECIMALS + " decimals"));
                } else {
                    numbers.put(column, number);
                }
            } catch (NumberFormatException e) {
                errors.add(new RowError(line, "'" + column.header() + "' is not a valid number: '" + value
                        + "' (use 15000,50 or 15.000,50)"));
            }
        }

        String name = texts.get(Column.NOMBRE);
        if (name == null) {
            if (errors.size() == errorsBefore) errors.add(new RowError(line, "The name is required"));
            return null;
        }
        if (!seen.add(name)) {
            errors.add(new RowError(line, "The product '" + name + "' is repeated in the file"));
            return null;
        }
        Product product = existing.get(name);
        if (product == null && productRepository.existsByNameIncludingDeleted(name)) {
            errors.add(new RowError(line, "The name '" + name + "' belongs to a deleted product"));
            return null;
        }
        return errors.size() == errorsBefore ? new ParsedRow(name, texts, numbers, product) : null;
    }

    private void create(ParsedRow row) {
        Product product = new Product();
        product.setName(row.name());
        product.setCategory(row.texts().get(Column.CATEGORIA));
        product.setDescription(row.texts().get(Column.DESCRIPCION));
        product.setPrice(row.numbers().get(Column.PRECIO));
        product.setCost(row.numbers().get(Column.COSTO));
        product.setStock(0);
        product.setSlug(Slugs.unique(row.name(), productRepository::existsBySlugIncludingDeleted));
        product.setMinStock(0);
        product = productRepository.save(product);

        if (row.hasMetadata()) {
            ProductMetaData metaData = new ProductMetaData();
            metaData.setProduct(product);
            applyMetadata(metaData, row);
            productMetaDataRepository.save(metaData);
        }
    }

    // Solo cambia lo que viene con valor: una celda vacia deja el dato como estaba
    private void update(ParsedRow row) {
        Product product = row.existing();
        setIfPresent(row.texts().get(Column.CATEGORIA), product::setCategory);
        setIfPresent(row.texts().get(Column.DESCRIPCION), product::setDescription);
        setIfPresent(row.numbers().get(Column.PRECIO), product::setPrice);
        setIfPresent(row.numbers().get(Column.COSTO), product::setCost);
        product = productRepository.save(product);

        if (row.hasMetadata()) {
            Product owner = product;
            ProductMetaData metaData = productMetaDataRepository.findByProduct_Id(product.getId())
                    .orElseGet(() -> {
                        ProductMetaData created = new ProductMetaData();
                        created.setProduct(owner);
                        return created;
                    });
            applyMetadata(metaData, row);
            productMetaDataRepository.save(metaData);
        }
    }

    private static void applyMetadata(ProductMetaData metaData, ParsedRow row) {
        setIfPresent(row.texts().get(Column.MATERIAL), metaData::setMaterial);
        setIfPresent(row.texts().get(Column.COLOR), metaData::setColor);
        setIfPresent(row.texts().get(Column.ESTILO), metaData::setStyle);
        setIfPresent(row.numbers().get(Column.ALTO), v -> metaData.setHeight(v.doubleValue()));
        setIfPresent(row.numbers().get(Column.ANCHO), v -> metaData.setWidth(v.doubleValue()));
        setIfPresent(row.numbers().get(Column.LARGO), v -> metaData.setLength(v.doubleValue()));
        setIfPresent(row.numbers().get(Column.PESO), v -> metaData.setWeight(v.doubleValue()));
    }

    private static <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) setter.accept(value);
    }

    private static List<Object> toRow(Product p, ProductMetaData md) {
        List<Object> row = new ArrayList<>();
        row.add(p.getName());
        row.add(nullToEmpty(p.getCategory()));
        row.add(nullToEmpty(p.getDescription()));
        row.add(ArgentineNumbers.format(p.getPrice()));
        row.add(ArgentineNumbers.format(p.getCost()));
        if (md == null) {
            for (int i = 0; i < METADATA.size(); i++) row.add("");
        } else {
            row.add(nullToEmpty(md.getMaterial()));
            row.add(nullToEmpty(md.getColor()));
            row.add(nullToEmpty(md.getStyle()));
            row.add(ArgentineNumbers.format(md.getHeight()));
            row.add(ArgentineNumbers.format(md.getWidth()));
            row.add(ArgentineNumbers.format(md.getLength()));
            row.add(ArgentineNumbers.format(md.getWeight()));
        }
        return row;
    }

    private static List<String> headers() {
        return Arrays.stream(Column.values()).map(Column::header).toList();
    }

    private static CSVFormat format() {
        return CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setRecordSeparator("\r\n")
                .setIgnoreEmptyLines(true)
                .build();
    }

    // 'Categoría', ' CATEGORIA ' y 'categoria' son la misma columna
    private static String normalize(String header) {
        return Normalizer.normalize(header.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ImportResultDto failed(int row, String message) {
        return new ImportResultDto(0, 0, List.of(new RowError(row, message)));
    }

    private record ParsedRow(String name, Map<Column, String> texts, Map<Column, BigDecimal> numbers, Product existing) {
        boolean hasMetadata() {
            return METADATA.stream().anyMatch(c -> texts.containsKey(c) || numbers.containsKey(c));
        }
    }
}
