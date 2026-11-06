package com.kleinkauf.common.audit;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/*
Records creates, updates and deletes of @Audited entities (docs/features/audit.md).
- Runs on flush, with the Hibernate session: the outbox row is inserted with the same connection, in the
  same transaction as the change. If the change is rolled back, so is the audit.
- The "before" comes from the state Hibernate had loaded (oldState), and the changed fields from the ones
  Hibernate marked as dirty.
- A soft delete (deletedAt from null to a date) is recorded as DELETE.
- Collections are not recorded (the child entity is audited instead, e.g. a product's images). A relation
  to another entity is stored as its id.
 */
public class AuditHibernateListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    static final String SOFT_DELETE_PROPERTY = "deletedAt";

    private final AuditEvents events;
    private final OutboxStore outbox;
    private final Map<Class<?>, String> entityNames = new ConcurrentHashMap<>();

    public AuditHibernateListener(AuditEvents events, OutboxStore outbox) {
        this.events = events;
        this.outbox = outbox;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        String entity = entityName(event.getPersister());
        if (entity == null) return;
        Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
        EntityPersister persister = event.getPersister();
        Object[] state = event.getState();
        for (int i = 0; i < state.length; i++) {
            if (skip(persister, i)) continue;
            String to = value(persister.getPropertyTypes()[i], state[i], event.getSession());
            if (to != null) changes.put(persister.getPropertyNames()[i], new AuditEvent.Change(null, to));
        }
        write(event.getSession(), AuditEvent.CREATE, entity, event.getId(), changes);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        String entity = entityName(event.getPersister());
        if (entity == null) return;
        EntityPersister persister = event.getPersister();
        Object[] state = event.getState();
        Object[] old = event.getOldState();
        Map<String, AuditEvent.Change> changes = new LinkedHashMap<>();
        String action = AuditEvent.UPDATE;
        for (int i = 0; i < state.length; i++) {
            if (!dirty(event, i) || skip(persister, i)) continue;
            Type type = persister.getPropertyTypes()[i];
            String from = old == null ? null : value(type, old[i], event.getSession());
            String to = value(type, state[i], event.getSession());
            if (Objects.equals(from, to) && old != null) continue;
            String name = persister.getPropertyNames()[i];
            changes.put(name, new AuditEvent.Change(from, to));
            if (SOFT_DELETE_PROPERTY.equals(name) && old != null && old[i] == null && state[i] != null) {
                action = AuditEvent.DELETE;
            }
        }
        // Only ignored fields changed (e.g. the last login): nothing to record
        if (changes.isEmpty()) return;
        write(event.getSession(), action, entity, event.getId(), changes);
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        String entity = entityName(event.getPersister());
        if (entity == null) return;
        write(event.getSession(), AuditEvent.DELETE, entity, event.getId(), Map.of());
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void write(EventSource session, String action, String entity, Object id, Map<String, AuditEvent.Change> changes) {
        if (AuditSuppression.isSuppressed()) return;
        AuditEvent event = events.build(action, entity, id == null ? null : String.valueOf(id), changes);
        if (event == null) return;
        session.doWork(connection -> outbox.insert(connection, event));
    }

    // null if the entity is not @Audited
    private String entityName(EntityPersister persister) {
        Class<?> type = persister.getMappedClass();
        String name = entityNames.computeIfAbsent(type, c -> {
            Audited audited = c.getAnnotation(Audited.class);
            if (audited == null) return "";
            return audited.value().isBlank() ? c.getSimpleName() : audited.value();
        });
        return name.isEmpty() ? null : name;
    }

    private static boolean dirty(PostUpdateEvent event, int index) {
        int[] dirty = event.getDirtyProperties();
        if (dirty == null) return true;
        for (int d : dirty) {
            if (d == index) return true;
        }
        return false;
    }

    private static boolean skip(EntityPersister persister, int index) {
        return persister.getPropertyTypes()[index].isCollectionType()
                || AuditValues.ignored(persister.getMappedClass(), persister.getPropertyNames()[index]);
    }

    private static String value(Type type, Object value, SharedSessionContractImplementor session) {
        if (value == null) return null;
        if (type.isEntityType()) {
            try {
                Object id = session.getContextEntityIdentifier(value);
                if (id == null) id = session.getEntityPersister(null, value).getIdentifier(value, session);
                return AuditValues.text(id);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return AuditValues.text(value);
    }
}
