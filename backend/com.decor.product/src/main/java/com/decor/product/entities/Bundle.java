package com.decor.product.entities;

import com.decor.common.audit.Audited;
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

    // Precio del combo; el de lista (suma de los productos) se calcula en BundleFactory
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

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
