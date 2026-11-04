package com.kleinkauf.product.entities;

import com.kleinkauf.common.audit.Audited;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Entity
@Audited("ProductMetadata")
@Table(name = "productmetadata")
@Getter
@Setter
@AllArgsConstructor
public class ProductMetaData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private String material;
    private String color;
    private String style;
    private double height;
    private double width;
    private double length;
    private double weight;

    public ProductMetaData(){
        // No data = empty (not a filler value that is later shown or exported as if it were real)
        this.material = "";
        this.color = "";
        this.style = "";
        this.height = 0.0;
        this.width = 0.0;
        this.length = 0.0;
        this.weight = 0.0;
    }

}
