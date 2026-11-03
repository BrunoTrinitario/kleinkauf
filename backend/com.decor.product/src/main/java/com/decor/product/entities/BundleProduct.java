package com.decor.product.entities;

import com.decor.common.audit.Audited;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Audited
@Table(name = "bundle_products")
@Getter
@Setter
public class BundleProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "bundle_id")
    private Bundle bundle;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;
}