# Observability

How to know at any moment how the platform and each store are doing: service health, metrics, traces, logs and a
**real-time panel over WebSocket**, at **platform** level (superadmin) and **store** level (admin). The audit log uses
the same service and is described in [audit.md](audit.md).

In the frontend: `/estado` and `/auditoria` on the platform, `/admin/actividad` and `/admin/auditoria` in each store.

## Decisions

| Topic | Decision |
|---|---|
| Health and metrics | **Spring Boot Actuator** in every service, configured in `kleinkauf-common`: `/health`, `/ready` and `/metrics` |
| Access | An **internal management port** per service, not exposed by the gateway. Read by Docker, Prometheus and observability |
| Visualization | **Our own panel** in the app (real time over WS) **+ Prometheus and Grafana** in Docker for the technical side |
| Service | **`kleinkauf-observability` microservice**: gathers health, metrics and events, stores the audit log and pushes everything over WS |
| Traces and logs | **OpenTelemetry + Tempo + Loki**: each request carries a `traceId` end to end and logs are JSON with `traceId` and `tenant`. **Grafana Alloy** is the collector (traces over OTLP and container logs) |
| Collection | **Events + polling**: services publish events to Redis Streams; health and technical metrics are polled every 10 s |
| Protocol | **Plain WebSocket** with our own JSON messages (no STOMP) |
| WS authentication | **Short-lived ticket over REST**: single use, 30 s. The JWT never goes in the URL |
| Revoked access | Every minute the roles of connected users are checked again and the **WS is closed** for anyone who lost the role |
| Per-store metrics | HTTP metrics carry a **`tenant`** tag |
| Connected users | By **last activity**: connected = some request in the last 5 minutes |

## /health, /ready and /metrics

| Path | What it answers | Purpose |
|---|---|---|
| `/health` | **Liveness**: the process is alive and not stuck | Docker / the orchestrator restarts the service if it fails |
| `/ready` | **Readiness**: it can serve (database, Redis, Eureka and whatever each service needs) | Do not send traffic until it is ready |
| `/metrics` | Metrics in **Prometheus** format | Prometheus and observability |
| `/status` | Full health detail, component by component | Checking by hand what is failing |

- Served on the **management port**: the service port + 6000 → users 9001, products 9002, sales 9003, observability
  9004, gateway 9000, eureka 9761. Each service sets it in its `application.properties`; otherwise `kleinkauf-common`
  computes it. The port is published in Eureka metadata (`management.port`), which is how Prometheus and
  observability find it.
- The shared configuration lives in `kleinkauf-common` (`ObservabilityEnvironmentPostProcessor`): they are defaults and
  whatever a service defines wins. `/health` includes only `livenessState` and `ping`; `/ready` includes every
  indicator of the service (database, Redis, Eureka) except `livenessState`.
- `/ready` does **not** look at the other microservices: if users goes down, products stays ready (its calls have a
  circuit breaker). In users, mail and RabbitMQ are left out.
- The gateway is reactive: its filters (JWT, tenant, rate limit) also run on the management port, so they let it
  through (`ManagementRequests`) and it has its own security chain without login.

## What can be seen

### Platform (superadmin)

| Section | Content |
|---|---|
| Service status | Each service up, degraded or down; its Eureka instances with the `/ready` result per component; open circuit breakers |
| Traffic and errors | Requests per second, p50 / p95 / p99 latency, 5xx and 4xx errors, rate limit 429s, per service. Per endpoint: in Grafana |
| Resources | Heap, CPU, threads and database connections in use, per service |
| Connected users | Total and per store, logged-in users and anonymous visitors |
| Business | Tenants by state, sign-ups and usage per store: REST in users and products ([admin-dashboards.md](admin-dashboards.md)); `platform.business` signals when they change |
| Audit | Who did what on the platform (`platform.audit` and its history) |

Observability does **not** recompute what the platform panel already provides (`GET /api/v1/platform/admin/dashboard`,
`/admin/usage` and `/platform/products/usage`): it adds health, traffic and real time. The superadmin does not see
orders or revenue across stores.

### Store (admin)

| Section | Content |
|---|---|
| Live audit | Who did what in their store (see [audit.md](audit.md)) |
| Sales | **Revenue** and **order history**: REST in [sales](sales.md) (`/api/v1/sales/admin/**`) |
| Live activity | New orders and in-store sales, uploaded receipts, manual stock movements and products dropping to low stock, as they happen (`store.activity`) |
| Store usage | Catalog visits and most viewed products of the day; requests and errors per second for the store (`store.usage`) |
| Connected users | Users with activity in the last 5 minutes (every logged-in user, customers too) with their last activity, plus how many anonymous visitors (`store.users`) |

#### Store activity (`store.activity`)

Events from `kleinkauf:events` for that store, forwarded as they are: `{ "type", "at", ...data }`.

| `type` | Published by | When | Data |
|---|---|---|---|
| `order.created` | sales | Online order created or in-store sale recorded | `number`, `channel` (`ONLINE`/`LOCAL`), `status`, `total`, `buyerName` |
| `order.receipt-uploaded` | sales | The buyer uploaded a receipt (the order goes to review) | Same as `order.created` |
| `stock.movement` | products | Movement recorded by hand from the panel (not the ones from orders: the order event already arrives) | `productId`, `productName`, `movementType`, `quantity`, `stockAfter`, `reason`, `userId` |
| `stock.low` | products | A product with a minimum **crosses** into low stock with any movement, including orders (not repeated while it stays low) | `productId`, `productName`, `stock`, `minStock` |

They are published after commit (`KleinKaufEventPublisher`); if Redis fails there is no event and the operation goes
on. They are live only: what happened while the panel was closed is in the audit log and the orders.

#### Usage and connected users (`store.usage` and `store.users`)

- On each collector read (10 s), only for stores that have some WS subscribed.
- **Traffic**: requests, 4xx and 5xx per second for the store, from the **gateway** metrics with the `tenant` tag
  (every store request goes through it once; counting in the services would count it again). The service name comes
  from `observability.gateway-service`. Until the gateway's second read, `traffic` is `null`.
- **Catalog**: products counts in Redis, per store and per day (`usage:<tenant>:<yyyy-MM-dd>`, `UsageKeys` in
  `kleinkauf-common`), **visits** (the first page of the listing without a search) and **views** of each product's
  page (by slug). The top 5 are sent. They expire after 8 days. The day is Buenos Aires' for every store.
- **Connected**: `SCAN` of `activity:<tenant>:*` with the time of the last request and the name (from users, cached).
- **Visitors**: the gateway marks anonymous visitors of a store (`visitor:<tenant>:<id>` in Redis, 5 minutes; the id
  is a hash of IP and browser, the IP itself is not stored).

```json
{ "at": "…", "date": "2026-11-03", "catalogVisits": 40,
  "topProducts": [ { "slug": "mesa-de-roble", "views": 12 } ],
  "traffic": { "requestsPerSecond": 2.5, "errors4xxPerSecond": 0.1, "errors5xxPerSecond": 0.0 } }
```

```json
{ "total": 2, "users": [ { "userId": 8, "name": "Beto", "lastActivity": "2026-11-03T13:00:05Z" } ], "visitors": 14 }
```

## Components

### kleinkauf-common

- Actuator configuration (paths, management port, health groups).
- Micrometer Tracing with OpenTelemetry and `traceId` propagation in `RestTemplate` / `WebClient`. OTLP trace export
  is **off by default** (`management.otlp.tracing.export.enabled`) and the Docker stack turns it on.
- Logs with `traceId`, `spanId`, `tenant` and `userId` (MDC from the `TenantContext`): plain text by default and JSON
  (logstash) with `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`.
- `tenant` tag on HTTP metrics (`TenantTag`: only schema-shaped values; anything else becomes `invalid`).
- `KleinKaufEventPublisher`: publishes events to the Redis stream `kleinkauf:events` (`type`, `tenant`, `at` in UTC and
  `data` as JSON), trimmed to about 10,000. Inside a transaction it publishes after commit. If Redis fails it is
  logged and the operation goes on: these events are only for the live panel. The audit log, which must not be lost,
  goes separately through an **outbox** table (`com.kleinkauf.common.audit`, see [audit.md](audit.md)).
- Events published today: users, when a store is created, enabled, disabled or deleted (`tenant.created`,
  `tenant.enabled`, `tenant.disabled`, `tenant.deleted`); sales, orders (`order.created`, `order.receipt-uploaded`);
  products, stock (`stock.movement`, `stock.low`).
- `UsageKeys`: format of the catalog usage counters, written by products and read by observability.
- `ActivityKeys`: format of the last-activity keys, written by the gateway and read by observability.

### kleinkauf-observability

- Its own Gradle project with `kleinkauf-common`, registered in Eureka, port 3004 (management 9004). `observability`
  database for the audit log, with JDBC and no Hibernate: that way the multi-tenancy and audit capture from
  `kleinkauf-common`, meant for the services that send events, are not activated.
- **Collector** (`collector/`): every 10 s (`observability.poll.interval`) reads `/ready` and `/metrics` from every
  Eureka instance that publishes `management.port`, all at once, with a 2 s timeout each. Eureka itself is read
  separately (`observability.static-targets`). From `/metrics` it takes requests, 5xx, 4xx, 429 and the latency
  histogram (rates and percentiles come from subtracting two reads), heap, CPU, threads, database connections and open
  circuit breakers. It does not count management port or WS requests. The state lives in memory.
  - A service is `UP` if all its instances are ready, `DEGRADED` if some are and `DOWN` if none. Services listed in
    `observability.services` with no instance in Eureka show as `DOWN`.
  - Connected users: walks the `activity:*` keys with `SCAN`.
- **Events** (`events/`): reads the `kleinkauf:events` stream **without a consumer group**, because each instance has
  its own WS and must see every event. It starts with the events arriving after startup. `tenant.*` goes to
  `platform.business`; `order.*` and `stock.*` to that store's `store.activity`.
- **Store** (`store/`): builds `store.usage` and `store.users` on each collector read (`StoreFeeds`) and answers their
  GETs.
- **Audit** (`audit/`): consumes the `kleinkauf:audit` stream with a consumer group, stores it and sends it over WS.
  Details in [audit.md](audit.md).
- **WebSocket** (`ws/`) at `/ws`, plus REST for what happened before the panel was opened. The WS only sends what is
  new.

### gateway

- `/ws` route to observability (`lb:ws://`), plus `/api/v1/platform/observability/**` and `/api/v1/observability/**`.
  `/ws` does not require a JWT (it uses the ticket) and from the platform frontend it does not look up a store.
- `ActivityFilter` marks in Redis the **last activity** of each logged-in user (`activity:<tenant>:<userId>`, 5 min
  TTL; `public` for superadmins), at most once every 30 s per user and without holding the request.
- `X-Client-Ip`: the connection's IP, for the audit log (the one sent by the client is removed).
- In metrics, the WS uses `uri="/ws"` so its long connections do not mix with latencies.

## Endpoints

All through the gateway. Platform routes have no store (from a store they answer 404) and store routes cannot be
requested from the platform either (404). Full reference in [backend/observability.md](../backend/observability.md).

| Method | Path | Who | What it does |
|---|---|---|---|
| POST | `/api/v1/platform/observability/ws-ticket` | Superadmin | Ticket for the platform WS |
| GET | `/api/v1/platform/observability/overview` | Superadmin | Last collector read: health, traffic, resources and connected users |
| GET | `/api/v1/platform/observability/audit` | Superadmin | Platform audit history ([audit.md](audit.md)) |
| POST | `/api/v1/observability/ws-ticket` | Store admin | Ticket for their store's WS |
| GET | `/api/v1/observability/store/audit` | Store admin | Their store's audit history |
| GET | `/api/v1/observability/store/audit/{entity}/{entityId}` | Store admin | History of a single thing |
| GET | `/api/v1/observability/store/users` | Store admin | Connected users (same as `store.users`) |
| GET | `/api/v1/observability/store/usage` | Store admin | Today's catalog usage and traffic (same as `store.usage`) |
| GET | `/ws?ticket=…` | With a ticket | WebSocket |

## WebSocket

1. The frontend asks for a ticket with its JWT (`POST …/ws-ticket` above) → `{ "ticket": "…", "expiresIn": 30 }`. It
   is issued with the **current** roles (observability asks users at that moment). It is stored in Redis
   (`ws-ticket:<id>`) with the user and tenant, lasts **30 s** and works **only once** (read and deleted with
   `GETDEL`).
2. It opens `ws://<gateway>/ws?ticket=…`. The gateway recognizes the tenant by `Origin`; observability validates the
   ticket (missing, expired or used: 401) and that its tenant is the connection's (otherwise: 403).
   - **Superadmin**: the platform frontend (`platform.url` in the gateway) has no store, so the gateway accepts `/ws`
     from that `Origin` without looking up a tenant and the connection belongs to `public`.
3. It subscribes to channels with JSON messages:

```json
{ "type": "subscribe", "channel": "platform.health" }
```

```json
{ "type": "event", "channel": "platform.health", "data": [ … ], "at": "2026-11-02T10:00:00Z" }
```

| Channel | Who | What it sends |
|---|---|---|
| `platform.health` | Superadmin | Health of every service, whenever some state changes |
| `platform.traffic` | Superadmin | Traffic, resources and connected users, on each read (10 s) |
| `platform.business` | Superadmin | Store sign-ups, deletions and changes (`tenant.*`) |
| `platform.audit` | Superadmin | Each platform audit event, as soon as it is stored |
| `store.audit` | Store admin | Each audit event of their store, as soon as it is stored |
| `store.activity` | Store admin | Orders, in-store sales, receipts and stock, as they happen |
| `store.usage` | Store admin | Catalog visits, most viewed products, requests and errors, on each read (10 s) |
| `store.users` | Store admin | Connected users, on each read (10 s) |

Client messages: `subscribe` and `unsubscribe` (with `channel`) and `ping`. The server answers `subscribed`,
`unsubscribed`, `pong` or `{ "type": "error", "message": … }` without closing the connection.

- A platform ticket can only subscribe to `platform.*` and a store ticket only to `store.*`. `store.*` channels always
  belong to **the ticket's store**: another store's channel cannot be requested.
- Ping every 30 s; if the connection drops, the frontend (`hooks/use-live-channels.ts`) asks for a new ticket and
  reconnects with growing back-off, and stops retrying if access was lost.
- A client that does not read (more than 5 s or 512 KB pending) is disconnected.
- **Revoked access**: whoever lost the role (a deactivated superadmin, for example) cannot get new tickets. And every
  minute observability fetches the roles of connected users again and **closes the WS** for anyone who no longer has
  it (code 1008), the same minute their REST access is cut. If users does not answer, nobody is disconnected.

## How to use it

### With Docker

```bash
docker compose -f backend/deploy/docker-compose.dev.yml -f backend/deploy/docker-compose.observability.yml up -d
```

The first file starts the services (observability included); the second adds Prometheus, Grafana, Tempo, Loki and
Alloy. With only the first one everything works the same, just without that stack.

| What | Where |
|---|---|
| Grafana (no login in development) | http://localhost:3300 → **KleinKauf** folder, **KleinKauf - Servicios** dashboard |
| Prometheus | http://localhost:9090 (Status → Targets lists each instance) |
| Traces | Grafana → Explore → Tempo |
| Logs | Grafana → Explore → Loki, e.g. `{service="product-service", level="ERROR"}` or `{service=~".+"} \| tenant="demo"` |

- **What changes in the services with the stack up**: the overlay sets `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`
  (JSON logs) and turns on trace export to Alloy (`MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED`). Without the stack, logs
  are plain text with `[service,traceId,spanId,tenant]` and traces are not sent, so there are no connection errors.
- **Prometheus** finds instances through Eureka (with their IP and the `management.port` from metadata), so a new
  instance shows up on its own. Eureka is scraped separately because it does not register itself.
- **Alloy** receives traces over OTLP and forwards them to Tempo, and reads the service containers' logs through the
  Docker socket. In Loki the level is a label; `traceId`, `tenant` and `userId` go as *structured metadata* (they do
  not create a series per value).
- Logs and traces are linked in Grafana: from a log you can open its trace and from a trace, its logs.
- The **KleinKauf - Servicios** dashboard shows services up or down, requests per second, p95 latency, 5xx and 4xx
  errors (429 is the rate limit), requests per store at the gateway, slowest endpoints, heap, CPU and database
  connections. It does not count management port requests.
- The development compose healthchecks use `/health` on the management port.
- Trace sampling: 100% (`management.tracing.sampling.probability`, can be lowered per service).
- The stack's data has no volume: it is lost when the containers are removed. Prometheus keeps 3 days and Tempo 72 h.
- The Docker VM needs memory: with 4 GB, the services compiling with `bootRun` at the same time do not fit. Give it
  6 GB or more (`memory=` in `.wslconfig` on Windows) or start them one by one the first time.

### Without Docker (bootRun)

observability needs Postgres (`observability` database), Redis and Eureka, like the others. `init.sql` creates the
database; with an older Postgres volume it has to be created by hand (see
[getting-started.md](../getting-started.md#1-database-and-redis)).

## Known limitations

- The `tenant` tag on metrics is fine with hundreds of stores; with thousands, per-store usage should be counted only
  in observability.
- "Connected" is an approximation: someone who closed the browser shows up for up to 5 minutes.
- Catalog visits are requests to the first page of the listing: a bot or a page reload also count. There is no usage
  history beyond 8 days.
- The collector state lives in memory: with several observability instances each one has its own (they read the
  same data, so they only differ by a few seconds).
- `kleinkauf:events` events (not the audit log) are lost if Redis is down when they are published. This only affects
  the live panel.
- Gateway logs carry `traceId` but not `tenant` or `userId` (it is reactive and the MDC is not built per request); the
  gateway's tenant is in the metrics.
- Trace sampling, Grafana alerts and data retention outside development are left as they are for now.
