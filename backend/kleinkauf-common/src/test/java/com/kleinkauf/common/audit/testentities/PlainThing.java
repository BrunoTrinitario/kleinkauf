package com.kleinkauf.common.audit.testentities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

// Sin @Audited: no tiene que aparecer en la auditoria
@Entity
public class PlainThing {

    @Id
    @GeneratedValue
    public Long id;

    public String name;
}
