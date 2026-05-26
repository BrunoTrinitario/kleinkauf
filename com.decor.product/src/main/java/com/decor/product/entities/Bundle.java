package com.decor.product.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
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

    @OneToMany(
            mappedBy = "bundle",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<BundleProduct> products = new ArrayList<>();

}
