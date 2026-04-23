/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.decorusers.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.decor.users.decorusers.dtos.FullUserDto;
import com.decor.users.decorusers.dtos.LoggedUser;
import com.decor.users.decorusers.dtos.LoginUserDto;
import com.decor.users.decorusers.dtos.PatchUserDto;
import com.decor.users.decorusers.dtos.RegisterUserDto;
import com.decor.users.decorusers.entities.User;
import com.decor.users.decorusers.exceptions.ApiException;
import com.decor.users.decorusers.security.CustomUserDetails;
import com.decor.users.decorusers.services.UserService;

/**
 *
 * @author bruno.trinitario
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<LoggedUser> Login(@RequestBody LoginUserDto userData) throws ApiException {
        LoggedUser loggedUser = userService.login(userData.name(), userData.password());
        return new ResponseEntity<>(loggedUser, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<LoggedUser> Register(@RequestBody RegisterUserDto userData) throws ApiException {
        userService.createUser(userData.name(), userData.email(), userData.password(), userData.roles());
        LoggedUser loggedUser = userService.login(userData.name(), userData.password());
        return new ResponseEntity<>(loggedUser, org.springframework.http.HttpStatus.CREATED);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Boolean> Delete(@AuthenticationPrincipal CustomUserDetails userDetails) throws ApiException {
        boolean deleted = userService.deleteUserById(userDetails.getUser().getId());
        return new ResponseEntity<>(deleted, org.springframework.http.HttpStatus.OK);
    }

    @PutMapping("/update")
    public ResponseEntity<FullUserDto> Update(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody PatchUserDto userData) throws ApiException {
        User user = userDetails.getUser();
        FullUserDto updatedUser = userService.updateUser(user.getId(), userData.name(), userData.password());
        return new ResponseEntity<>(updatedUser, org.springframework.http.HttpStatus.OK);
    }
}
