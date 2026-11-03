package com.kleinkauf.users.controllers;

import com.kleinkauf.users.dtos.FullUserDto;
import com.kleinkauf.common.dto.RoleDto;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.services.UserService;
import com.kleinkauf.users.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/internal/users")
public class MSUserController {

    @Autowired
    UserService userService;

    @Autowired
    CacheUtil cacheUtil;

    @GetMapping("/{id}")
    public ResponseEntity<FullUserDto> getUserById(@PathVariable Long id) throws ApiException {
        FullUserDto user = userService.getUserById(id);
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/roles/{id}")
    public ResponseEntity<List<RoleDto>> getUserRolesById(@PathVariable Long id) throws ApiException {
        List<RoleDto> roles = userService.getUserRoles(id);
        return new ResponseEntity<>(roles, org.springframework.http.HttpStatus.OK);
    }
}
