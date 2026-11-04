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

    // Precio del combo cuando pricingType es FIXED (null en los otros). El de lista (suma de los productos) y el
    // precio final se calculan en BundleFactory: nunca leer este campo directo para mostrar o cobrar
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    // Los combos que ya existian quedan en FIXED
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'FIXED'")
    @Column(name = "pricing_type", nullable = false, length = 10)
    private BundlePricingType pricingType = BundlePricingType.FIXED;

    // PERCENT: porcentaje (0 a 100) sobre la suma de los productos. AMOUNT: monto que se resta. FIXED: null
    @Column(precision = 12, scale = 2)
    private BigDecimal discount;

    // Combos vendidos (pedidos pagos de sales). Solo lo ve el administrador
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
