package com.decor.users.decorusers.util;

import com.decor.users.decorusers.constants.RolesType;

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
