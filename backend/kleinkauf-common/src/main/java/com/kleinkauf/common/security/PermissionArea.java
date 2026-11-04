package com.kleinkauf.common.security;

import java.util.EnumSet;
import java.util.Set;

/*
Permission areas. Each store role has a bit mask (role_permissions in users):
PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16.
- PRODUCTOS: create, edit and delete products, images, metadata and featured products.
- PRECIOS: see cost and margin, edit price and cost.
- STOCK: see and record movements, see low stock.
- COMBOS: create, edit and delete bundles and change their price.
- USUARIOS: manage the store's users (not delegable: ADMINISTRADOR only).
CSV import/export requires PRODUCTOS and PRECIOS. ADMINISTRADOR has all of them.
 */
public enum PermissionArea {
    PRODUCTOS(1),
    PRECIOS(2),
    STOCK(4),
    COMBOS(8),
    USUARIOS(16);

    public static final int ALL = 31;

    private final int bit;

    PermissionArea(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    // Spring Security authority: PERM_PRODUCTOS, PERM_PRECIOS, ...
    public String authority() {
        return "PERM_" + name();
    }

    public boolean in(int mask) {
        return (mask & bit) != 0;
    }

    public static Set<PermissionArea> fromMask(int mask) {
        Set<PermissionArea> areas = EnumSet.noneOf(PermissionArea.class);
        for (PermissionArea area : values()) {
            if (area.in(mask)) areas.add(area);
        }
        return areas;
    }
}
