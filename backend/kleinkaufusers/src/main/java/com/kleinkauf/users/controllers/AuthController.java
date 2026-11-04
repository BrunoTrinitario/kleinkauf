/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.kleinkauf.users.controllers;

import com.kleinkauf.users.constants.SuccessMessages;
import com.kleinkauf.users.dtos.*;
import com.kleinkauf.users.dtos.stats.PasswordDto;
import com.kleinkauf.common.servlet.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.users.services.UserService;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author bruno.trinitario
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<FullUserDto> getOwnData(@AuthenticationPrincipal CustomUserDetails userDetails) throws ApiException {
        FullUserDto user = userService.getUserById(userDetails.getId());
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<LoggedUser> Login(@RequestBody LoginUserDto userData) throws ApiException {
        LoggedUser loggedUser = userService.login(userData.email(), userData.password());
        return new ResponseEntity<>(loggedUser, org.springframework.http.HttpStatus.OK);
    }

    // Renews the session with the login refresh token (public: the 1-hour token may have expired)
    @PostMapping("/refresh")
    public ResponseEntity<LoggedUser> refresh(@RequestBody RefreshTokenDto dto) throws ApiException {
        return ResponseEntity.ok(userService.refresh(dto == null ? null : dto.refreshToken()));
    }

    // Closes the session: the refresh token stops working. 204 even if it no longer worked
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenDto dto) {
        userService.logout(dto == null ? null : dto.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<LoggedUser> Register(@RequestBody NewUserDto userData) throws ApiException {
        userService.createUser(userData.name(), userData.email(), userData.password(), Set.of("CLIENTE"));
        LoggedUser loggedUser = userService.login(userData.email(), userData.password());
        return new ResponseEntity<>(loggedUser, org.springframework.http.HttpStatus.CREATED);
    }

    // Delete your own account. 409 if it is the store's last ADMINISTRADOR
    @DeleteMapping("/me")
    public ResponseEntity<String> Delete(@AuthenticationPrincipal CustomUserDetails userDetails) throws ApiException {
        userService.deleteUserById(userDetails.getId());
        return new ResponseEntity<>(SuccessMessages.USER_DELETED, org.springframework.http.HttpStatus.OK);
    }

    // Change your own name or password (null ones do not change; the email cannot be edited)
    @PatchMapping("/me")
    public ResponseEntity<FullUserDto> Update(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody PatchUserDto userData) throws ApiException {
        FullUserDto updatedUser = userService.updateUser(userDetails.getId(), userData.name(), userData.password());
        return new ResponseEntity<>(updatedUser, org.springframework.http.HttpStatus.OK);
    }

    // Own profile photo: PNG, JPG, WEBP or GIF up to 1 MB. Replaces the previous one
    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    public ResponseEntity<FullUserDto> uploadAvatar(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                    @RequestParam("file") MultipartFile file) throws ApiException {
        return ResponseEntity.ok(userService.setAvatar(userDetails.getId(), file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<FullUserDto> deleteAvatar(@AuthenticationPrincipal CustomUserDetails userDetails) throws ApiException {
        return ResponseEntity.ok(userService.deleteAvatar(userDetails.getId()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Boolean> forgotPasswordRequest(@RequestBody EmailDto userData) throws ApiException {
        this.userService.forgotPasswordRequest(userData);
        return new ResponseEntity<>(true, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/forgot-password/{token}")
    public ResponseEntity<FullUserDto> forgotPassword(@PathVariable String token, @RequestBody PasswordDto userData) throws ApiException {
        FullUserDto user = this.userService.refreshPassword(token, userData.newPassword());
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }
}
