package com.kleinkauf.product.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.MetaDataDto;
import com.kleinkauf.product.dto.catalog.Availability;
import com.kleinkauf.product.dto.catalog.CatalogBundleDto;
import com.kleinkauf.product.dto.catalog.CatalogBundleProductDto;
import com.kleinkauf.product.dto.catalog.CatalogPageDto;
import com.kleinkauf.product.dto.catalog.CatalogProductDetailDto;
import com.kleinkauf.product.dto.catalog.CatalogProductDto;
import com.kleinkauf.product.dto.catalog.CatalogSort;
import com.kleinkauf.product.dto.catalog.CategoryDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductImage;
import com.kleinkauf.product.entities.ProductMetaData;
import com.kleinkauf.product.factories.BundleFactory;
import com.kleinkauf.product.repositories.BundleRepository;
import com.kleinkauf.product.repositories.CatalogRepository;
import com.kleinkauf.product.repositories.ProductRepository;
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
Public catalog (no login): paginated products with search, category and sort, featured products, detail by
slug, categories and bundles. Does not expose exact stock or cost. Responses are cached in Redis (CatalogCacheService).
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

    // Slugs of every product in the catalog (not deleted), for the sitemap
    public List<String> productSlugs() {
        return inTransaction(() -> productRepository.findAllByOrderByNameAsc().stream()
                .map(product -> product.getSlug())
                .filter(slug -> slug != null && !slug.isBlank())
                .toList());
    }

    // Featured products for the home page, in the order the administrator chose (unordered ones last, by name)
    public List<CatalogProductDto> featured() {
        return cache.get("featured",
                objectMapper.getTypeFactory().constructCollectionType(List.class, CatalogProductDto.class),
                () -> inTransaction(() -> productRepository.findFeatured()
                        .stream()
                        .map(CatalogService::toDto)
                        .toList()));
    }

    public List<CategoryDto> categories() {
        return cache.get("categories",
                objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryDto.class),
                () -> catalogRepository.categories());
    }

    // Enabled bundles with a price and all their products still available (the others cannot be bought)
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
        return BundleFactory.price(bundle) != null
                && !bundle.getProducts().isEmpty()
                && bundle.getProducts().stream().allMatch(bp ->
                        bp.getProduct() != null && bp.getProduct().getDeletedAt() == null
                                && bp.getQuantity() != null && bp.getQuantity() > 0);
    }

    static CatalogBundleDto toDto(Bundle bundle) {
        List<CatalogBundleProductDto> products = bundle.getProducts().stream()
                .map(bp -> {
                    Product p = bp.getProduct();
                    List<String> images = images(p);
                    return new CatalogBundleProductDto(p.getSlug(), p.getName(), bp.getQuantity(), p.getCategory(),
                            p.getDescription(), p.getPrice(), images.isEmpty() ? null : images.get(0));
                })
                .toList();
        String image = bundle.getProducts().stream()
                .flatMap(bp -> images(bp.getProduct()).stream())
                .findFirst()
                .orElse(null);
        Availability availability = BundleFactory.available(bundle.getProducts()) > 0
                ? Availability.DISPONIBLE
                : Availability.AGOTADO;
        return new CatalogBundleDto(bundle.getId(), bundle.getName(), BundleFactory.price(bundle),
                BundleFactory.listPrice(bundle.getProducts()), availability, image, products);
    }

    // Images and metadata are lazy: they are read inside a transaction
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
