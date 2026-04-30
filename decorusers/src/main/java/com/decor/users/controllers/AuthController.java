/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.controllers;

import com.decor.users.constants.ErrorMessages;
import com.decor.users.constants.SuccessMessages;
import com.decor.users.dtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.decor.users.entities.User;
import com.decor.users.exceptions.ApiException;
import com.decor.users.security.CustomUserDetails;
import com.decor.users.services.UserService;
import java.util.Set;

/**
 *
 * @author bruno.trinitario
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<FullUserDto> getOwnData(@AuthenticationPrincipal CustomUserDetails userDetails) throws ApiException {
        FullUserDto user = userService.getUserById(userDetails.getUser().getId());
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<LoggedUser> Login(@RequestBody LoginUserDto userData) throws ApiException {
        LoggedUser loggedUser = userService.login(userData.name(), userData.password());
        return new ResponseEntity<>(loggedUser, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<LoggedUser> Register(@RequestBody NewUserDto userData) throws ApiException {
        userService.createUser(userData.name(), userData.email(), userData.password(), Set.of("CLIENTE"));
        LoggedUser loggedUser = userService.login(userData.name(), userData.password());
        return new ResponseEntity<>(loggedUser, org.springframework.http.HttpStatus.CREATED);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> Delete(@AuthenticationPrincipal CustomUserDetails userDetails) throws ApiException {
        boolean deleted = userService.deleteUserById(userDetails.getUser().getId());
        return new ResponseEntity<>(SuccessMessages.USER_DELETED, org.springframework.http.HttpStatus.OK);
    }

    @PutMapping("/update")
    public ResponseEntity<FullUserDto> Update(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody PatchUserDto userData) throws ApiException {
        User user = userDetails.getUser();
        FullUserDto updatedUser = userService.updateUser(user.getId(), userData.name(), userData.password());
        return new ResponseEntity<>(updatedUser, org.springframework.http.HttpStatus.OK);
    }
}
