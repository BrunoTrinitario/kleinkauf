package com.decor.product.services;

import com.decor.product.constants.ErrorMessages;
import com.decor.product.dto.*;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.entities.ProductMetaData;
import com.decor.product.exceptions.ApiException;
import com.decor.product.factories.ProductFactory;
import com.decor.product.repositories.ProductImageRepository;
import com.decor.product.repositories.ProductMetaDataRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.util.FileManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductMetaDataRepository productMetaDataRepository;

    @Autowired
    private ProductFactory  productFactory;

    @Autowired
    private FileManager fileManager;

    public ArrayList<LightProductDto> getAllProducts() throws ApiException {
        ArrayList<LightProductDto> products = new ArrayList<>();
        Collection<Product> productCollection = productRepository.findAll();
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
        Product product = productRepository.findById(id).orElse(null);
        if (product == null){
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }else{
            product.setName(updatedData.name());
            product.setCategory(updatedData.category());
            product.setDescription(updatedData.description());
            product.setStock(updatedData.stock());
            productRepository.save(product);
        }
        return productFactory.getFullProductInfo(product);
    }

    public FullProductDto createProduct(NewProductDto updatedData) throws ApiException{
        if (this.productRepository.existsByName(updatedData.name())) {
            throw new ApiException(ErrorMessages.PRODUCT_NAME_IN_USE, HttpStatus.CONFLICT);
        }
        Product product = new Product();
        product.setName(updatedData.name());
        product.setCategory(updatedData.category());
        product.setDescription(updatedData.description());
        product.setStock(updatedData.stock());
        product = productRepository.save(product);
        return this.getProductById(product.getId());
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
        return this.getProductImages(productId);
    }

    public boolean saveImages(Long productId, List<MultipartFile> files) throws ApiException {
        List<String> imagesPaths = fileManager.SaveAllImages(files);
        this.addProductImages(productId, imagesPaths);
        return true;
    }

}
