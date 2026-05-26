package com.decor.product.dto.bundles;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BundleDto{
    private Long id;
    private String name;
    private boolean enabled;
    private List<BundleProductDto> products;

}
