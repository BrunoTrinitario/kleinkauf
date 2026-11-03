package com.kleinkauf.product.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/*
Slugs para los links del catalogo: 'Mesa de Roble (2 m)' -> 'mesa-de-roble-2-m'.
Solo a-z, 0-9 y guiones; sin acentos. Si ya existe se agrega un numero: mesa-de-roble-2.
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

    // exists: si el slug ya esta usado (incluye productos borrados)
    public static String unique(String name, Predicate<String> exists) {
        String base = slugify(name);
        String candidate = base;
        for (int i = 2; exists.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
