package com.kleinkauf.product.services;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.constants.ErrorMessages;
import com.kleinkauf.product.dto.bundles.BundleDto;
import com.kleinkauf.product.dto.bundles.NewBundleDto;
import com.kleinkauf.product.dto.bundles.NewBundleProductDto;
import com.kleinkauf.product.dto.bundles.UpdateBundlePriceDto;
import com.kleinkauf.product.entities.Bundle;
import com.kleinkauf.product.entities.BundlePricingType;
import com.kleinkauf.product.entities.BundleProduct;
import com.kleinkauf.product.entities.Product;
import com.kleinkauf.product.factories.BundleFactory;
import com.kleinkauf.product.repositories.BundleProductRepository;
import com.kleinkauf.product.repositories.BundleRepository;
import com.kleinkauf.product.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BundleService {
    @Autowired
    private BundleRepository bundleRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BundleProductRepository bundleProductRepository;
    // Bundles are in the public catalog: every change invalidates its cache
    @Autowired
    private CatalogCacheService catalogCache;

    public ArrayList<BundleDto> getAllBundles() {
        ArrayList<BundleDto> bundleDtos = new ArrayList<>();
        for  (Bundle bundle : bundleRepository.findAll()) {
            bundleDtos.add(
                    BundleFactory.getBundle(bundle)
            );
        }
        return bundleDtos;
    }

    public BundleDto getBundleById(Long id) throws ApiException {
        Bundle b = this.bundleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.BUNDLE_NOT_FOUND, HttpStatus.NOT_FOUND));
        return BundleFactory.getBundle(b);
    }

    // Validates everything before saving: the bundle is created complete or not at all
    @Transactional
    public BundleDto createBundle(NewBundleDto newBundleDto) throws ApiException {
        String name = newBundleDto.name() != null ? newBundleDto.name().trim() : "";
        if (name.isEmpty() || name.length() > 255) {
            throw new ApiException(ErrorMessages.BUNDLE_NAME_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        BundlePricingType pricingType = pricingTypeOf(newBundleDto.pricingType());
        validatePricing(pricingType, newBundleDto.price(), newBundleDto.discount());
        List<NewBundleProductDto> items = newBundleDto.products();
        if (items == null || items.isEmpty()) {
            throw new ApiException(ErrorMessages.BUNDLE_PRODUCTS_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        Set<Long> ids = new HashSet<>();
        for (NewBundleProductDto item : items) {
            validateItem(item);
            if (!ids.add(item.productId())) {
                throw new ApiException(ErrorMessages.BUNDLE_PRODUCT_REPEATED, HttpStatus.BAD_REQUEST);
            }
        }
        Map<Long, Product> products = new HashMap<>();
        for (Product p : this.productRepository.findAllById(ids)) {
            products.put(p.getId(), p);
        }
        if (products.size() != ids.size()) {
            throw new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Bundle b = new Bundle();
        b.setName(name);
        // Without the field it is created enabled, as in the dashboard
        b.setEnabled(!Boolean.FALSE.equals(newBundleDto.enabled()));
        applyPricing(b, pricingType, newBundleDto.price(), newBundleDto.discount());
        for (NewBundleProductDto item : items) {
            BundleProduct bp = new BundleProduct();
            bp.setBundle(b);
            bp.setProduct(products.get(item.productId()));
            bp.setQuantity(item.quantity());
            b.getProducts().add(bp);
        }
        // The bundle's products are saved in cascade with the bundle
        b = this.bundleRepository.save(b);
        catalogCache.invalidate();
        return BundleFactory.getBundle(b);
    }

    private void validateItem(NewBundleProductDto item) throws ApiException {
        if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
            throw new ApiException(ErrorMessages.INVALID_BUNDLE_QUANTITY, HttpStatus.BAD_REQUEST);
        }
    }

    // Fixed price or discount over the sum of the products (without pricingType, fixed price as before)
    public BundleDto updatePrice(Long id, UpdateBundlePriceDto dto) throws ApiException {
        BundlePricingType pricingType = pricingTypeOf(dto.pricingType());
        validatePricing(pricingType, dto.price(), dto.discount());
        Bundle b = this.bundleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.BUNDLE_NOT_FOUND, HttpStatus.NOT_FOUND));
        applyPricing(b, pricingType, dto.price(), dto.discount());
        this.bundleRepository.save(b);
        catalogCache.invalidate();
        return BundleFactory.getBundle(b);
    }

    private static BundlePricingType pricingTypeOf(BundlePricingType type) {
        return type != null ? type : BundlePricingType.FIXED;
    }

    // FIXED: price null (no price) or >= 0. PERCENT: discount between 0 and 100. AMOUNT: discount >= 0
    private void validatePricing(BundlePricingType type, BigDecimal price, BigDecimal discount) throws ApiException {
        if (type == BundlePricingType.FIXED) {
            if (price != null && price.signum() < 0) {
                throw new ApiException(ErrorMessages.INVALID_PRICE, HttpStatus.BAD_REQUEST);
            }
            return;
        }
        boolean invalid = discount == null || discount.signum() < 0
                || (type == BundlePricingType.PERCENT && discount.compareTo(BigDecimal.valueOf(100)) > 0);
        if (invalid) {
            throw new ApiException(ErrorMessages.INVALID_BUNDLE_DISCOUNT, HttpStatus.BAD_REQUEST);
        }
    }

    // Stores only what each type uses: with a discount no old price stays stored that could be read by mistake
    private static void applyPricing(Bundle b, BundlePricingType type, BigDecimal price, BigDecimal discount) {
        b.setPricingType(type);
        b.setPrice(type == BundlePricingType.FIXED ? price : null);
        b.setDiscount(type == BundlePricingType.FIXED ? null : discount);
    }

    public Boolean deleteBundleById(Long id) throws ApiException {
        Bundle b = this.bundleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.BUNDLE_NOT_FOUND, HttpStatus.NOT_FOUND));
        this.bundleRepository.delete(b);
        catalogCache.invalidate();
        return true;
    }

    // A disabled bundle does not appear in the catalog and cannot be bought
    public BundleDto setEnabled(Long id, Boolean enabled) throws ApiException {
        if (enabled == null) {
            throw new ApiException(ErrorMessages.BUNDLE_ENABLED_REQUIRED, HttpStatus.BAD_REQUEST);
        }
        Bundle b = this.bundleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorMessages.BUNDLE_NOT_FOUND, HttpStatus.NOT_FOUND));
        b.setEnabled(enabled);
        this.bundleRepository.save(b);
        catalogCache.invalidate();
        return BundleFactory.getBundle(b);
    }

    // If one fails none is applied
    @Transactional(rollbackFor = ApiException.class)
    public BundleDto bulkUpdateBundle(Long bundleId, List<NewBundleProductDto> newBundleDtos) throws ApiException {
        BundleDto b = null;
        for  (NewBundleProductDto newBundleDto : newBundleDtos) {
            b = this.addOrUpdateProductToBundle(bundleId, newBundleDto);
        }
        return b;
    }

    // Adds the product or changes its quantity (if it was already there it is not added to the list again)
    public BundleDto addOrUpdateProductToBundle(Long bundleId, NewBundleProductDto bundleProduct) throws ApiException {
        validateItem(bundleProduct);
        Bundle b = this.bundleRepository.findById(bundleId)
                .orElseThrow(() -> new ApiException(ErrorMessages.BUNDLE_NOT_FOUND, HttpStatus.NOT_FOUND));
        Product p = this.productRepository.findById(bundleProduct.productId())
                .orElseThrow(() -> new ApiException(ErrorMessages.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
        BundleProduct bp = this.bundleProductRepository.findByBundleAndProduct(b,p).orElse(null);
        if (bp == null){
            bp = new BundleProduct();
            bp.setBundle(b);
            bp.setProduct(p);
            b.getProducts().add(bp);
        }
        bp.setQuantity(bundleProduct.quantity());
        this.bundleProductRepository.save(bp);
        catalogCache.invalidate();
        return BundleFactory.getBundle(b);
    }

    public boolean removeProductFromBundle(Long bundleId, NewBundleProductDto bundleProduct) {
        Bundle b = this.bundleRepository.findById(bundleId).orElse(null);
        Product p = this.productRepository.findById(bundleProduct.productId()).orElse(null);
        BundleProduct bp = this.bundleProductRepository.findByBundleAndProduct(b,p).orElse(null);
        if (bp!= null){
            this.bundleProductRepository.delete(bp);
            catalogCache.invalidate();
        }
        return true;
    }

}
