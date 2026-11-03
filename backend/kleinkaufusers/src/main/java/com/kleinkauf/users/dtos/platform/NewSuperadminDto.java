package com.kleinkauf.users.dtos.platform;

// La contrasena es temporal: se la pasa quien lo crea, pero no hay que cambiarla en el primer login
public record NewSuperadminDto(
        String name,
        String email,
        String password
) {
}
