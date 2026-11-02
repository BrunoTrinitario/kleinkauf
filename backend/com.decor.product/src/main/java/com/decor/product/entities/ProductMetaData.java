package com.decor.product.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Entity
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
        this.material = "unknown";
        this.color = "unknown";
        this.style = "unknown";
        this.height = 0.0;
        this.width = 0.0;
        this.length = 0.0;
        this.weight = 0.0;
    }

}
