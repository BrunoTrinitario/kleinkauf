# observability (kleinkauf-observability)

Spring Boot (servlet) on port **3004**, registered in Eureka as `observability-microservice`. Postgres database
`observability` (one schema per store plus `public`) with JDBC, no Hibernate.

Shared conventions: [README](README.md). Design and decisions: [observability.md](../features/observability.md) and
[audit.md](../features/audit.md).

## What it does

- **Platform health and traffic:** every 10 s it reads `/ready` and `/metrics` from every instance registered in
  Eureka and builds each service's status, traffic (requests, errors, latencies), resources and connected users.
- **Audit log:** stores the events sent by users, products and sales (who did what) and lets them be queried.
- **Real time over WebSocket** for the platform panel (superadmin) and each store's panel (admin): health, traffic,
  store sign-ups, live audit, store activity (orders and stock), catalog usage and connected users.

## Health and metrics

Every service exposes on its **management port** (service port + 6000), which the gateway does not publish:

| Path | What it answers | Used by |
|---|---|---|
| `/health` | Liveness: the process is alive | Docker (healthchecks) |
| `/ready` | Readiness: database, Redis, Eureka and whatever the service needs (not the other services) | observability |
| `/metrics` | Prometheus format, with the `tenant` tag on HTTP metrics | Prometheus and observability |
| `/status` | Health detail per component | By hand |

Ports: gateway 9000, users 9001, products 9002, sales 9003, observability 9004, eureka 9761.

## Platform panel

`SUPERADMIN` only, from the platform frontend (no store). With a store they answer **404**.

### POST /api/v1/platform/observability/ws-ticket

**Access:** SUPERADMIN

Ticket to open the WebSocket. It is issued with the **current** roles (asked to users at that moment).

```bash
curl -X POST http://localhost:3000/api/v1/platform/observability/ws-ticket \
  -H 'Origin: http://localhost:2999' -H 'Authorization: Bearer <token>'
```

Response **200**: `{ "ticket": "f3a9...", "expiresIn": 30 }`

| Status | When |
|---|---|
| 403 | No longer a superadmin (for example, deactivated) |
| 503 | users does not answer (`No se pudo verificar el acceso`) |

### GET /api/v1/platform/observability/overview

**Access:** SUPERADMIN

The collector's last read, to show something before the first WS message.

```json
{
  "at": "2026-11-03T13:00:10Z",
  "services": [
    { "name": "products-microservice", "status": "UP",
      "instances": [ { "id": "products-microservice:172.18.0.5:3002", "status": "UP",
                       "components": { "db": "UP", "redis": "UP", "discoveryComposite": "UP" }, "error": null } ],
      "openCircuitBreakers": [] }
  ],
  "traffic": [
    { "name": "products-microservice", "instances": 1, "requestsPerSecond": 2.4,
      "errors5xxPerSecond": 0.0, "errors4xxPerSecond": 0.1, "rateLimitedPerSecond": 0.0,
      "p50Ms": 12.3, "p95Ms": 48.0, "p99Ms": 95.5,
      "heapUsedBytes": 1.5E8, "heapMaxBytes": 4.0E9, "cpu": 0.03, "threads": 42.0,
      "dbConnectionsActive": 1.0, "dbConnectionsMax": 10.0 }
  ],
  "connectedUsers": { "total": 5, "visitors": 12,
                      "byTenant": [ { "tenant": "demo", "users": 4, "visitors": 12 }, { "tenant": "public", "users": 1, "visitors": 0 } ] }
}
```

- A service's `status`: `UP` (all instances ready), `DEGRADED` (some) or `DOWN` (none, or no instances in Eureka).
- Rates and percentiles are `null` until the second read (they come from subtracting two). Resources are the sum over
  instances (CPU is the average).
- Before the first read: `at: null` and empty lists.

### GET /api/v1/platform/observability/audit

**Access:** SUPERADMIN

The platform audit log (stores and superadmins), paginated, newest first. It does not include the stores' logs.

| Parameter | What it does |
|---|---|
| `from`, `to` | Date **with zone**, ISO-8601 (`2026-11-02T03:00:00Z`); `to` is exclusive. The frontend decides what "a day" is for the viewer |
| `userId` | Who did it |
| `entity`, `entityId` | Which thing (`Tenant`, `7`) |
| `action` | `CREATE`, `UPDATE`, `DELETE`, `READ`, `STATUS_CHANGE`, `IMPORT`, `ACTIVATE`, `DEACTIVATE` |
| `page`, `size` | 0 and 20 |

Response **200**: `{ "items": [AuditEvent], "page", "size", "totalItems", "totalPages" }` (see
[format](#audit-format)). Error: 400 if `from` or `to` are not dates with a zone.

### GET /api/v1/platform/observability/audit/export

**Access:** SUPERADMIN

The same audit log as **CSV** (`auditoria-plataforma-<date>.csv`), with the same filters minus `page` and `size`.
Format in [audit.md](../features/audit.md#queries). 400 if there are more than 50,000 events (narrow the dates).

## Store panel

The store's `ADMINISTRADOR` only. Without a store (from the platform) they answer **404**; the superadmin does not see
the stores' audit logs.

### POST /api/v1/observability/ws-ticket

**Access:** ADMINISTRADOR

Same as the platform one, for the store's WS. Response **200**: `{ "ticket": "...", "expiresIn": 30 }`. 403 if no
longer an admin, 503 if users does not answer.

### GET /api/v1/observability/store/audit

**Access:** ADMINISTRADOR

The store's audit log, paginated. Same parameters as the platform one except `entityId` (for a single thing, use the
endpoint below).

```bash
curl 'http://localhost:3000/api/v1/observability/store/audit?entity=Product&action=update&from=2026-11-01T03:00:00Z' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

### GET /api/v1/observability/store/audit/{entity}/{entityId}

**Access:** ADMINISTRADOR

History of one thing (`/store/audit/Product/12`, `/store/audit/Order/K7QM-4X2P`). Paginated.

### GET /api/v1/observability/store/audit/export

**Access:** ADMINISTRADOR

The store's audit log as **CSV** (`auditoria-<store>-<date>.csv`), with the same filters as `/store/audit` minus `page`
and `size`. 400 if there are more than 50,000 events.

```bash
curl -o audit.csv 'http://localhost:3000/api/v1/observability/store/audit/export?from=2026-11-01T03:00:00Z' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

### GET /api/v1/observability/store/users

**Access:** ADMINISTRADOR

The store's connected users (some request in the last 5 minutes), most recent first. It includes every logged-in user,
customers too. `name` is `null` if users did not answer. `visitors`: how many anonymous visitors there were in the same
5 minutes (only the number: the gateway marks them with a hash of IP and browser, without storing the IP; two people on
the same connection and browser count as one).

```json
{ "total": 2,
  "users": [ { "userId": 8, "name": "Beto", "lastActivity": "2026-11-03T13:00:05Z" },
             { "userId": 1, "name": "Dueño Demo", "lastActivity": "2026-11-03T12:58:40Z" } ],
  "visitors": 14 }
```

Error: 503 if Redis could not be read.

### GET /api/v1/observability/store/usage

**Access:** ADMINISTRADOR

The store's usage for the day.

```json
{
  "at": "2026-11-03T13:00:10Z",
  "date": "2026-11-03",
  "catalogVisits": 40,
  "topProducts": [ { "slug": "mesa-de-roble", "views": 12 }, { "slug": "silla-nordica", "views": 7 } ],
  "traffic": { "requestsPerSecond": 2.5, "errors4xxPerSecond": 0.1, "errors5xxPerSecond": 0.0 }
}
```

- `date`: the day in Buenos Aires. `catalogVisits`: times the first page of the catalog was opened without a search.
  `topProducts`: the 5 most viewed product pages, by slug.
- `traffic`: the store's requests and errors per second at the gateway, from the last read. `null` until the gateway's
  second read.

Error: 503 if Redis could not be read.

## WebSocket

### Client guide

1. **Ask for a ticket** with the JWT: `POST /api/v1/platform/observability/ws-ticket` (superadmin) or
   `POST /api/v1/observability/ws-ticket` (admin). It lasts **30 s** and works **only once**.
2. **Connect** to `ws://localhost:3000/ws?ticket=<ticket>` from the frontend (the browser sends `Origin`). The JWT
   never goes in the URL.
   - Missing, expired or used ticket: the handshake fails with **401**.
   - Ticket from another store (or a store ticket opened from the platform, and vice versa): **403**.
3. **Subscribe** to the channels the page shows. A platform ticket can only use `platform.*`; a store ticket only
   `store.*`, always for its own store.
4. **Ping every 30 s** to keep the connection alive.
5. **Load what came before over REST** (overview, audit log, users, usage): the WS only sends what is new since the
   subscription.
6. **If it drops**, ask for a new ticket and reconnect (with growing back-off). If the ticket request answers **403**,
   access was lost: do not retry.

| Close | Reason | What to do |
|---|---|---|
| 1008 `Acceso revocado` | Roles of connected users are checked every minute; the user lost the role | Do not reconnect; log out or go back home |
| 4500 (untrusted session) | The client did not read in time (more than 5 s or 512 KB pending) | Reconnect with a new ticket |
| Others | Network, service restart | Reconnect with a new ticket |

```js
async function connectWs(ticketUrl, token, channels, onEvent) {
  const res = await fetch(ticketUrl, { method: "POST", headers: { Authorization: `Bearer ${token}` } })
  if (res.status === 403) throw new Error("No access")
  const { ticket } = await res.json()

  const ws = new WebSocket(`ws://localhost:3000/ws?ticket=${encodeURIComponent(ticket)}`)
  let ping
  ws.onopen = () => {
    channels.forEach((channel) => ws.send(JSON.stringify({ type: "subscribe", channel })))
    ping = setInterval(() => ws.send(JSON.stringify({ type: "ping" })), 30_000)
  }
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.type === "event") onEvent(msg.channel, msg.data, msg.at)
    if (msg.type === "error") console.warn(msg.message)
  }
  ws.onclose = (e) => {
    clearInterval(ping)
    if (e.code !== 1008) setTimeout(() => connectWs(ticketUrl, token, channels, onEvent), 2_000)
  }
  return ws
}
```

The real client is `frontend/src/hooks/use-live-channels.ts`.

### Messages

From the client (JSON up to 1,024 characters):

| Message | Answer |
|---|---|
| `{ "type": "subscribe", "channel": "store.activity" }` | `{ "type": "subscribed", "channel": "store.activity" }` |
| `{ "type": "unsubscribe", "channel": "store.activity" }` | `{ "type": "unsubscribed", "channel": "store.activity" }` |
| `{ "type": "ping" }` | `{ "type": "pong" }` |

An error does not close the connection: `{ "type": "error", "message": "Canal no permitido: platform.health" }` (also
`JSON invalido`, `Mensaje demasiado largo`, `Tipo de mensaje desconocido: …`).

From the server, on each subscribed channel:

```json
{ "type": "event", "channel": "store.activity", "data": { ... }, "at": "2026-11-03T13:00:00Z" }
```

### Channels

| Channel | Who | When it sends | `data` |
|---|---|---|---|
| `platform.health` | Superadmin | When the state of a service, instance or circuit breaker changes | List of services (like the overview's `services`) |
| `platform.traffic` | Superadmin | On each read (10 s) | `{ at, traffic, connectedUsers }` (like the overview) |
| `platform.business` | Superadmin | A store is created, enabled, disabled or deleted | `{ type: "tenant.created", at, id, identifier, schema }` |
| `platform.audit` | Superadmin | Each platform audit event, as soon as it is stored | `AuditEvent` |
| `store.audit` | Admin | Each audit event of their store | `AuditEvent` |
| `store.activity` | Admin | Orders, receipts and stock, as they happen | See below |
| `store.usage` | Admin | On each read (10 s) | Like `GET /store/usage` |
| `store.users` | Admin | On each read (10 s) | Like `GET /store/users` |

`store.activity` sends `{ type, at, ...data }`:

| `type` | When | Data |
|---|---|---|
| `order.created` | New online order or in-store sale | `number`, `channel` (`ONLINE`/`LOCAL`), `status`, `total`, `buyerName` |
| `order.receipt-uploaded` | The buyer uploaded a receipt | Same |
| `stock.movement` | Stock movement entered by hand (not the ones from orders) | `productId`, `productName`, `movementType`, `quantity`, `stockAfter`, `reason`, `userId` |
| `stock.low` | A product drops to low stock (once, when crossing the minimum) | `productId`, `productName`, `stock`, `minStock` |

```json
{ "type": "event", "channel": "store.activity",
  "data": { "type": "order.created", "at": "2026-11-03T13:00:00Z", "number": "K7QM-4X2P",
            "channel": "ONLINE", "status": "PENDIENTE_PAGO", "total": 72000.0, "buyerName": "Ana Pérez" },
  "at": "2026-11-03T13:00:00.120Z" }
```

`store.activity` is live only: what happened while the panel was closed is in the audit log and the order list.

## Audit format

```json
{
  "id": "0b6f…",
  "tenant": "demo",
  "at": "2026-11-02T10:15:00Z",
  "service": "products",
  "actor": { "userId": 3, "name": "Ana", "role": "ADMINISTRADOR" },
  "ip": "190.1.2.3",
  "traceId": "4bf92f3577b34da6",
  "action": "UPDATE",
  "entity": "Product",
  "entityId": "12",
  "changes": { "price": { "from": "15000.00", "to": "18000.00" } }
}
```

- `action`: `CREATE`, `UPDATE`, `DELETE` (soft delete too), `READ` (sensitive reads), `STATUS_CHANGE` (orders),
  `IMPORT` (CSV), `ACTIVATE` / `DEACTIVATE` (superadmins).
- `actor`: `null` on self-service store sign-up. `role` is the highest role; `name` is filled in by observability when
  saving (it stays even if the user is deleted later).
- `changes`: on a create, the fields with a value; on an update, only the changed ones. Values as text (up to 500).
  Never passwords, tokens or ignored fields (such as last login).
- `traceId` links the event to the trace and logs in Grafana.

What each service records: [audit.md](../features/audit.md#what-is-recorded). It arrives through an **outbox**: nothing
is lost even if observability or Redis are down.

## How it communicates

- **Collector:** every `observability.poll.interval` (10 s) it reads `/ready` and `/metrics` from every Eureka
  instance that publishes `management.port`, all at once, with a 2 s timeout. Eureka is read separately
  (`observability.static-targets`). The state lives in memory.
- **Per-store traffic:** from the gateway's metrics (`observability.gateway-service`), by the `tenant` tag.
- **Redis:**

| Key / stream | Purpose |
|---|---|
| `ws-ticket:<id>` | WS tickets (30 s, read and deleted with `GETDEL`) |
| `activity:<tenant>:<userId>` | Each user's last request (written by the gateway). Connected users |
| `visitor:<tenant>:<id>` | Anonymous visitors (written by the gateway) |
| `usage:<tenant>:<day>` | Catalog usage (written by products) |
| `kleinkauf:events` | Service events (stores, orders, stock). Read without a consumer group: each instance sees all of them |
| `kleinkauf:audit` | Audit log. Consumed with a group: each event is stored once and acknowledged |

- **users:** current roles for tickets and the minute-by-minute check, and names for the audit log (10-minute cache).
  With circuit breaker and retries. If users does not answer, nobody is disconnected.
- **Database:** the audit log, one schema per store plus `public`, created with the first event.

## Docker stack and Grafana

```bash
docker compose -f backend/deploy/docker-compose.dev.yml -f backend/deploy/docker-compose.observability.yml up -d
```

The second file adds Prometheus, Grafana, Tempo, Loki and Alloy; without it everything works the same but without
that stack.

| What | Where |
|---|---|
| Grafana (no login in development) | http://localhost:3300 → **KleinKauf** folder, **KleinKauf - Servicios** dashboard |
| Prometheus | http://localhost:9090 (Status → Targets) |
| Traces | Grafana → Explore → Tempo |
| Logs | Grafana → Explore → Loki, e.g. `{service="product-service", level="ERROR"}` |

- With the stack, services log JSON and send traces to Alloy; without it, plain-text logs with
  `[service,traceId,spanId,tenant]`.
- From a log you can open its trace, and from a trace its logs.
- The stack's data has no volume. Details in [observability.md](../features/observability.md#how-to-use-it).

## Configuration

| Property | Development value | Purpose |
|---|---|---|
| `server.port` / `management.server.port` | `3004` / `9004` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/observability` | Audit log |
| `spring.data.redis.*` | `localhost:6379` | Tickets, activity, events |
| `users.url` | `http://users-microservice/` | Roles and names |
| `observability.poll.interval` / `poll.timeout` | `10s` / `2s` | Collector |
| `observability.services` | The 5 services | The ones that must be present (without instances they show as `DOWN`) |
| `observability.static-targets` | `eureka=http://localhost:9761` | Whatever does not register in Eureka |
| `observability.gateway-service` | `gateway-microservice` | Source of per-store traffic |
| `observability.ticket.ttl` | `30s` | Ticket lifetime |
| `observability.ws.revalidate-interval` | `60s` | Role check of connected users |
| `observability.scheduling.enabled`, `events.enabled`, `audit.enabled` | `true` | Turned off by the tests |
| `audit.export.zone` | `America/Argentina/Buenos_Aires` | Time zone of the dates in the audit CSV |

Without Docker it needs Postgres (`observability` database; with an old volume:
`docker exec postgres-dev psql -U postgres -c "CREATE DATABASE observability"`), Redis and Eureka.

## Known limitations

- The collector state lives in memory: with several instances each one has its own.
- "Connected" is approximate: someone who closed the browser shows up for up to 5 minutes.
- Catalog visits count any request to the first page (reloads, bots) and are kept for 8 days.
- The `tenant` tag on metrics handles hundreds of stores; with thousands, usage should be counted only here.
