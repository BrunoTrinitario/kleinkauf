package com.decor.common.audit;

import java.util.function.Supplier;

/*
Apaga el registro automatico de las entidades mientras corre una operacion masiva que se registra entera
con AuditLog (ej. la importacion de un CSV: una entrada con el resumen y no una por producto).
Lo que se registra a mano con AuditLog sale igual.
 */
public final class AuditSuppression {

    private static final ThreadLocal<Boolean> SUPPRESSED = ThreadLocal.withInitial(() -> false);

    private AuditSuppression() {
    }

    public static <T> T run(Supplier<T> operation) {
        boolean before = SUPPRESSED.get();
        SUPPRESSED.set(true);
        try {
            return operation.get();
        } finally {
            SUPPRESSED.set(before);
        }
    }

    public static void run(Runnable operation) {
        run(() -> {
            operation.run();
            return null;
        });
    }

    static boolean isSuppressed() {
        return SUPPRESSED.get();
    }
}
