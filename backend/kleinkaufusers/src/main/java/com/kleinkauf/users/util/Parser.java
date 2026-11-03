/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.kleinkauf.users.util;

import com.kleinkauf.users.dtos.FullUserDto;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.dto.TenantDto;
import com.kleinkauf.users.entities.Tenant;
import com.kleinkauf.users.entities.User;

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
