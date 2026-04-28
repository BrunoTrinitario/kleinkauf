/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.decor.users.util;

import com.decor.users.dtos.FullUserDto;
import com.decor.users.entities.User;

/**
 *
 * @author bruno.trinitario
 */
public class Parser {
    public static FullUserDto fromUserEntityToFullDto(User user){
        return new FullUserDto(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRolesAsString()
        );
    }
}
