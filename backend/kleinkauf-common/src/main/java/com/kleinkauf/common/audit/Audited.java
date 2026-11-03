package com.kleinkauf.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
Entidad auditada (docs/auditoria.md): sus altas, cambios y bajas quedan en la auditoria, con los campos que
cambiaron. value: el nombre de la entidad en la auditoria (por defecto, el de la clase).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String value() default "";
}
