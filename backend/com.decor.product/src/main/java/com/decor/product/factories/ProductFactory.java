package com.decor.product.factories;

import com.decor.product.dto.FullProductDto;
import com.decor.product.dto.ImageDto;
import com.decor.product.dto.LightProductDto;
import com.decor.product.dto.MetaDataDto;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.entities.ProductMetaData;
import com.decor.product.repositories.ProductImageRepository;
import com.decor.product.repositories.ProductMetaDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class ProductFactory {
    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductMetaDataRepository productMetaDataRepository;

    public FullProductDto getFullProductInfo(Product p){
        ProductMetaData md = productMetaDataRepository.findByProduct_Id(p.getId()).orElse(null);
        Collection<ProductImage> im = productImageRepository.findByProduct_Id(p.getId());
        MetaDataDto metaDataDto;
        ArrayList<ImageDto> images = new ArrayList<>();
        for  (ProductImage productImage : im) {
            images.add(new ImageDto(
                    productImage.getId(),
                    productImage.getPath(),
                    productImage.getImageOrder()
            ));
        }
        FullProductDto fullProductDto = new FullProductDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getCategory(),
                p.getDescription(),
                p.getStock(),
                p.getPrice(),
                p.isFeatured(),
                null,
                images
        );
        if (md!=null){
            metaDataDto = new MetaDataDto(
                    md.getMaterial(),
                    md.getColor(),
                    md.getStyle(),
                    md.getHeight(),
                    md.getWidth(),
                    md.getLength(),
                    md.getWeight()
            );
            fullProductDto.setMetadata(metaDataDto);
        }
        return fullProductDto;
    }

    public LightProductDto getLightProductInfo(Product p, List<String> imagesPath){
        return  new LightProductDto(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getCategory(),
                p.getDescription(),
                p.getStock(),
                p.getPrice(),
                p.isFeatured(),
                imagesPath,
                p.getMinStock()
        );
    }
}
