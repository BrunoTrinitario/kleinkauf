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
    // Mascara de bits de PermissionArea de este rol en la tienda (la calcula users)
    private int permissions;

}
