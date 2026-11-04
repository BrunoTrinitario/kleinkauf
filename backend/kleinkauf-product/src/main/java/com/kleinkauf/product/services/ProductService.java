package com.kleinkauf.product.services;

import java.io.IOException;
import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.security.PermissionArea;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.*;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductImage;
import com.kleinkauf.product.entities.ProductMetaData;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.factories.ProductFactory;
import com.kleinkauf.product.dto.catalog.CatalogSort;
import com.kleinkauf.product.repositories.CatalogRepository;
import com.kleinkauf.product.repositories.ProductImageRepository;
import com.kleinkauf.product.repositories.ProductMetaDataRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.util.FileManager;
import com.kleinkauf.product.util.SecurityUtil;
import com.kleinkauf.product.util.Slugs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    public static final long MAX_IMAGE_BYTES = 1024 * 1024; // 1 MB

    // Manual recording of what is not a change to an @Audited entity (docs/auditoria.md)
    @Autowired
    private AuditLog auditLog;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private ProductMetaDataRepository productMetaDataRepository;

    @Autowired
    private ProductFactory  productFactory;

    @Autowired
    private FileManager fileManager;

    @Autowired
    private StockService stockService;

    @Autowired
    private CatalogCacheService catalogCache;

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /*
    Paginated dashboard listing (with exact stock), ordered by name. Uses the same search as the
    catalog (name, description and category) and fetches the images of the whole page in a single query.
     */
    public ProductPageDto searchProducts(String query, String category, Integer page, Integer size) throws ApiException {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorMessages.INVALID_CATALOG_PAGE, HttpStatus.BAD_REQUEST);
        }
        CatalogRepository.Filter filter = new CatalogRepository.Filter(blankToNull(query), blankToNull(category));
        long total = catalogRepository.count(filter);
        List<Product> products = catalogRepository.search(filter, CatalogSort.NAME, pageNumber, pageSize);

        Map<Long, List<String>> imagesByProduct = new HashMap<>();
        if (!products.isEmpty()) {
            List<Long> ids = products.stream().map(Product::getId).toList();
            for (Object[] row : productImageRepository.findPathsByProductIds(ids)) {
                imagesByProduct.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
            }
        }

        List<LightProductDto> items = products.stream()
                .map(p -> productFactory.getLightProductInfo(p, imagesByProduct.getOrDefault(p.getId(), List.of())))
                .toList();
        int totalPages = (int) ((total + pageSize - 1) / pageSize);
        return new ProductPageDto(items, pageNumber, pageSize, total, totalPages);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Featured products ordered by featuredOrder (unordered last) and then by name
    public ArrayList<LightProductDto> getFeaturedProducts() throws ApiException {
        return toLightProducts(productRepository.findFeatured());
    }

    public FullProductDto setFeatured(Long id, FeaturedDto dto) throws ApiException {
        if (dto.featured() && dto.order() != null && dto.order() < 0) {
            throw new ApiException(ErrorMessages.INVALID_FEATURED_ORDER, HttpStatus.BAD_REQUEST);
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
        product.setFeatured(dto.featured());
        product.setFeaturedOrder(dto.featured() ? dto.order() : null);
        productRepository.save(product);
        catalogCache.invalidate();
        return productFactory.getFullProductInfo(product);
    }

    private ArrayList<LightProductDto> toLightProducts(Collection<Product> productCollection) throws ApiException {
        ArrayList<LightProductDto> products = new ArrayList<>();
        for  (Product product : productCollection) {
            ArrayList<ImageDto> images = this.getProductImages(product.getId());
            List<String> imagesPath = images.stream().map(i->{
                 return i.path();
            }).toList();
            products.add(productFactory.getLightProductInfo(product,imagesPath));
        }
        return products;
    }

    public FullProductDto getProductById(Long id) throws ApiException {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        return productFactory.getFullProductInfo(product);
    }

    public boolean deleteProductById(Long id) throws ApiException {
        FullProductDto product = this.getProductById(id);
        if (product==null) throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        product.getImages().stream().forEach(image -> {
            this.deleteImagePhyscally(image.path());
        });
        productRepository.deleteById(id);
        catalogCache.invalidate();
        return true;
    }

    public ArrayList<ImageDto> getProductImages(Long id) throws ApiException {
        ArrayList<ImageDto> images = new ArrayList<>();
        Collection<ProductImage> im = this.productImageRepository.findByProduct_Id(id);
        for  (ProductImage product : im) {
            images.add(new ImageDto(
                    product.getId(),
                    product.getPath(),
                    product.getImageOrder()
            ));
        }
        return images;
    }

    public MetaDataDto getProductMetaData(Long id) throws ApiException {
        ProductMetaData productMetaData = this.productMetaDataRepository.findByProduct_Id(id).orElse(null);
        if (productMetaData == null) return null;
        return new MetaDataDto(
                productMetaData.getMaterial(),
                productMetaData.getColor(),
                productMetaData.getStyle(),
                productMetaData.getHeight(),
                productMetaData.getWidth(),
                productMetaData.getLength(),
                productMetaData.getWeight()
        );
    }

    public boolean deleteImageById(Long id) throws ApiException {
        ProductImage image = productImageRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.IMAGE_NOT_FOUND, HttpStatus.NOT_FOUND));
        productImageRepository.delete(image);
        deleteImagePhyscally(image.getPath());
        List<ProductImage> images = productImageRepository.findByProduct_IdOrderByImageOrderAsc(image.getProduct().getId());
        for (int i = 0; i < images.size(); i++)
            images.get(i).setImageOrder(i);
        productImageRepository.saveAll(images);
        catalogCache.invalidate();
        return true;
    }

    public void deleteImagePhyscally(String path){
        fileManager.deleteImage(path);
    }

    // Null fields do not change (BasicUpdateDto)
    public FullProductDto updateProduct(Long id, BasicUpdateDto updatedData) throws ApiException{
        if (updatedData.minStock() != null) validateMinStock(updatedData.minStock());
        Product product = productRepository.findById(id).orElse(null);
        if (product == null){
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (updatedData.name() != null) {
            String name = validateName(updatedData.name());
            if (!name.equals(product.getName())) {
                validateNameAvailable(name);
                product.setName(name);
            }
        }
        if (updatedData.category() != null) product.setCategory(updatedData.category());
        if (updatedData.description() != null) product.setDescription(updatedData.description());
        if (updatedData.minStock() != null) product.setMinStock(updatedData.minStock());
        productRepository.save(product);
        catalogCache.invalidate();
        return productFactory.getFullProductInfo(product);
    }

    public FullProductDto createProduct(NewProductDto updatedData) throws ApiException{
        String name = validateName(updatedData.name());
        validateNameAvailable(name);
        // Without the PRECIOS permission price and cost are ignored (the product is created without them)
        boolean canSetPrices = SecurityUtil.hasPermission(PermissionArea.PRECIOS);
        BigDecimal price = canSetPrices ? updatedData.price() : null;
        BigDecimal cost = canSetPrices ? updatedData.cost() : null;
        validatePrices(price, cost);
        if (updatedData.stock() < 0) {
            throw new ApiException(ErrorMessages.INVALID_STOCK_QUANTITY, HttpStatus.BAD_REQUEST);
        }
        validateMinStock(updatedData.minStock());
        Product product = new Product();
        product.setName(name);
        product.setCategory(updatedData.category());
        product.setDescription(updatedData.description());
        product.setStock(updatedData.stock());
        product.setMinStock(updatedData.minStock());
        product.setPrice(price);
        product.setCost(cost);
        // Fixed slug for the catalog link (does not change if the name changes later)
        product.setSlug(Slugs.unique(product.getName(), productRepository::existsBySlugIncludingDeleted));
        product = productRepository.save(product);
        stockService.registerInitialStock(product);
        catalogCache.invalidate();
        return this.getProductById(product.getId());
    }

    public PricingDto getPricing(Long id) throws ApiException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
        // Sensitive read: the cost and the margin
        auditLog.read("ProductPricing", id);
        return toPricing(product);
    }

    public PricingDto updatePricing(Long id, UpdatePricingDto dto) throws ApiException {
        validatePrices(dto.price(), dto.cost());
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
        product.setPrice(dto.price());
        product.setCost(dto.cost());
        productRepository.save(product);
        catalogCache.invalidate();
        return toPricing(product);
    }

    static PricingDto toPricing(Product product) {
        BigDecimal price = product.getPrice();
        BigDecimal cost = product.getCost();
        BigDecimal margin = null;
        BigDecimal marginPercent = null;
        if (price != null && cost != null) {
            margin = price.subtract(cost);
            if (price.signum() > 0) {
                marginPercent = margin.multiply(BigDecimal.valueOf(100))
                        .divide(price, 2, RoundingMode.HALF_UP);
            }
        }
        return new PricingDto(product.getId(), price, cost, margin, marginPercent, product.getSoldCount());
    }

    private void validatePrices(BigDecimal price, BigDecimal cost) throws ApiException {
        if ((price != null && price.signum() < 0) || (cost != null && cost.signum() < 0)) {
            throw new ApiException(ErrorMessages.INVALID_PRICE, HttpStatus.BAD_REQUEST);
        }
    }

    private static String validateName(String name) throws ApiException {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new ApiException(ErrorMessages.PRODUCT_NAME_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        return name.trim();
    }

    // The name is unique in the table, also against deleted products (soft delete): without this, the insert
    // or the update hit the constraint and responded 500
    private void validateNameAvailable(String name) throws ApiException {
        if (productRepository.existsByName(name)) {
            throw new ApiException(ErrorMessages.PRODUCT_NAME_IN_USE, HttpStatus.CONFLICT);
        }
        if (productRepository.existsByNameIncludingDeleted(name)) {
            throw new ApiException(ErrorMessages.PRODUCT_NAME_OF_DELETED, HttpStatus.CONFLICT);
        }
    }

    private void validateMinStock(int minStock) throws ApiException {
        if (minStock < 0) {
            throw new ApiException(ErrorMessages.INVALID_MIN_STOCK, HttpStatus.BAD_REQUEST);
        }
    }

    public FullProductDto updateMetadata(Long productId, MetaDataDto updatedData) throws ApiException{
        ProductMetaData productMetaData = this.productMetaDataRepository.findByProduct_Id(productId).orElse(null);
        if (productMetaData == null){
            Product product = productRepository.findById(productId).orElse(null);
            if (product != null){
                productMetaData = new ProductMetaData();
                productMetaData.setProduct(product);
            }else
                throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        productMetaData.setMaterial(updatedData.material());
        productMetaData.setColor(updatedData.color());
        productMetaData.setStyle(updatedData.style());
        productMetaData.setHeight(updatedData.height());
        productMetaData.setWidth(updatedData.width());
        productMetaData.setLength(updatedData.length());
        productMetaData.setWeight(updatedData.weight());
        productMetaDataRepository.save(productMetaData);
        catalogCache.invalidate();
        return this.getProductById(productId);
    }

    public ArrayList<ImageDto> addProductImages(Long productId, List<String> imagePaths) throws ApiException {
        ArrayList<ImageDto> currentImages = this.getProductImages(productId);
        Product product = productRepository.findById(productId).orElse(null);
        int cont = currentImages.size();
        for(String path : imagePaths) {
            ProductImage realImage = new ProductImage();
            realImage.setProduct(product);
            String fileName = Paths.get(path).getFileName().toString();
            realImage.setPath(fileName);
            realImage.setImageOrder(cont);
            cont++;
            productImageRepository.save(realImage);
        }
        catalogCache.invalidate();
        return this.getProductImages(productId);
    }

    public boolean saveImages(Long productId, List<MultipartFile> files) throws ApiException {
        if (!productRepository.existsById(productId)) {
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        // Everything is validated before saving: if one fails none is left half done. First the size of all of them
        // (the general upload limit is 5 MB, because of the CSV; images stay at 1 MB) and then the type
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new ApiException(ErrorMessages.IMAGE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
            }
        }
        for (MultipartFile file : files) {
            try {
                if (FileManager.imageExtension(file.getBytes()) == null) {
                    throw new ApiException(ErrorMessages.INVALID_IMAGE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                }
            } catch (IOException e) {
                throw new ApiException(ErrorMessages.INVALID_IMAGE, HttpStatus.BAD_REQUEST);
            }
        }
        List<String> imagesPaths = fileManager.SaveAllImages(files);
        this.addProductImages(productId, imagesPaths);
        return true;
    }

}
