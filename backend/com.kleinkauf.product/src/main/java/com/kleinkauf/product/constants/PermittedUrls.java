package com.kleinkauf.product.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

    private static final List<String> PATTERNS = List.of(
            "^/all$",
            "^/\\d+$",
            "^/search(\\?.*)?$",
            "^/\\d+/images$",
            "^/\\d+/metadata$",
            "^/products/images/.*$"
    );

    public static boolean isPermitted(String path) {

        String cleanPath = path.split("\\?")[0];

        return PATTERNS.stream()
                .anyMatch(cleanPath::matches);
    }
}
