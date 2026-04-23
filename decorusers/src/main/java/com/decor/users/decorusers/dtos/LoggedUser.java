/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */

package com.decor.users.decorusers.dtos;

/**
 *
 * @author bruno.trinitario
 */
public record LoggedUser(
    String name,
    Long id,
    String email,
    String token
) {
}
