package com.decor.common.audit.testentities;

import com.decor.common.audit.AuditIgnore;
import com.decor.common.audit.Audited;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Audited("Thing")
public class AuditedThing {

    @Id
    @GeneratedValue
    public Long id;

    public String name;

    public BigDecimal price;

    public String password;

    @AuditIgnore
    public LocalDateTime lastLogin;

    public LocalDateTime deletedAt;
}
