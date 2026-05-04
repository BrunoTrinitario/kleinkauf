package com.decor.users.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Tenant {
    @Id
    @GeneratedValue
    private UUID id;

    private String name, dbName, dbUser, dbPassword, configId;

    @Column(unique = true)
    private String url;

    private LocalDateTime created_at, deleted_at;

    private boolean enable;
}
