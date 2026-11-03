/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */

package com.kleinkauf.users.dtos;

import com.kleinkauf.common.dto.RoleDto;

import java.util.List;

/**
 *
 * @author bruno.trinitario
 */
public record LoggedUser(
    String name,
    Long id,
    String email,
    String token,
    List<RoleDto> roles
) {
}
