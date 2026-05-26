package com.decor.product.services;

import com.decor.product.dto.bundles.BundleDto;
import com.decor.product.dto.bundles.NewBundleDto;
import com.decor.product.dto.bundles.NewBundleProductDto;
import com.decor.product.entities.Bundle;
import com.decor.product.entities.BundleProduct;
import com.decor.product.entities.Product;
import com.decor.product.factories.BundleFactory;
import com.decor.product.repositories.BundleProductRepository;
import com.decor.product.repositories.BundleRepository;
import com.decor.product.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class BundleService {
    @Autowired
    private BundleRepository bundleRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BundleProductRepository bundleProductRepository;

    public ArrayList<BundleDto> getAllBundles() {
        ArrayList<BundleDto> bundleDtos = new ArrayList<>();
        for  (Bundle bundle : bundleRepository.findAll()) {
            bundleDtos.add(
                    BundleFactory.getBundle(bundle)
            );
        }
        return bundleDtos;
    }

    public BundleDto getBundleById(Long id) {
        Bundle b = this.bundleRepository.findById(id).orElse(null);
        if (b != null)
            return BundleFactory.getBundle(b);
        else
            return null;
    }

    public BundleDto createBundle(NewBundleDto newBundleDto) {
        Bundle b = new Bundle();
        b.setName(newBundleDto.name());
        b.setEnabled(newBundleDto.enabled());
        b = this.bundleRepository.save(b);
        return BundleFactory.getBundle(b);
    }

    public Boolean deleteBundleById(Long id) {
        Bundle b = this.bundleRepository.findById(id).orElse(null);
        if (b != null)
            this.bundleRepository.delete(b);
        return true;
    }

    public Boolean changeBundleStatus(Long id){
        Bundle b = this.bundleRepository.findById(id).orElse(null);
        if (b != null){
            b.setEnabled(!b.isEnabled());
            this.bundleRepository.save(b);
        }
        return true;
    }

    public BundleDto bulkUpdateBundle(Long bundleId, List<NewBundleProductDto> newBundleDtos) {
        BundleDto b = null;
        for  (NewBundleProductDto newBundleDto : newBundleDtos) {
            b = this.addOrUpdateProductToBundle(bundleId, newBundleDto);
        }
        return b;
    }

    public BundleDto addOrUpdateProductToBundle(Long bundleId, NewBundleProductDto bundleProduct) {
        Bundle b = this.bundleRepository.findById(bundleId).orElse(null);
        Product p = this.productRepository.findById(bundleProduct.productId()).orElse(null);
        BundleProduct bp = this.bundleProductRepository.findByBundleAndProduct(b,p).orElse(null);
        if (bp == null){
            bp = new BundleProduct();
            bp.setBundle(b);
            bp.setProduct(p);
        }
        bp.setQuantity(bundleProduct.quantity());
        bp = this.bundleProductRepository.save(bp);
        b.getProducts().add(bp);
        return BundleFactory.getBundle(b);
    }

    public boolean removeProductFromBundle(Long bundleId, NewBundleProductDto bundleProduct) {
        Bundle b = this.bundleRepository.findById(bundleId).orElse(null);
        Product p = this.productRepository.findById(bundleProduct.productId()).orElse(null);
        BundleProduct bp = this.bundleProductRepository.findByBundleAndProduct(b,p).orElse(null);
        if (bp!= null){
            this.bundleProductRepository.delete(bp);
        }
        return true;
    }

}
