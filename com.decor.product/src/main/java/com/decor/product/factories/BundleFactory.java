package com.decor.product.factories;

import com.decor.product.dto.bundles.BundleDto;
import com.decor.product.dto.bundles.BundleProductDto;
import com.decor.product.entities.Bundle;
import com.decor.product.entities.BundleProduct;
import com.decor.product.entities.Product;

import java.util.ArrayList;
import java.util.List;

public class BundleFactory {
    public static BundleDto getBundle(Bundle bundle) {
        BundleDto bundleDto = new BundleDto();
        bundleDto.setId(bundle.getId());
        bundleDto.setName(bundle.getName());
        bundleDto.setEnabled(bundle.isEnabled());
        List<BundleProductDto> bundleProducts = new ArrayList<>();
        for (BundleProduct bp: bundle.getProducts()){
            Product p = bp.getProduct();
            BundleProductDto bpdto = new BundleProductDto(
                    p.getId(),
                    p.getName(),
                    bp.getQuantity()
            );
            bundleProducts.add(bpdto);
        }
        bundleDto.setProducts(bundleProducts);
        return bundleDto;
    }
}
