package com.kleinkauf.product.entities;

import com.kleinkauf.common.audit.Audited;
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
@Audited
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

    // For the public catalog links ('mesa-de-roble'). Generated on creation and does not change with the name
    @Column(unique = true, length = 100)
    private String slug;

    // Only modified with stock movements (StockService)
    private int stock;

    // Below this value the product shows up in the low stock alert (0 = no alert)
    @ColumnDefault("0")
    @Column(name = "min_stock", nullable = false)
    private int minStock;

    // Sale price (public)
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    // Cost (only the administrator sees it)
    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    // Featured in the catalog (home page)
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean featured;

    // Order among featured products (lower first); null = last, by name
    @Column(name = "featured_order")
    private Integer featuredOrder;

    // Units sold (paid sales orders). Only the administrator sees it
    @ColumnDefault("0")
    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL)
    private ProductMetaData metaData;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

}
