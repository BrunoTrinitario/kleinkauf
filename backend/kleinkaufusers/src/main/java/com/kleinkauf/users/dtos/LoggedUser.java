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
// token: el de sesion (1 hora). refreshToken: para renovarlo con POST /auth/refresh (7 dias)
public record LoggedUser(
    String name,
    Long id,
    String email,
    String token,
    String refreshToken,
    List<RoleDto> roles,
    // Profile photo: file under /users/avatars/, or null
    String avatar
) {
}
