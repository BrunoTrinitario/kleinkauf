package com.kleinkauf.product.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/*
Slugs for the catalog links: 'Mesa de Roble (2 m)' -> 'mesa-de-roble-2-m'.
Only a-z, 0-9 and hyphens; no accents. If it already exists a number is added: mesa-de-roble-2.
 */
public class Slugs {

    static final int MAX_LENGTH = 80;
    private static final String DEFAULT = "producto";

    public static String slugify(String name) {
        String slug = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? DEFAULT : slug;
    }

    // exists: whether the slug is already used (deleted products included)
    public static String unique(String name, Predicate<String> exists) {
        String base = slugify(name);
        String candidate = base;
        for (int i = 2; exists.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
