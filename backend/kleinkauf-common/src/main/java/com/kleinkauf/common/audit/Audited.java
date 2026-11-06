package com.kleinkauf.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
Audited entity (docs/features/audit.md): its creates, updates and deletes go to the audit, with the fields that
changed. value: the entity name in the audit (defaults to the class name).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String value() default "";
}
