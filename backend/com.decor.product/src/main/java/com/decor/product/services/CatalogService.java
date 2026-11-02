package com.decor.product.services;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.constants.ErrorMessages;
import com.decor.product.dto.MetaDataDto;
import com.decor.product.dto.catalog.Availability;
import com.decor.product.dto.catalog.CatalogBundleDto;
import com.decor.product.dto.catalog.CatalogBundleProductDto;
import com.decor.product.dto.catalog.CatalogPageDto;
import com.decor.product.dto.catalog.CatalogProductDetailDto;
import com.decor.product.dto.catalog.CatalogProductDto;
import com.decor.product.dto.catalog.CatalogSort;
import com.decor.product.dto.catalog.CategoryDto;
import com.decor.product.entities.Bundle;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.entities.ProductMetaData;
import com.decor.product.factories.BundleFactory;
import com.decor.product.repositories.BundleRepository;
import com.decor.product.repositories.CatalogRepository;
import com.decor.product.repositories.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/*
Catalogo publico (sin login): productos paginados con busqueda, categoria y orden, detalle por slug,
categorias y combos. No expone stock exacto ni costo. Las respuestas se cachean en Redis (CatalogCacheService).
 */
@Service
public class CatalogService {

    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private CatalogCacheService cache;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    public CatalogPageDto search(String query, String category, String sort, Integer page, Integer size) throws ApiException {
        CatalogSort catalogSort;
        try {
            catalogSort = CatalogSort.parse(sort);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorMessages.INVALID_CATALOG_SORT, HttpStatus.BAD_REQUEST);
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorMessages.INVALID_CATALOG_PAGE, HttpStatus.BAD_REQUEST);
        }
        CatalogRepository.Filter filter = new CatalogRepository.Filter(normalize(query), normalize(category));

        String key = "products:" + catalogSort + ":" + pageNumber + ":" + pageSize
                + ":" + filter.category() + ":" + filter.query();
        return cache.get(key, objectMapper.constructType(CatalogPageDto.class), () -> inTransaction(() -> {
            long total = catalogRepository.count(filter);
            List<CatalogProductDto> items = catalogRepository.search(filter, catalogSort, pageNumber, pageSize)
                    .stream()
                    .map(CatalogService::toDto)
                    .toList();
            int totalPages = (int) ((total + pageSize - 1) / pageSize);
            return new CatalogPageDto(items, pageNumber, pageSize, total, totalPages);
        }));
    }

    public CatalogProductDetailDto getBySlug(String slug) throws ApiException {
        CatalogProductDetailDto detail = cache.get("product:" + slug,
                objectMapper.constructType(CatalogProductDetailDto.class),
                () -> inTransaction(() -> productRepository.findBySlug(slug).map(CatalogService::toDetail).orElse(null)));
        if (detail == null) {
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return detail;
    }

    public List<CategoryDto> categories() {
        return cache.get("categories",
                objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryDto.class),
                () -> catalogRepository.categories());
    }

    // Combos habilitados con precio y con todos sus productos vigentes (los demas no se pueden comprar)
    public List<CatalogBundleDto> bundles() {
        return cache.get("bundles",
                objectMapper.getTypeFactory().constructCollectionType(List.class, CatalogBundleDto.class),
                () -> inTransaction(() -> bundleRepository.findByEnabledTrueOrderByNameAsc()
                        .stream()
                        .filter(CatalogService::isSellable)
                        .map(CatalogService::toDto)
                        .toList()));
    }

    static boolean isSellable(Bundle bundle) {
        return bundle.getPrice() != null
                && !bundle.getProducts().isEmpty()
                && bundle.getProducts().stream().allMatch(bp ->
                        bp.getProduct() != null && bp.getProduct().getDeletedAt() == null
                                && bp.getQuantity() != null && bp.getQuantity() > 0);
    }

    static CatalogBundleDto toDto(Bundle bundle) {
        List<CatalogBundleProductDto> products = bundle.getProducts().stream()
                .map(bp -> new CatalogBundleProductDto(bp.getProduct().getSlug(), bp.getProduct().getName(), bp.getQuantity()))
                .toList();
        String image = bundle.getProducts().stream()
                .flatMap(bp -> images(bp.getProduct()).stream())
                .findFirst()
                .orElse(null);
        Availability availability = BundleFactory.available(bundle.getProducts()) > 0
                ? Availability.DISPONIBLE
                : Availability.AGOTADO;
        return new CatalogBundleDto(bundle.getId(), bundle.getName(), bundle.getPrice(),
                BundleFactory.listPrice(bundle.getProducts()), availability, image, products);
    }

    // Las imagenes y los metadatos son lazy: se leen dentro de una transaccion
    private <T> T inTransaction(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template.execute(status -> work.get());
    }

    static CatalogProductDto toDto(Product p) {
        return new CatalogProductDto(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getCategory(),
                p.getDescription(),
                p.getPrice(),
                Availability.of(p.getStock(), p.getMinStock()),
                p.isFeatured(),
                images(p)
        );
    }

    static CatalogProductDetailDto toDetail(Product p) {
        ProductMetaData md = p.getMetaData();
        MetaDataDto metadata = md == null ? null : new MetaDataDto(
                md.getMaterial(), md.getColor(), md.getStyle(),
                md.getHeight(), md.getWidth(), md.getLength(), md.getWeight());
        return new CatalogProductDetailDto(
                p.getId(),
                p.getSlug(),
                p.getName(),
                p.getCategory(),
                p.getDescription(),
                p.getPrice(),
                Availability.of(p.getStock(), p.getMinStock()),
                p.isFeatured(),
                images(p),
                metadata
        );
    }

    private static List<String> images(Product p) {
        return p.getImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getImageOrder))
                .map(ProductImage::getPath)
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
