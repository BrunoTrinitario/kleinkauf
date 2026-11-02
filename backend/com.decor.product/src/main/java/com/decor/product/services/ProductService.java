package com.decor.product.services;

import com.decor.common.security.PermissionArea;
import com.decor.product.constants.ErrorMessages;
import com.decor.product.dto.*;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.entities.ProductMetaData;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.factories.ProductFactory;
import com.decor.product.dto.catalog.CatalogSort;
import com.decor.product.repositories.CatalogRepository;
import com.decor.product.repositories.ProductImageRepository;
import com.decor.product.repositories.ProductMetaDataRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.util.FileManager;
import com.decor.product.util.SecurityUtil;
import com.decor.product.util.Slugs;
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

    public ArrayList<LightProductDto> getAllProducts() throws ApiException {
        return toLightProducts(productRepository.findAll());
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /*
    Listado paginado del panel (con stock exacto), ordenado por nombre. Usa la misma busqueda que el
    catalogo (nombre, descripcion y categoria) y trae las imagenes de toda la pagina en una sola consulta.
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

    // Destacados ordenados por featuredOrder (sin orden al final) y despues por nombre
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

    public FullProductDto getProductByName(String name) throws ApiException {
        Product product = productRepository.findByName(name).orElse(null);
        if (product == null) throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        return productFactory.getFullProductInfo(product);
    }

    public FullProductDto updateProduct(Long id, BasicUpdateDto updatedData) throws ApiException{
        validateMinStock(updatedData.minStock());
        Product product = productRepository.findById(id).orElse(null);
        if (product == null){
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }else{
            product.setName(updatedData.name());
            product.setCategory(updatedData.category());
            product.setDescription(updatedData.description());
            product.setMinStock(updatedData.minStock());
            productRepository.save(product);
            catalogCache.invalidate();
        }
        return productFactory.getFullProductInfo(product);
    }

    public FullProductDto createProduct(NewProductDto updatedData) throws ApiException{
        if (this.productRepository.existsByName(updatedData.name())) {
            throw new ApiException(ErrorMessages.PRODUCT_NAME_IN_USE, HttpStatus.CONFLICT);
        }
        // Sin el permiso PRECIOS el precio y el costo se ignoran (el producto se crea sin ellos)
        boolean canSetPrices = SecurityUtil.hasPermission(PermissionArea.PRECIOS);
        BigDecimal price = canSetPrices ? updatedData.price() : null;
        BigDecimal cost = canSetPrices ? updatedData.cost() : null;
        validatePrices(price, cost);
        if (updatedData.stock() < 0) {
            throw new ApiException(ErrorMessages.INVALID_STOCK_QUANTITY, HttpStatus.BAD_REQUEST);
        }
        validateMinStock(updatedData.minStock());
        Product product = new Product();
        product.setName(updatedData.name());
        product.setCategory(updatedData.category());
        product.setDescription(updatedData.description());
        product.setStock(updatedData.stock());
        product.setMinStock(updatedData.minStock());
        product.setPrice(price);
        product.setCost(cost);
        // Slug fijo para el link del catalogo (no cambia si despues cambia el nombre)
        product.setSlug(Slugs.unique(product.getName(), productRepository::existsBySlugIncludingDeleted));
        product = productRepository.save(product);
        stockService.registerInitialStock(product);
        catalogCache.invalidate();
        return this.getProductById(product.getId());
    }

    public PricingDto getPricing(Long id) throws ApiException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
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
        return new PricingDto(product.getId(), price, cost, margin, marginPercent);
    }

    private void validatePrices(BigDecimal price, BigDecimal cost) throws ApiException {
        if ((price != null && price.signum() < 0) || (cost != null && cost.signum() < 0)) {
            throw new ApiException(ErrorMessages.INVALID_PRICE, HttpStatus.BAD_REQUEST);
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
        ArrayList<ImageDto> actualImages = this.getProductImages(productId);
        Product product = productRepository.findById(productId).orElse(null);
        int cont = actualImages.size();
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
        // El limite general de subida es 5 MB (por el CSV); las imagenes siguen en 1 MB
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new ApiException(ErrorMessages.IMAGE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
            }
        }
        List<String> imagesPaths = fileManager.SaveAllImages(files);
        this.addProductImages(productId, imagesPaths);
        return true;
    }

}
