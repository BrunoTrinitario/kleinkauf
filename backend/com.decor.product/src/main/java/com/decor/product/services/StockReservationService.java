package com.decor.product.services;

import com.decor.common.dto.stock.ReservationDto;
import com.decor.common.dto.stock.ReservationItemDto;
import com.decor.common.dto.stock.ReservationItemType;
import com.decor.common.dto.stock.ReservationRequestDto;
import com.decor.common.dto.stock.ReservedItemDto;
import com.decor.common.dto.stock.SoldItemsDto;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.constants.ErrorMessages;
import com.decor.product.entities.Bundle;
import com.decor.product.entities.Product;
import com.decor.product.entities.StockMovement;
import com.decor.product.entities.StockMovementType;
import com.decor.product.entities.StockReservation;
import com.decor.product.repositories.BundleProductRepository;
import com.decor.product.repositories.BundleRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.repositories.StockMovementRepository;
import com.decor.product.repositories.StockReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
Reservas de stock de los pedidos de sales (endpoints internos).
- Reservar: valida todos los items (existen, tienen precio y alcanza el stock) y registra una SALIDA
  por producto. Todo o nada. Un combo descuenta cada producto que lo compone.
  Venta del local: acepta items sin precio y, si falta stock, registra un AJUSTE por lo que falta
  (el stock queda en 0) e informa los productos en shortages.
- Devolver: registra una ENTRADA por cada SALIDA de la reserva (todo lo vendido, aunque haya habido AJUSTE).
- Vendidos: suma o resta el contador de cada producto y combo del pedido.
Todas son idempotentes por reference (numero de pedido), ver StockReservation.
Las transacciones vuelven atras tambien con ApiException (checked).
 */
@Service
public class StockReservationService {

    static final int MAX_REFERENCE_LENGTH = 40;
    private static final String SHORTAGE_SEPARATOR = ", ";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private BundleProductRepository bundleProductRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockReservationRepository reservationRepository;

    @Autowired
    private CatalogCacheService catalogCache;

    // Lo que hay que descontar de un item: productId -> unidades
    private record Line(ReservationItemDto item, Bundle bundle, Map<Long, Integer> components) {
    }

    @Transactional(rollbackFor = Exception.class)
    public ReservationDto reserve(ReservationRequestDto request) throws ApiException {
        validate(request);
        String reference = request.reference().trim();

        StockReservation existing = reservationRepository.findByReference(reference).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == StockReservation.Status.RELEASED) {
                throw new ApiException(ErrorMessages.RESERVATION_RELEASED, HttpStatus.CONFLICT);
            }
            // Reintento de una reserva ya hecha: devuelve los datos sin volver a descontar
            List<Line> lines = resolve(request.items());
            Map<Long, Product> products = byId(productRepository.findAllById(productIds(lines)));
            return new ReservationDto(reference, describe(lines, products, request.local()), shortages(existing));
        }

        StockReservation reservation = new StockReservation();
        reservation.setReference(reference);
        reservation.setStatus(StockReservation.Status.RESERVED);
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // Otro pedido con la misma reference se esta procesando al mismo tiempo
            throw new ApiException(ErrorMessages.RESERVATION_IN_PROGRESS, HttpStatus.CONFLICT);
        }

        List<Line> lines = resolve(request.items());
        Map<Long, Product> products = byId(productRepository.findAllByIdForUpdate(productIds(lines)));
        List<ReservedItemDto> items = describe(lines, products, request.local());

        Map<Long, Integer> needed = new TreeMap<>();
        for (Line line : lines) {
            line.components().forEach((id, quantity) -> needed.merge(id, quantity, Integer::sum));
        }
        List<String> missing = needed.entrySet().stream()
                .filter(e -> products.get(e.getKey()).getStock() < e.getValue())
                .map(e -> products.get(e.getKey()).getName())
                .toList();
        if (!missing.isEmpty() && !request.local()) {
            throw new ApiException(ErrorMessages.INSUFFICIENT_STOCK + ": " + String.join(", ", missing), HttpStatus.CONFLICT);
        }

        String reason = "Pedido " + reference;
        for (Map.Entry<Long, Integer> e : needed.entrySet()) {
            Product product = products.get(e.getKey());
            int stock = Math.max(product.getStock(), 0);
            if (stock < e.getValue()) {
                // Venta del local sin stock suficiente: se ajusta a lo vendido y despues sale todo
                move(product, StockMovementType.AJUSTE, e.getValue() - product.getStock(),
                        "Ajuste automatico pedido " + reference + ": faltaba stock", reference);
            }
            move(product, StockMovementType.SALIDA, -e.getValue(), reason, reference);
        }

        if (!missing.isEmpty()) {
            reservation.setShortages(truncate(String.join(SHORTAGE_SEPARATOR, missing)));
            reservationRepository.save(reservation);
        }
        catalogCache.invalidate();
        return new ReservationDto(reference, items, missing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(String reference) throws ApiException {
        String ref = validReference(reference);

        StockReservation reservation = reservationRepository.findByReferenceForUpdate(ref).orElse(null);
        if (reservation == null) {
            // La reserva nunca llego (o todavia no): queda marcada para que no se aplique despues
            reservation = new StockReservation();
            reservation.setReference(ref);
            reservation.setStatus(StockReservation.Status.RELEASED);
            reservation.setReleasedAt(LocalDateTime.now());
            try {
                reservationRepository.saveAndFlush(reservation);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(ErrorMessages.RESERVATION_IN_PROGRESS, HttpStatus.CONFLICT);
            }
            return;
        }
        if (reservation.getStatus() == StockReservation.Status.RELEASED) {
            return;
        }

        Map<Long, Integer> reserved = new TreeMap<>();
        for (Object[] row : stockMovementRepository.findQuantitiesByReference(ref, StockMovementType.SALIDA)) {
            reserved.merge((Long) row[0], -((Integer) row[1]), Integer::sum);
        }
        // Los productos borrados no vuelven al stock: ya no se venden
        Map<Long, Product> products = reserved.isEmpty()
                ? Map.of()
                : byId(productRepository.findAllByIdForUpdate(reserved.keySet()));
        String reason = "Devolucion pedido " + ref;
        reserved.forEach((id, quantity) -> {
            Product product = products.get(id);
            if (product != null) move(product, StockMovementType.ENTRADA, quantity, reason, ref);
        });

        reservation.setStatus(StockReservation.Status.RELEASED);
        reservation.setReleasedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        catalogCache.invalidate();
    }

    // Suma el pedido al contador de vendidos (al validar el pago o al cargar una venta del local)
    @Transactional(rollbackFor = Exception.class)
    public void markSold(String reference, SoldItemsDto dto) throws ApiException {
        StockReservation reservation = soldReservation(reference, dto);
        if (reservation.isSold()) return;
        if (reservation.getStatus() == StockReservation.Status.RELEASED) {
            throw new ApiException(ErrorMessages.RESERVATION_RELEASED, HttpStatus.CONFLICT);
        }
        addSold(dto, 1);
        reservation.setSold(true);
        reservationRepository.save(reservation);
    }

    // Resta el pedido del contador (al cancelar un pedido que ya estaba pago)
    @Transactional(rollbackFor = Exception.class)
    public void unmarkSold(String reference, SoldItemsDto dto) throws ApiException {
        StockReservation reservation = soldReservation(reference, dto);
        if (!reservation.isSold()) return;
        addSold(dto, -1);
        reservation.setSold(false);
        reservationRepository.save(reservation);
    }

    private StockReservation soldReservation(String reference, SoldItemsDto dto) throws ApiException {
        String ref = validReference(reference);
        if (dto == null || dto.items() == null) {
            throw new ApiException(ErrorMessages.INVALID_RESERVATION, HttpStatus.BAD_REQUEST);
        }
        validateItems(dto.items());
        return reservationRepository.findByReferenceForUpdate(ref)
                .orElseThrow(() -> new ApiException(ErrorMessages.RESERVATION_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    // Los productos y combos borrados se saltean: el pedido conserva sus datos
    private void addSold(SoldItemsDto dto, int sign) {
        for (ReservationItemDto item : dto.items()) {
            int delta = sign * item.quantity();
            if (item.type() == ReservationItemType.PRODUCT) {
                productRepository.findById(item.id()).ifPresent(p -> {
                    p.setSoldCount(Math.max(p.getSoldCount() + delta, 0));
                    productRepository.save(p);
                });
            } else {
                bundleRepository.findById(item.id()).ifPresent(b -> {
                    b.setSoldCount(Math.max(b.getSoldCount() + delta, 0));
                    bundleRepository.save(b);
                });
            }
        }
    }

    private void validate(ReservationRequestDto request) throws ApiException {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ApiException(ErrorMessages.INVALID_RESERVATION, HttpStatus.BAD_REQUEST);
        }
        validReference(request.reference());
        validateItems(request.items());
    }

    private static void validateItems(List<ReservationItemDto> items) throws ApiException {
        for (ReservationItemDto item : items) {
            if (item == null || item.type() == null || item.id() == null
                    || item.quantity() == null || item.quantity() <= 0) {
                throw new ApiException(ErrorMessages.INVALID_RESERVATION, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static String validReference(String reference) throws ApiException {
        if (reference == null || reference.isBlank() || reference.trim().length() > MAX_REFERENCE_LENGTH) {
            throw new ApiException(ErrorMessages.INVALID_RESERVATION, HttpStatus.BAD_REQUEST);
        }
        return reference.trim();
    }

    // Arma cada linea con los productos que descuenta. Los combos tienen que estar habilitados y con productos
    private List<Line> resolve(List<ReservationItemDto> items) throws ApiException {
        List<Line> lines = new ArrayList<>();
        for (ReservationItemDto item : items) {
            if (item.type() == ReservationItemType.PRODUCT) {
                lines.add(new Line(item, null, Map.of(item.id(), item.quantity())));
                continue;
            }
            Bundle bundle = bundleRepository.findById(item.id())
                    .filter(Bundle::isEnabled)
                    .orElseThrow(() -> new ApiException(ErrorMessages.ITEM_NOT_AVAILABLE, HttpStatus.CONFLICT));
            Map<Long, Integer> components = new HashMap<>();
            for (Object[] row : bundleProductRepository.findComponents(bundle.getId())) {
                Integer quantity = (Integer) row[1];
                if (row[0] == null || quantity == null || quantity <= 0) continue;
                components.merge((Long) row[0], quantity * item.quantity(), Integer::sum);
            }
            if (components.isEmpty()) {
                throw new ApiException(ErrorMessages.ITEM_NOT_AVAILABLE + ": " + bundle.getName(), HttpStatus.CONFLICT);
            }
            lines.add(new Line(item, bundle, components));
        }
        return lines;
    }

    // Nombre, precio y costo de cada linea. Falla si falta algun producto (borrado) o el precio (salvo en el local)
    private List<ReservedItemDto> describe(List<Line> lines, Map<Long, Product> products, boolean local) throws ApiException {
        List<ReservedItemDto> result = new ArrayList<>();
        for (Line line : lines) {
            for (Long id : line.components().keySet()) {
                if (!products.containsKey(id)) {
                    String name = line.bundle() != null ? ": " + line.bundle().getName() : "";
                    throw new ApiException(ErrorMessages.ITEM_NOT_AVAILABLE + name, HttpStatus.CONFLICT);
                }
            }
            if (line.bundle() == null) {
                Product product = products.get(line.item().id());
                if (product.getPrice() == null && !local) {
                    throw new ApiException(ErrorMessages.ITEM_WITHOUT_PRICE + ": " + product.getName(), HttpStatus.CONFLICT);
                }
                result.add(new ReservedItemDto(ReservationItemType.PRODUCT, product.getId(), product.getName(),
                        product.getPrice(), product.getCost()));
            } else {
                Bundle bundle = line.bundle();
                if (bundle.getPrice() == null && !local) {
                    throw new ApiException(ErrorMessages.ITEM_WITHOUT_PRICE + ": " + bundle.getName(), HttpStatus.CONFLICT);
                }
                result.add(new ReservedItemDto(ReservationItemType.BUNDLE, bundle.getId(), bundle.getName(),
                        bundle.getPrice(), bundleCost(line, products)));
            }
        }
        return result;
    }

    // Costo de un combo: suma del costo de sus productos; null si alguno no tiene costo
    private static BigDecimal bundleCost(Line line, Map<Long, Product> products) {
        BigDecimal total = BigDecimal.ZERO;
        int bundles = line.item().quantity();
        for (Map.Entry<Long, Integer> component : line.components().entrySet()) {
            BigDecimal cost = products.get(component.getKey()).getCost();
            if (cost == null) return null;
            // components tiene las unidades de todos los combos de la linea: se divide por la cantidad
            total = total.add(cost.multiply(BigDecimal.valueOf(component.getValue() / bundles)));
        }
        return total;
    }

    private void move(Product product, StockMovementType type, int delta, String reason, String reference) {
        product.setStock(product.getStock() + delta);
        productRepository.save(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantity(delta);
        movement.setStockAfter(product.getStock());
        movement.setReason(reason);
        movement.setReference(reference);
        stockMovementRepository.save(movement);
    }

    private static List<String> shortages(StockReservation reservation) {
        if (reservation.getShortages() == null) return List.of();
        return Arrays.asList(reservation.getShortages().split(SHORTAGE_SEPARATOR));
    }

    private static String truncate(String value) {
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static Set<Long> productIds(List<Line> lines) {
        Set<Long> ids = new LinkedHashSet<>();
        lines.forEach(line -> ids.addAll(line.components().keySet()));
        return ids;
    }

    private static Map<Long, Product> byId(List<Product> products) {
        return products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
