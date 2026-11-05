package com.kleinkauf.common.audit.testentities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

// Without @Audited: must not show up in the audit
@Entity
public class PlainThing {

    @Id
    @GeneratedValue
    public Long id;

    public String name;
}
