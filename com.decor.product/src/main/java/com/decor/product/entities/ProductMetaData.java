package com.decor.product.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "productmetadata")
@Getter
@Setter
@NoArgsConstructor
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

}
