package com.kleinkauf.common.audit;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
How values are written in the audit and which fields never go in.
 */
public final class AuditValues {

    static final int MAX_LENGTH = 500;

    // Name fragments that give away sensitive data: never stored, even if not marked with @AuditIgnore
    private static final Set<String> SENSITIVE = Set.of("password", "token", "secret");

    private static final Map<String, Boolean> IGNORED_FIELDS = new ConcurrentHashMap<>();

    private AuditValues() {
    }

    public static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = switch (value) {
            case BigDecimal decimal -> decimal.toPlainString();
            case Enum<?> e -> e.name();
            default -> value.toString();
        };
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH) + "…";
    }

    public static boolean ignored(Class<?> entity, String property) {
        String lower = property.toLowerCase(Locale.ROOT);
        if (SENSITIVE.stream().anyMatch(lower::contains)) {
            return true;
        }
        return IGNORED_FIELDS.computeIfAbsent(entity.getName() + "#" + property, key -> hasIgnore(entity, property));
    }

    private static boolean hasIgnore(Class<?> type, String property) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(property);
                return field.isAnnotationPresent(AuditIgnore.class);
            } catch (NoSuchFieldException ignored) {
                // May be in the superclass
            }
        }
        return false;
    }
}
