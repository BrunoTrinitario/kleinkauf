package com.decor.users.dtos;

import com.decor.common.dto.RoleDto;

import com.decor.users.constants.CacheKeys;
import lombok.Getter;

import java.util.List;

@Getter
public class CachedUser {
    private String key;
    private String hashKey = "roles";
    private List<RoleDto> roles;
    private long duration = 1;

    public CachedUser(Long userId, String tenantSchema, List<RoleDto> roles) {
        this.key = CacheKeys.CACHE_USER(userId, tenantSchema);
    }

}
