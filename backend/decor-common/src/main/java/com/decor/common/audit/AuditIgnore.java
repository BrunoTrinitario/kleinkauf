package com.decor.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
Campo que no va a la auditoria: datos sensibles o que cambian solos (ej. el ultimo login).
Las contrasenas, tokens y secretos se saltean siempre, esten marcados o no (ver AuditValues).
Si en un cambio solo cambiaron campos ignorados, no se registra nada.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditIgnore {
}
