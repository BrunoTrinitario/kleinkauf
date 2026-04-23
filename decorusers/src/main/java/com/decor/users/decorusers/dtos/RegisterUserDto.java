/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */

package com.decor.users.decorusers.dtos;

import java.util.Set;

/**
 *
 * @author bruno.trinitario
 */
public record RegisterUserDto(
        String name,
        String email,
        String password,
        Set<String> roles
) {

}
