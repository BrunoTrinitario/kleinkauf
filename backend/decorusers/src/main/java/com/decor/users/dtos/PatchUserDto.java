package com.decor.users.dtos;

// Cambio de nombre o contrasena (PATCH /auth/me y PATCH /users/{id}): null = no se cambia. El email no se edita
public record PatchUserDto(
    String name,
    String password
) {
}
