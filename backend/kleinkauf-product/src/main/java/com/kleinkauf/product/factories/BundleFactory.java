package com.kleinkauf.product.factories;

import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.dto.bundles.BundleProductDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundlePricingType;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.entities.ProductImage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BundleFactory {
    public static BundleDto getBundle(Bundle bundle) {
        BundleDto bundleDto = new BundleDto();
        bundleDto.setId(bundle.getId());
        bundleDto.setName(bundle.getName());
        bundleDto.setEnabled(bundle.isEnabled());
        bundleDto.setPrice(price(bundle));
        bundleDto.setPricingType(pricingType(bundle));
        bundleDto.setDiscount(bundle.getDiscount());
        List<BundleProductDto> bundleProducts = new ArrayList<>();
        for (BundleProduct bp: bundle.getProducts()){
            Product p = bp.getProduct();
            BundleProductDto bpdto = new BundleProductDto(
                    p.getId(),
                    p.getName(),
                    bp.getQuantity(),
                    p.getPrice(),
                    firstImage(p)
            );
            bundleProducts.add(bpdto);
        }
        bundleDto.setProducts(bundleProducts);
        bundleDto.setListPrice(listPrice(bundle.getProducts()));
        bundleDto.setAvailable(available(bundle.getProducts()));
        return bundleDto;
    }

    /*
    Precio final del combo: el unico lugar donde se calcula (panel, catalogo y la reserva que le da el precio a sales).
    - FIXED: el precio guardado (null = sin precio).
    - PERCENT: suma de los productos menos el porcentaje. AMOUNT: suma menos el monto, sin bajar de 0.
    Con descuento, si algun producto no tiene precio o el combo no tiene productos, queda sin precio (no se vende
    online). Redondeado a 2 decimales.
    */
    public static BigDecimal price(Bundle bundle) {
        BundlePricingType type = pricingType(bundle);
        if (type == BundlePricingType.FIXED) return bundle.getPrice();
        BigDecimal discount = bundle.getDiscount();
        BigDecimal list = listPrice(bundle.getProducts());
        if (discount == null || list == null || bundle.getProducts().isEmpty()) return null;
        BigDecimal price = type == BundlePricingType.PERCENT
                ? list.multiply(HUNDRED.subtract(discount)).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : list.subtract(discount).max(BigDecimal.ZERO);
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    // Un combo creado antes del tipo de precio (o en un test) cuenta como FIXED
    private static BundlePricingType pricingType(Bundle bundle) {
        return bundle.getPricingType() != null ? bundle.getPricingType() : BundlePricingType.FIXED;
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

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

    // La de menor orden, como en el catalogo
    private static String firstImage(Product p) {
        if (p.getImages() == null) return null;
        return p.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getImageOrder))
                .map(ProductImage::getPath)
                .orElse(null);
    }

    private static int quantity(BundleProduct bp) {
        return bp.getQuantity() != null ? bp.getQuantity() : 0;
    }
}
