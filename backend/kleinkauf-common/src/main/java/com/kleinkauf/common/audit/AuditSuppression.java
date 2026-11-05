package com.kleinkauf.common.audit;

import java.util.function.Supplier;

/*
Turns off automatic entity recording while a bulk operation runs that is recorded as a whole
with AuditLog (e.g. a CSV import: one entry with the summary instead of one per product).
What is recorded manually with AuditLog still goes out.
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
