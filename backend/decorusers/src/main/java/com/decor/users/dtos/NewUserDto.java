package com.decor.users.dtos;

public record NewUserDto(
        String name,
        String email,
        String password
) {
}
