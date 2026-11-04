package com.kleinkauf.users.entities;

import com.kleinkauf.common.audit.Audited;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
A role's permissions in the store (one row per role, in each tenant's schema).
permissions is a PermissionArea bit mask. If a role has no row it uses the
default permissions (see RolePermissionService).
 */
@Entity
@Audited
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
