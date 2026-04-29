package com.decor.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FullProductDto{
    private Long id;
    private String name;
    private String category;
    private String description;
    private int stock;
    private MetaDataDto metadata;
    private List<ImageDto> images;
}
