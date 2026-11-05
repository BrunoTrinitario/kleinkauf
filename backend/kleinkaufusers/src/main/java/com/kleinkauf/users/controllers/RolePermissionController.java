package com.kleinkauf.users.controllers;

import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.constants.ErrorMessages;
import com.kleinkauf.users.dtos.RolePermissionDto;
import com.kleinkauf.users.services.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
Store permissions per role. ADMINISTRADOR only (/api/v1/users/** in SecurityConfig).
permissions: bit mask (PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16).
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

    // Only EMPLEADO and CLIENTE; USUARIOS cannot be granted. Body: {"permissions": 5}
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
