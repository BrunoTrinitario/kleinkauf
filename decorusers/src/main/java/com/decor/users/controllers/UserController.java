/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.decor.users.constants.SuccessMessages;
import com.decor.users.dtos.RegisterUserDto;
import com.decor.users.dtos.RoleDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.decor.users.dtos.FullUserDto;
import com.decor.users.dtos.PatchUserDto;
import com.decor.users.exceptions.ApiException;
import com.decor.users.services.UserService;


/**
 *
 * @author bruno.trinitario
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/all")
    public ResponseEntity<ArrayList<FullUserDto>> getAllUsers() {
        ArrayList<FullUserDto> users = userService.getAllUsers();
        return new ResponseEntity<>(users, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/")
    public ResponseEntity<FullUserDto> createUser(@RequestBody RegisterUserDto userData) throws ApiException {
        FullUserDto createdUser = userService.createUser(userData.name(), userData.email(), userData.password(), userData.roles());
        return new ResponseEntity<>(createdUser, org.springframework.http.HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FullUserDto> getUserById(@PathVariable Long id) throws ApiException {
        FullUserDto user = userService.getUserById(id);
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUserById(@PathVariable Long id) throws ApiException {
        boolean deleted = userService.deleteUserById(id);
        return new ResponseEntity<>(SuccessMessages.USER_DELETED, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<FullUserDto> giveRoleToUser(@PathVariable Long userId, @RequestParam String roleName) throws ApiException {
        FullUserDto user = userService.addRoleToUser(userId, roleName);
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @DeleteMapping("/{id}/roles")
    public ResponseEntity<FullUserDto> removeRoleFromUser(@PathVariable Long userId, @RequestParam String roleName) throws ApiException {
        userService.deleteRoleFromUser(userId, roleName);
        FullUserDto user = userService.getUserById(userId);
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FullUserDto> updateUser(@PathVariable Long userId, @RequestBody PatchUserDto userDto) throws ApiException {
        FullUserDto user = userService.updateUser(userId, userDto.name(), userDto.password());
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/{id}/roles")
    public ResponseEntity<List<RoleDto>> getUserRoles(@PathVariable Long userId) throws ApiException {
        List<RoleDto> roles = userService.getUserRoles(userId);
        return new ResponseEntity<>(roles, org.springframework.http.HttpStatus.OK);
    }

}
