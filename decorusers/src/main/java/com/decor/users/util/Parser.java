/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.util;

import com.decor.users.dtos.FullUserDto;
import com.decor.users.dtos.RoleDto;
import com.decor.users.dtos.TenantDto;
import com.decor.users.entities.Tenant;
import com.decor.users.entities.User;

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

    public static TenantDto fromTenantEntityToFullDto(Tenant t){
        return new TenantDto(
                t.getId(),
                t.getConfigId(),
                t.getSchema(),
                t.getEnable(),
                t.getCreatedAt(),
                t.getDeletedAt()
        );
    }

}
