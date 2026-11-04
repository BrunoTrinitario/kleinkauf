package com.kleinkauf.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleDto implements Serializable {

    private String type;
    // PermissionArea bit mask of this role in the store (computed by users)
    private int permissions;

}
