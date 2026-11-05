package com.kleinkauf.users.dtos;

// Name or password change (PATCH /auth/me and PATCH /users/{id}): null = not changed. The email cannot be edited
public record PatchUserDto(
    String name,
    String password
) {
}
