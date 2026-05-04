package com.decor.users.controllers;

import com.decor.users.dtos.FullUserDto;
import com.decor.users.exceptions.ApiException;
import com.decor.users.services.UserService;
import com.decor.users.util.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/internal/users")
public class MSUserController {

    @Autowired
    UserService userService;

    @Autowired
    CacheUtil cacheUtil;

    @GetMapping("/all")
    public ResponseEntity<ArrayList<FullUserDto>> getAllUsers() {
        ArrayList<FullUserDto> users = userService.getAllUsers();
        return new ResponseEntity<>(users, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FullUserDto> getUserById(@PathVariable Long id) throws ApiException {
        FullUserDto user = userService.getUserById(id);
        return new ResponseEntity<>(user, org.springframework.http.HttpStatus.OK);
    }

}
