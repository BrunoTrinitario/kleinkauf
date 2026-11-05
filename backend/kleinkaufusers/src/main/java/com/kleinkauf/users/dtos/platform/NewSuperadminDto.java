package com.kleinkauf.users.dtos.platform;

// The password is temporary: whoever creates the account hands it over, but it does not have to be changed on first login
public record NewSuperadminDto(
        String name,
        String email,
        String password
) {
}
