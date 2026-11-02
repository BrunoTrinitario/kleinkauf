package com.decor.common.security;

import java.util.EnumSet;
import java.util.Set;

/*
Areas de permisos. Cada rol de una tienda tiene una mascara de bits (role_permissions en users):
PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16.
- PRODUCTOS: crear, editar y borrar productos, imagenes, metadatos y destacados.
- PRECIOS: ver costo y margen, editar precio y costo.
- STOCK: ver y registrar movimientos, ver stock bajo.
- COMBOS: crear, editar y borrar combos y cambiar su precio.
- USUARIOS: gestionar los usuarios de la tienda (no se delega: solo ADMINISTRADOR).
Importar/exportar CSV pide PRODUCTOS y PRECIOS. El ADMINISTRADOR tiene todas.
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

    // Authority de Spring Security: PERM_PRODUCTOS, PERM_PRECIOS, ...
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
