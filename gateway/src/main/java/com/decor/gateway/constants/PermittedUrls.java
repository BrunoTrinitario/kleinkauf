package com.decor.gateway.constants;

import java.util.List;

public class PermittedUrls {

    public static String INTERNAL_PATH = "/api/internal";

    public static List<String> getExcludedPaths() {
        return List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/products/all",

                "/api/v1/products/{id}",
                "/api/v1/products/{id}/metadata",
                "/api/v1/products/search",
                "/products/images/{any}"
        );
    }

    public static boolean isPermitted(String path) {

        String cleanPath = path.split("\\?")[0]; // remover query params

        for (String allowed : getExcludedPaths()) {
            String regex = allowed
                    .replace("{id}", "\\d+")
                    .replace("{any}",".*$")
                    .replace("/", "\\/");

            regex = "^" + regex + "$";

            if (cleanPath.matches(regex)) {
                return true;
            }
        }
        return false;
    }
}
