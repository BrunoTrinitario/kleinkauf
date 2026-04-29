package com.decor.product.services;

import com.decor.product.constants.ErrorMessages;
import com.decor.product.dto.FullProductDto;
import com.decor.product.dto.ImageDto;
import com.decor.product.dto.LightProductDto;
import com.decor.product.dto.MetaDataDto;
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

    public ArrayList<LightProductDto> getAllProducts(){
        ArrayList<LightProductDto> products = new ArrayList<>();
        Collection<Product> productCollection = productRepository.findAll();
        for  (Product product : productCollection) {
            products.add(productFactory.getLightProductInfo(product));
        }
        return products;
    }

    public FullProductDto getProductById(Long id) throws ApiException {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null) throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        return productFactory.getFullProductInfo(product);
    }

    public boolean deleteProductById(Long id) throws ApiException {
        boolean exists = productRepository.existsById(id);
        if (!exists) throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        productRepository.deleteById(id);
        return true;
    }

    public ArrayList<ImageDto> getProductImages(Long id) throws ApiException {
        ArrayList<ImageDto> images = new ArrayList<>();
        Collection<ProductImage> im = this.productImageRepository.findByProduct_Id(id);
        for  (ProductImage product : im) {
            images.add(new ImageDto(
                    product.getPath(),
                    product.getOrder()
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
        Long productId = image.getProduct().getId();
        FileManager.deleteImage(image.getPath());
        productImageRepository.delete(image);
        List<ProductImage> images = productImageRepository.findByProduct_IdOrderByPositionAsc(productId);
        for (int i = 0; i < images.size(); i++)
            images.get(i).setOrder(i);
        productImageRepository.saveAll(images);
        return true;
    }

    public FullProductDto getProductByName(String name) throws ApiException {
        Product product = productRepository.findByName(name).orElse(null);
        if (product == null) throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        return productFactory.getFullProductInfo(product);
    }

    public FullProductDto updateProduct(LightProductDto updatedData) throws ApiException{
        Product product = productRepository.findById(updatedData.id()).orElse(null);
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

    public FullProductDto createProduct(LightProductDto updatedData) throws ApiException{
        Product product = new Product();
        product.setName(updatedData.name());
        productRepository.save(product);
        return this.updateProduct(updatedData);
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

    public ArrayList<ImageDto> addProductImages(Long productId, ArrayList<ImageDto> images) throws ApiException {
        ArrayList<ImageDto> actualImages = this.getProductImages(productId);
        Product product = productRepository.findById(productId).orElse(null);
        int cont = actualImages.size();
        for(ImageDto image : images) {
            ProductImage realImage = new ProductImage();
            realImage.setProduct(product);
            realImage.setPath(image.path());
            realImage.setOrder(cont);
            cont++;
            productImageRepository.save(realImage);
        }
        return this.getProductImages(productId);
    }

    public boolean saveImages(Long productId, List<MultipartFile> files) throws ApiException {
        List<String> imagesPaths = FileManager.SaveAllImages(files);
        ArrayList<ImageDto> images = new ArrayList<>();
        for (String path : imagesPaths) {
            images.add(new ImageDto(path, 0));
        }
        this.addProductImages(productId, images);
        return true;
    }

}
