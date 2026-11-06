# KleinKauf backend

Backend reference for integrating the frontend or starting work on a service: what each microservice does, every
endpoint with examples, and how they talk to each other. One file per service:

| Service | Doc | Port | What it does |
|---|---|---|---|
| gateway | [gateway.md](gateway.md) | 3000 | Single entry point: routing, store recognition, login validation, rate limit and CORS |
| users | [users.md](users.md) | 3001 | Login, users, roles and permissions of each store; store sign-up and platform panel |
| products | [products.md](products.md) | 3002 | Products, stock, bundles, CSV, public catalog and store panel |
| sales | [sales.md](sales.md) | 3003 | Online orders, bank transfer payments, in-store sales and reports |
| observability | [observability.md](observability.md) | 3004 | Health, metrics, audit log and the WebSocket for the real-time panels |
| kleinkauf-common | [common.md](common.md) | — | Shared library (not a service) |
| eureka | [eureka.md](eureka.md) | 8761 | Service registry |

The feature docs explain things that cross several services and are linked from here when needed:
[tenants](../features/tenants.md), [permissions](../features/permissions.md), [sales](../features/sales.md),
[admin dashboards](../features/admin-dashboards.md), [observability](../features/observability.md) and
[audit](../features/audit.md). How to run everything: [getting started](../getting-started.md).

## Architecture

<p align="center"><img src="../diagrams/backend-overview.svg" alt="Backend services and how they talk to each other"></p>

- **Multi-tenancy:** each store (tenant) has its own Postgres schema in every database. The gateway recognizes the
  store by the `Origin` header and passes it to the services. See [tenants.md](../features/tenants.md).
- **Platform:** whatever does not belong to a store (store sign-up, superadmins, platform panel) lives in the `public`
  schema and under `/api/v1/platform/**`.
- **Between services:** synchronous HTTP through Eureka with an internal token, protected by Resilience4j (circuit
  breaker, retries and timeout). Events for the live panels and the audit log go through Redis Streams.
- **Cache:** Caffeine (local) + Redis (shared) for tenants, roles and catalog.

## Route map

Everything is called on the gateway (`http://localhost:3000` in development). Each route is detailed in its service's
doc.

| Prefix | Service | Store | Doc |
|---|---|---|---|
| `/api/v1/auth/**` | users | Yes | [users.md](users.md#authentication) |
| `/api/v1/users/**` | users | Yes | [users.md](users.md#store-users) |
| `/api/v1/bug-reports` | users | Yes | [users.md](users.md#bug-reports) |
| `/users/avatars/**` | users | No | [users.md](users.md#authentication) |
| `/api/v1/platform/**` (except below) | users | No | [users.md](users.md#platform) |
| `/api/v1/products/**`, `/api/v1/bundles/**`, `/api/v1/catalog/**`, `/api/v1/store/**` | products | Yes | [products.md](products.md) |
| `/products/images/**`, `/share/**` | products | No | [products.md](products.md#images) |
| `/api/v1/platform/products/**` | products | No | [products.md](products.md#platform) |
| `/api/v1/sales/**` | sales | Yes | [sales.md](sales.md) |
| `/api/v1/observability/**` | observability | Yes | [observability.md](observability.md#store-panel) |
| `/api/v1/platform/observability/**` | observability | No | [observability.md](observability.md#platform-panel) |
| `/ws` | observability | Depends on the ticket | [observability.md](observability.md#websocket) |
| `/api/internal/**` | users, products | — | Service to service only (the "Internal" section of each doc) |

"Store: Yes" means the request must come from a store's frontend (its `Origin`); "No" means it belongs to the platform
or is public without a store.

## Conventions

### Calling the API

| Header | When | Example |
|---|---|---|
| `Origin` | Always on store routes: the gateway recognizes the store by it. Browsers send it on their own; with curl or Postman it must be set | `Origin: http://demo.localhost:2999` |
| `Authorization` | Routes that need login | `Authorization: Bearer <token>` |
| `Content-Type` | JSON bodies / files | `application/json` or `multipart/form-data` |

The access token comes from login (`POST /api/v1/auth/login` or `/api/v1/platform/auth/login`), lasts **1 hour** and is
bound to the store where the user logged in: a token from one store cannot call another (401). Login also returns a
**refresh token** (7 days) to renew the session with `POST /api/v1/auth/refresh`; see [users.md](users.md#authentication).

The `X-User-Id`, `X-Forwarded-Host`, `X-Client-Ip` and internal headers are set by the gateway; if the client sends
them, they are removed.

### Access

On each endpoint's card, **Access** is one of:

| Value | Meaning |
|---|---|
| Public | No token |
| Public (email) | No token; requires the order's email (sales). If it does not match, 404 |
| Login | Any user logged into the store |
| `PRODUCTOS`, `PRECIOS`, `STOCK`, `COMBOS` | Permission for that area. `ADMINISTRADOR` has all of them; `EMPLEADO` and `CLIENTE` get what the admin configures. See [permissions.md](../features/permissions.md) |
| ADMINISTRADOR | Only the store's admin role (cannot be delegated) |
| SUPERADMIN | Platform superadmin (token for `public`, no store) |
| Internal | Service to service, with the internal token (`X-internal-request: true` and `X-Internal-Token`) |

Store roles: `ADMINISTRADOR` (the owner), `EMPLEADO` (employee) and `CLIENTE` (customer). A user can have several.

### Errors

**The format is not uniform**; almost everything is the HTTP status with the message as **plain English text**:

```
HTTP/1.1 404
Product not found
```

| Case | Body |
|---|---|
| Business errors (400, 404, 409...) | Plain text with the message. Each doc lists the literal messages |
| Gateway 401 (no token, expired or from another store) | Empty |
| Gateway 404 / 403 for the store | Text: `The tenant was not found.` / `The tenant is disabled.` |
| 429 (rate limit) | Empty |
| Spring Security 403 (missing role or area) | Empty or Spring's default JSON |
| File larger than the limit | 413, `The file exceeds the maximum upload size` |
| Exceptions | A few cases return their own JSON: CSV import (errors per row), creating an order with stale prices (the new prices) and observability (Spring's default JSON: `timestamp`, `status`, `error`, `path`). They are on each endpoint's card |
| Unknown route / method / bad parameter | 404 `Not found` / 405 `Method not allowed` / 400 |
| Unexpected error | 500 |

For the frontend: treat the HTTP status as the source of truth and the text only for display or mapping.

### Pagination

Paginated lists take `page` (from 0) and `size`, and always return:

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }
```

Default and maximum size vary per endpoint (usually 20 and 100; the catalog 24 and 100). Out of range: 400.

### Dates

- Dates without time: `yyyy-MM-dd` (`2026-11-03`), in parameters (`from`, `to`) and responses.
- Store date-times: **no zone** (`2026-11-03T10:15:00`), in server time (UTC in Docker).
- Platform (`/api/v1/platform/**`): **with offset** (`2026-11-03T10:15:00Z`), so the browser does not shift them.
- observability and WS events: UTC instants with `Z`.
- Sales reports and dashboard periods count days in Argentina time (configurable).
- Dashboard periods use `groupBy` = `DAY`, `WEEK` (starts on Monday) or `MONTH`; each period is named by its first day
  and all of them are returned, including those with 0.

### Amounts and quantities

- Prices, costs and totals are decimal numbers in JSON (`15000.50`), with 2 decimals. No price = `null` ("ask for
  price").
- Stock is an integer. It only changes through stock movements, never through a product `PUT`.

### Ids and others

- Ids are numeric and **repeat across stores** (product 5 of one store is not another store's product 5).
- Orders are identified by their **number** (`K7QM-4X2P`, case-insensitive), not by id.
- Products have a **slug** (`mesa-de-roble`) for public links.

### Service health

Each service exposes `/health`, `/ready`, `/metrics` and `/status` on an internal **management port** (the service
port + 6000: users 9001, products 9002, sales 9003, observability 9004, gateway 9000, eureka 9761). The gateway does
not expose them. See [observability.md](observability.md#health-and-metrics).
