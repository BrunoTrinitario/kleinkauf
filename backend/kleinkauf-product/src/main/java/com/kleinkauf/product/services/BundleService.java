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
    // Los combos estan en el catalogo publico: cada cambio invalida su cache
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

    // Valida todo antes de guardar: el combo se crea completo o no se crea
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
        // Sin el campo se crea habilitado, como en el panel
        b.setEnabled(!Boolean.FALSE.equals(newBundleDto.enabled()));
        applyPricing(b, pricingType, newBundleDto.price(), newBundleDto.discount());
        for (NewBundleProductDto item : items) {
            BundleProduct bp = new BundleProduct();
            bp.setBundle(b);
            bp.setProduct(products.get(item.productId()));
            bp.setQuantity(item.quantity());
            b.getProducts().add(bp);
        }
        // Los productos del combo se guardan en cascada con el combo
        b = this.bundleRepository.save(b);
        catalogCache.invalidate();
        return BundleFactory.getBundle(b);
    }

    private void validateItem(NewBundleProductDto item) throws ApiException {
        if (item == null || item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
            throw new ApiException(ErrorMessages.INVALID_BUNDLE_QUANTITY, HttpStatus.BAD_REQUEST);
        }
    }

    // Precio fijo o descuento sobre la suma de los productos (sin pricingType, precio fijo como antes)
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

    // FIXED: precio null (sin precio) o >= 0. PERCENT: descuento entre 0 y 100. AMOUNT: descuento >= 0
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

    // Guarda solo lo que usa cada tipo: con descuento no queda un precio viejo guardado que pueda leerse por error
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

    // Un combo deshabilitado no aparece en el catalogo ni se puede comprar
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

    // Si uno falla no se aplica ninguno
    @Transactional(rollbackFor = ApiException.class)
    public BundleDto bulkUpdateBundle(Long bundleId, List<NewBundleProductDto> newBundleDtos) throws ApiException {
        BundleDto b = null;
        for  (NewBundleProductDto newBundleDto : newBundleDtos) {
            b = this.addOrUpdateProductToBundle(bundleId, newBundleDto);
        }
        return b;
    }

    // Agrega el producto o cambia su cantidad (si ya estaba no se suma otra vez a la lista)
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
