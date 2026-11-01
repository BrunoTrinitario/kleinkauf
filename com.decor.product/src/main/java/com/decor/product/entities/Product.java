package com.decor.product.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE product SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(unique = true)
    private String name;

    private String category;

    private String description;

    // Solo se modifica con movimientos de stock (StockService)
    private int stock;

    // Debajo de este valor el producto aparece en la alerta de stock bajo (0 = sin alerta)
    @ColumnDefault("0")
    @Column(name = "min_stock", nullable = false)
    private int minStock;

    // Precio de venta (publico)
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    // Costo (solo lo ve el administrador)
    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL)
    private ProductMetaData metaData;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

}
