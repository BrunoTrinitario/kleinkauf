# Audit log

A record of who did what, when and what changed, in each store and on the platform, viewable in real time. It is
built on the [observability](observability.md) service.

In the frontend: `/admin/auditoria` for a store's admin and `/auditoria` for the superadmin (history with filters in
the URL, before and after of each change, new events live and CSV export).

## Decisions

| Topic | Decision |
|---|---|
| What is audited | **Data changes**, **platform actions** and **sensitive reads**. Logins are not |
| Who counts | Only what **a person** does: a request with a user or a public one (self-service sign-up). Internal calls between services (sales reserving stock in products) and work without a request (scheduled jobs, startup) are not recorded |
| Detail | Who, when, IP, action, entity and id, and **only the fields that changed, with old and new value**. Never passwords or tokens |
| How it is captured | A **Hibernate listener** for `@Audited` entities; by hand with `AuditLog` for everything else |
| How it travels | **Outbox**: the row is saved in the same transaction as the change and then published to Redis Streams. **Nothing is lost** even if observability or Redis are down |
| Outbox | **One table per service**, `public.outbox_events`, with a `tenant` column (not one per schema): it is written with the same connection as the change and the publisher reads a single table |
| Where it is stored | In **`kleinkauf-observability`**, `observability` database: `<schema>.audit_events`, one schema per store and `public` for the platform |
| Partitions | `audit_events` is **partitioned by month** (`occurred_at` in UTC): `audit_events_2026_11`, etc. A date query only reads those months and an old month can be archived without touching the rest |
| Retention | **Forever**. Nobody edits or deletes it |
| Who sees it | **Each their own**: the admin sees their store's and the superadmin the platform's. The superadmin does **not** see the stores' |
| Names | Observability looks up the user's name in users **when saving** and stores it: if the user is deleted later, the audit still says who it was |
| Deleted store | Its audit log is kept but cannot be queried from the app (its admin can no longer log in and the superadmin does not see stores). It stays in the database |
| Live | `store.audit` and `platform.audit` WebSocket channels |

## What is recorded

### Data changes (store), automatic

`@Audited` entities: every create (`CREATE`), update (`UPDATE`) and delete (`DELETE`).

| Service | Entities |
|---|---|
| products | `Product` (including price and cost), `Bundle`, `BundleProduct`, `ProductImage`, `ProductMetadata`, `StockMovement`, `StoreSettings` |
| users | `User`, `Role` (granting and removing roles), `RolePermission` (a role's permissions), `Tenant` |
| sales | `SalesSettings` |

- A soft delete (`deletedAt` going from empty to a date) is recorded as `DELETE`.
- Collections are not recorded: the child entity is audited instead (e.g. each `ProductImage`). A relation to another
  entity is stored as its id.
- If an update only changed ignored fields (`@AuditIgnore`, e.g. last login), nothing is recorded.

### By hand (`AuditLog`)

| Service | What | Action and entity |
|---|---|---|
| products | CSV import: **one** entry with the summary (created, updated), not one per product | `IMPORT` `Product` |
| sales | Order status changes by the admin (validate, reject, cancel…), with the reason | `STATUS_CHANGE` `Order` (number) |
| users | Deactivating and reactivating superadmins (done with plain SQL, not through Hibernate) | `DEACTIVATE` / `ACTIVATE` `Superadmin` |

Creating superadmins does go through Hibernate (`User` and `Role` in `public`).

### Platform actions

users' actions on stores (`Tenant`) and superadmins are stored with `tenant = "public"`. When a superadmin does them,
`actor.role` is `SUPERADMIN`. **Self-service** sign-up has no user: `actor` is `null` and the IP is kept. The owner
created with the store goes to that store's audit log (`User` `CREATE`).

Reading the platform panel (summary, usage per store, superadmin list) is **not** audited.

### Sensitive reads (`READ`)

| What | Entity |
|---|---|
| Viewing cost and margin (`GET /products/{id}/pricing`) | `ProductPricing` (product id) |
| Exporting the products CSV | `ProductExport` |
| Downloading a payment receipt | `PaymentReceipt` (receipt id) |

## Event format

```json
{
  "id": "0b6f…",
  "tenant": "my_store",
  "at": "2026-11-02T10:15:00Z",
  "service": "products",
  "actor": { "userId": 3, "name": "Ana", "role": "ADMINISTRADOR" },
  "ip": "190.1.2.3",
  "traceId": "4bf92f3577b34da6",
  "action": "UPDATE",
  "entity": "Product",
  "entityId": "12",
  "changes": {
    "price": { "from": "15000.00", "to": "18000.00" },
    "name": { "from": "Mesa", "to": "Mesa ratona" }
  }
}
```

- `action`: `CREATE`, `UPDATE`, `DELETE`, `READ`, `STATUS_CHANGE`, `IMPORT`, `ACTIVATE` or `DEACTIVATE`.
- `actor.role`: the user's highest role. `actor.name` is filled in by observability.
- `ip`: the connection's IP, which the gateway passes in `X-Client-Ip` (if the client sends that header, it is
  removed). It is not taken from `X-Forwarded-For`, which the client can make up.
- `traceId` links the audit entry to that request's trace and logs in Grafana.
- `at` always **with a zone** (UTC, `Z`), stored as `timestamptz`.
- On a create, `changes` has the fields with a value (`from` empty). On an update, only the ones that changed. Values
  are text, cut to 500 characters.
- Fields marked `@AuditIgnore` and fields with `password`, `token` or `secret` in their name are never included.

## How it works

<p align="center"><img src="../diagrams/audit-flow.svg" alt="Audit events: outbox, Redis Streams, consumer, audit_events and WebSocket"></p>

1. **Capture** (`kleinkauf-common`, `com.kleinkauf.common.audit`): `AuditHibernateListener` listens to `PostInsert`,
   `PostUpdate` and `PostDelete`. The "before" comes from the state Hibernate had loaded, and the changed fields from
   the ones Hibernate marked dirty. It inserts the row with the same session's connection: if the change is rolled
   back, so is the audit entry. `AuditLog` does the same by hand with `JdbcTemplate` (inside a transaction it joins
   it).
2. **Outbox**: `public.outbox_events` is created when the service starts. Sent rows are deleted after a week: the
   audit log lives in observability, the outbox is only a stopover.
3. **Publisher** (`OutboxPublisher`): takes pending rows with `FOR UPDATE SKIP LOCKED` (with several instances each
   one takes different rows), sends them to the `kleinkauf:audit` stream and marks them. If Redis fails, it marks
   nothing and retries.
4. **Consumer** (`AuditConsumer` in observability): consumer group `observability-audit`, so with several instances
   each event is stored by only one. It stores it **once** even if it arrives twice (by `id`) and only then
   acknowledges it (`XACK`): if the database fails, it stays pending and is retried. Pending events of an instance
   that died are claimed by another one after a minute. Then it is sent over the WebSocket.

The CSV import turns automatic capture off while it runs (`AuditSuppression`) and leaves the summary entry.

`kleinkauf.audit.enabled=false` turns auditing off for a service; `kleinkauf.audit.publisher.enabled=false` only the
publishing.

## Queries

| Method | Path | Who | What it does |
|---|---|---|---|
| GET | `/api/v1/observability/store/audit` | Store admin | Their store's history, paginated (`page`, `size` up to 100), with filters `from`, `to`, `userId`, `entity` and `action` |
| GET | `/api/v1/observability/store/audit/{entity}/{entityId}` | Store admin | History of a single thing (e.g. `Product/12`) |
| GET | `/api/v1/platform/observability/audit` | Superadmin | Platform history, paginated, with the same filters (plus `entityId`) |
| GET | `/api/v1/observability/store/audit/export` | Store admin | The history with the same filters, as **CSV** |
| GET | `/api/v1/platform/observability/audit/export` | Superadmin | The platform's, as CSV |

- `from` and `to` are dates with a zone (ISO-8601, e.g. `2026-11-02T03:00:00Z`); `to` is exclusive. A malformed date
  answers 400.
- Response: `{ "items": [ … ], "page": 0, "size": 20, "totalItems": 3, "totalPages": 1 }`, newest first.
- From the platform, the store routes answer 404; an admin asking for the platform's gets 403.
- **CSV**: UTF-8 with BOM (for Excel), columns `fecha,usuario,id_usuario,rol,accion,entidad,id_entidad,cambios,servicio,ip`,
  the date in Argentina time (`audit.export.zone`) and changes as `field: before -> after; ...`. Values starting with
  `=`, `+`, `-` or `@` get a leading `'` so Excel does not treat them as formulas. Up to **50,000** events: with more
  it answers 400 and the date range has to be narrowed (a truncated file is never sent).

### Partitions

- A month's partition is created before storing the first event of that month (`CREATE TABLE IF NOT EXISTS ...
  PARTITION OF`). There is no default partition: if it could not be created, the event is not acknowledged and is
  retried.
- The key is `(id, occurred_at)` (on a partitioned table it must include the date). A repeated event carries the same
  date, so it is still stored only once.
- Older, non-partitioned tables are converted automatically the first time observability uses that schema: in one
  transaction the old table is renamed, the partitioned one is created with one partition per month it had, the
  events are copied and the old table is dropped.

## Known limitations

- Actions recorded by hand outside a transaction (an order status change, deactivating a superadmin) are written
  right after the change, not in the same transaction: if the service dies between the two, that entry is lost.
- The `kleinkauf:audit` stream is trimmed to about 100,000 events: if observability were down for a long time with a
  lot of activity, the oldest would be lost (the outbox already marked them as sent).
- Changes made with plain SQL that do not go through `AuditLog` are not recorded.
- The export builds the CSV in memory (up to 50,000 events, about 10 MB).
