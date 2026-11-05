package com.kleinkauf.users.util;

import com.kleinkauf.users.constants.RolesType;

public class RoleUtility {
    // Roles that can be assigned inside a store (SUPERADMIN is left out)
    public static boolean existsRole(String role) {
        try {
            return RolesType.valueOf(role) != RolesType.SUPERADMIN;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
