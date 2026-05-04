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
import com.decor.users.services.TenantService;

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
                t.getDbName(),
                t.getDbUser(),
                t.getDbPassword(),
                t.getCreated_at(),
                t.getDeleted_at(),
                t.isEnable()
        );
    }

    public static Tenant formDtoToTenant(FullTenantDto dto){
        Tenant t = new Tenant();
        t.setName(dto.name());
        t.setUrl(dto.url());
        t.setDbName(dto.dbName());
        t.setDbUser(dto.dbUser());
        t.setDbPassword(dto.dbPassword());
        t.setCreated_at(dto.createdAt());
        t.setDeleted_at(dto.deletedAt());
        t.setEnable(dto.enabled());
        return t;
    }
}
