package com.decor.users.controllers;

import com.decor.common.servlet.exceptions.ApiException;
import com.decor.users.constants.ErrorMessages;
import com.decor.users.dtos.RolePermissionDto;
import com.decor.users.services.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
Permisos por rol de la tienda. Solo ADMINISTRADOR (/api/v1/users/** en SecurityConfig).
permissions: mascara de bits (PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16).
 */
@RestController
@RequestMapping("/api/v1/users/roles")
public class RolePermissionController {

    @Autowired
    private RolePermissionService rolePermissionService;

    @GetMapping("/permissions")
    public ResponseEntity<List<RolePermissionDto>> list() {
        return ResponseEntity.ok(rolePermissionService.list());
    }

    // Solo EMPLEADO y CLIENTE; USUARIOS no se puede dar. Body: {"permissions": 5}
    @PutMapping("/{role}/permissions")
    public ResponseEntity<RolePermissionDto> update(@PathVariable String role, @RequestBody UpdatePermissionsDto dto) throws ApiException {
        if (dto == null || dto.permissions() == null) {
            throw new ApiException(ErrorMessages.INVALID_PERMISSIONS, HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(rolePermissionService.update(role, dto.permissions()));
    }

    public record UpdatePermissionsDto(Integer permissions) {
    }
}
