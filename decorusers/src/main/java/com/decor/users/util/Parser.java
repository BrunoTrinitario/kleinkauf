/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.util;

import com.decor.users.dtos.FullTenantDto;
import com.decor.users.dtos.FullUserDto;
import com.decor.users.dtos.RoleDto;
import com.decor.users.entities.Role;
import com.decor.users.entities.Tenant;
import com.decor.users.entities.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author bruno.trinitario
 */
public class Parser {
    public static FullUserDto fromUserEntityToFullDto(User user, List<RoleDto> roles){
        return new FullUserDto(
            user.getId(),
            user.getName(),
            user.getEmail(),
            roles
        );
    }

    public static FullTenantDto fromTenantEntityToFullDto(Tenant t){
        return new FullTenantDto(
                t.getId().toString(),
                t.getName(),
                t.getUrl(),
                t.getCreated_at(),
                t.getDeleted_at(),
                t.isEnable()
        );
    }
}
