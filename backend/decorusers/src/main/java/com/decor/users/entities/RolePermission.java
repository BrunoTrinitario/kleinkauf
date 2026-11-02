package com.decor.users.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
Permisos de un rol en la tienda (una fila por rol, en el schema de cada tenant).
permissions es una mascara de bits de PermissionArea. Si un rol no tiene fila usa los
permisos por defecto (ver RolePermissionService).
 */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String type;

    @Column(nullable = false)
    private int permissions;
}
