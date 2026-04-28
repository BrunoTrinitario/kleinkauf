/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */

package com.decor.users.dtos;

import java.util.List;

/**
 *
 * @author bruno.trinitario
 */
public record FullUserDto(
    Long id,
    String name,
    String email,
    List<String> roles
) {}
