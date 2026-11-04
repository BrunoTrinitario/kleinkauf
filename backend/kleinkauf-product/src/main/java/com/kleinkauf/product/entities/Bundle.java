package com.kleinkauf.product.entities;

import com.kleinkauf.common.audit.Audited;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Audited
@Table(name = "bundles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bundle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean enabled;

    // Bundle price when pricingType is FIXED (null in the others). The list price (sum of the products) and the
    // final price are computed in BundleFactory: never read this field directly to display or charge
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    // Bundles that already existed stay FIXED
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'FIXED'")
    @Column(name = "pricing_type", nullable = false, length = 10)
    private BundlePricingType pricingType = BundlePricingType.FIXED;

    // PERCENT: percentage (0 to 100) over the sum of the products. AMOUNT: amount subtracted. FIXED: null
    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    // Bundles sold (paid sales orders). Only the administrator sees it
    @ColumnDefault("0")
    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    @OneToMany(
            mappedBy = "bundle",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<BundleProduct> products = new ArrayList<>();

}
