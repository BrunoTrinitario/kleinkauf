package com.kleinkauf.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
Field that is not audited: sensitive data or values that change on their own (e.g. the last login).
Passwords, tokens and secrets are always skipped, marked or not (see AuditValues).
If only ignored fields changed in an update, nothing is recorded.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditIgnore {
}
