package com.decor.users.util;

import com.decor.users.constants.RolesType;

public class RoleUtility {
    // Roles que se pueden asignar dentro de una tienda (SUPERADMIN queda afuera)
    public static boolean existsRole(String role) {
        try {
            return RolesType.valueOf(role) != RolesType.SUPERADMIN;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
