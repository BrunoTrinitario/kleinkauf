package com.decor.users.util;

import com.decor.users.constants.RolesType;

public class RoleUtility {
    public static boolean existsRole(String role) {
        try {
            RolesType.valueOf(role);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
