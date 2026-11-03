package com.kleinkauf.users.dtos;

public record NewUserDto(
        String name,
        String email,
        String password
) {
}
