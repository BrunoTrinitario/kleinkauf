package com.kleinkauf.product.factories;

import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.dto.bundles.BundleProductDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BundleFactory {
    public static BundleDto getBundle(Bundle bundle) {
        BundleDto bundleDto = new BundleDto();
        bundleDto.setId(bundle.getId());
        bundleDto.setName(bundle.getName());
        bundleDto.setEnabled(bundle.isEnabled());
        bundleDto.setPrice(bundle.getPrice());
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
        bundleDto.setListPrice(listPrice(bundle.getProducts()));
        bundleDto.setAvailable(available(bundle.getProducts()));
        return bundleDto;
    }

    // Suma de precio * cantidad; null si algun producto no tiene precio
    public static BigDecimal listPrice(List<BundleProduct> products) {
        BigDecimal total = BigDecimal.ZERO;
        for (BundleProduct bp : products) {
            BigDecimal price = bp.getProduct().getPrice();
            if (price == null) return null;
            total = total.add(price.multiply(BigDecimal.valueOf(quantity(bp))));
        }
        return total;
    }

    // El producto con menos stock (en relacion a lo que pide el combo) limita cuantos combos se arman
    public static int available(List<BundleProduct> products) {
        if (products.isEmpty()) return 0;
        int available = Integer.MAX_VALUE;
        for (BundleProduct bp : products) {
            int quantity = quantity(bp);
            if (quantity == 0) continue;
            available = Math.min(available, Math.max(bp.getProduct().getStock(), 0) / quantity);
        }
        return available == Integer.MAX_VALUE ? 0 : available;
    }

    private static int quantity(BundleProduct bp) {
        return bp.getQuantity() != null ? bp.getQuantity() : 0;
    }
}
