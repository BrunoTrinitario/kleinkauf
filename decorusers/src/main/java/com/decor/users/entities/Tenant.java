package com.decor.users.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "config_id", nullable = false, unique = true, length = 100)
    private String configId;

    @Column(name = "name", unique = true, nullable = false)
    private String name;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "db_name", nullable = false, length = 100)
    private String dbName;

    @Column(name = "db_user", nullable = false, length = 100)
    private String dbUser;

    @Column(name = "db_password", nullable = false, length = 255)
    private String dbPassword;

    @Column(name = "enable", nullable = false)
    private Boolean enable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "driver_class_name")
    private String driverClassName;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (enable == null) {
            enable = true;
        }
    }
}
