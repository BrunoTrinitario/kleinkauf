# kleinkauf-common

Shared library, **not a service**. It is a Gradle composite build: each service includes it with
`includeBuild('../kleinkauf-common')` in its `settings.gradle` and uses it as a dependency, without publishing it.
Almost everything is auto-configured: the service adds the dependency and the beans show up (each one can be replaced
by defining your own).

Shared conventions: [README](README.md).

## What it provides and who uses it

| Package | What is in it | gateway | users | products | sales | observability |
|---|---|---|---|---|---|---|
| `constants` | `Headers`, `TenantConstants` (`public`, the `tenant` claim), `ActivityKeys`, `UsageKeys` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `dto` | `TenantDto`, `RoleDto` and the stock reservation DTOs (`dto.stock`) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `security` | `InternalTokenService` (service-to-service token), `PermissionArea` (areas and masks) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `servlet.tenant` | `TenantFilter`, `TenantContext`, schema-based Hibernate multi-tenancy (`TenantIdentifierResolver`, `DatabaseMultiTenantConnectionProvider`), `TenantSchemaManager` | | ✓ | ✓ | ✓ | |
| `servlet.security` | `CustomUserDetails`: `ROLE_<role>` and `PERM_<area>` authorities | | ✓ | ✓ | ✓ | ✓ |
| `servlet.exceptions` | `ApiException` (message + status) and `GlobalExceptionHandler` | | ✓ | ✓ | ✓ | |
| `events` | `KleinKaufEvent` and `KleinKaufEventPublisher` (Redis Streams `kleinkauf:events`) | | ✓ | ✓ | ✓ | ✓ (reads) |
| `audit` | `@Audited`, `@AuditIgnore`, `AuditLog`, outbox and `AuditEvent` | | ✓ | ✓ | ✓ | ✓ (reads) |
| `observability` | Actuator on the management port, traces, logs with `tenant` and `userId`, `tenant` tag (`TenantTag`) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `autoconfigure` | `RedisTemplate<String,Object>` (JSON), Caffeine cache (`caffeineCache`, 10 min) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ImageFiles` | Detects an image type (JPG, PNG, WEBP, GIF) from its first bytes | | ✓ | | | |

## Using it in a new service

1. **Gradle:** `includeBuild('../kleinkauf-common')` in `settings.gradle` and the dependency in `build.gradle` (copy
   from `kleinkauf-sales`). Also add it to `backend/settings.gradle` to import it with the rest.
2. **Minimum properties:**

| Property | Purpose |
|---|---|
| `spring.application.name` | Name in Eureka, metrics and audit log |
| `server.port` | The management port becomes this + 6000 unless `management.server.port` is set |
| `api.internal.key` | Creates the `InternalTokenService` (same key in every service) |
| `spring.data.redis.*` | Enables `RedisTemplate`, events and the audit publisher |
| `kleinkauf.tenant.required` | `true` by default: without `X-Forwarded-Host` it answers 404. `false` = without a store it works on `public` |
| `kleinkauf.tenant.public-paths` | Prefixes allowed without a store (e.g. `/products/images/,/api/v1/platform/`) |

3. **Multi-tenancy:** implement a `TenantSchemaManager` bean (see products' or sales' `TenantDataSource`) that creates
   and migrates a store's schema the first time it is used. With it, `KleinKaufServletAutoConfiguration` builds the
   multi-tenant `EntityManagerFactory`: each request uses the schema in `TenantContext`.
4. **Security:** your own `HeaderAuthFilter` that reads `X-User-Id`, asks users for the roles and builds
   `CustomUserDetails`; and a `SecurityConfig` with the `TenantFilter` before it. Copy the one from sales or products.
5. **Errors:** throw `ApiException(message, HttpStatus)`; `GlobalExceptionHandler` answers the message as text with
   that status. Request errors answer with their own status: unknown route 404 (`Not found`), a method the route does
   not have 405 (`Method not allowed`), a parameter of the wrong type 400 (`Invalid value for <parameter>`) and a body
   that is not JSON 400. Any other exception answers 500 and is logged with its stack trace (`Unhandled error`).
6. **Audit:** mark entities with `@Audited` (and fields that should not be recorded with `@AuditIgnore`). For anything
   that is not a create, update or delete, inject `AuditLog` and call `record(...)` or `read(entity, id)`. Turned off
   with `kleinkauf.audit.enabled=false`.
7. **Events for the live panel:** inject `KleinKaufEventPublisher` and call `publish(type, tenant, data)`. Inside a
   transaction it goes out after commit; if Redis fails it is logged and the call goes on. Add the type to
   `KleinKaufEvent` and its routing to observability's `EventStreamListener`.
8. **Gateway:** the route in `GatewayRoutes` and, if there are public routes, in `PermittedUrls`.

## Contracts between services

### Headers

| Header | Set by | Contents |
|---|---|---|
| `X-Forwarded-Host` | gateway (and a service calling another, with the same value) | Store schema |
| `X-User-Id` | gateway | User id from the JWT |
| `X-Open-Path` | gateway | `true` on public routes |
| `X-Client-Ip` | gateway | Connection IP (for the audit log) |
| `X-internal-request` | The calling service | `true` on internal calls |
| `X-Internal-Token` | The calling service | Internal JWT |

The gateway removes `X-User-Id`, `X-Forwarded-Host`, `X-Open-Path`, `X-Tenant-Name` and `X-Client-Ip` if the client
sends them.

### Internal token

A JWT signed with `api.internal.key` (different from the users' `jwt.secret`), with the service name as subject and a
**10-second** lifetime: one is generated per call with `generateMicroServiceToken(name)` and validated with
`validateInternalToken(token)`. products gives it the `INTERNAL` role (required by its `/api/internal/**`); users
requires it on all of `/api/internal/**`.

### User JWT

Issued by users (`jwt.secret`) with `id`, `email`, `name`, `tenant` (the schema, or `public`) and its type (`access`,
`refresh` or `password`, so a session token cannot change a password and the email token cannot be used to log in).
The access token lasts 1 hour. The gateway validates it and compares `tenant` with the request's store.

### Roles and permissions

`RoleDto { type, permissions }`: `permissions` is the `PermissionArea` mask (`PRODUCTOS`=1, `PRECIOS`=2, `STOCK`=4,
`COMBOS`=8, `USUARIOS`=16). `CustomUserDetails` grants `ROLE_<type>` for each role and `PERM_<area>` for the union of
the masks: `SecurityConfig` uses `hasRole("ADMINISTRADOR")` or `hasAuthority(PermissionArea.STOCK.authority())`.

### Shared Redis

| Key / stream | Written by | Read by | Contents |
|---|---|---|---|
| `user:<id>:<schema>` (hash `roles`) | users | gateway, products, sales, observability | User roles (1 h) |
| `tenant:<schema>`, `tenant:url:<url>` (hash `data`) | users | gateway, products, sales | `TenantDto` (1 h) |
| `gateway:cors:<url>` | users | gateway | Allowed origin |
| `activity:<schema>:<userId>` | gateway | users, observability | Time of the last request in ms (5 min). `ActivityKeys` |
| `visitor:<schema>:<id>` | gateway | observability | Anonymous visitor of a store (5 min; the id is a hash of IP and browser) |
| `usage:<schema>:<yyyy-MM-dd>` (hash) | products | observability | Catalog visits and views per product (8 days). `UsageKeys` |
| `kleinkauf:events` (stream) | users, products, sales | observability | `KleinKaufEvent`: `type`, `tenant`, `at` (UTC), `data` (JSON). Trimmed to ~10,000 |
| `kleinkauf:audit` (stream) | outbox of users, products, sales | observability | `AuditEvent` |

The gateway also keeps the rate limit counters and users the refresh tokens (`jti`) in Redis.

### Events (`KleinKaufEvent`)

| Type | Published by | `tenant` | `data` |
|---|---|---|---|
| `tenant.created`, `tenant.enabled`, `tenant.disabled`, `tenant.deleted` | users | `public` | `id`, `identifier`, `schema` |
| `order.created`, `order.receipt-uploaded` | sales | The store | `number`, `channel`, `status`, `total`, `buyerName` |
| `stock.movement` | products | The store | `productId`, `productName`, `movementType`, `quantity`, `stockAfter`, `reason`, `userId` |
| `stock.low` | products | The store | `productId`, `productName`, `stock`, `minStock` |

A value can be `null` (e.g. the `reason` of a movement without one): it goes as `null` in the JSON.

They are for the live panel: if Redis fails they are lost. The audit log, which must not be lost, goes through each
database's `public.outbox_events` table and a publisher that drains it into `kleinkauf:audit` (every 2 s,
`kleinkauf.audit.publish-interval`). Format in [observability.md](observability.md#audit-format).

### Observability

`ObservabilityEnvironmentPostProcessor` sets defaults (whatever the service defines wins):

- Actuator on the management port (`server.port + 6000`) with `/health`, `/ready`, `/metrics` and `/status`; the port
  is published in Eureka (`management.port` in the metadata).
- Latency histogram for HTTP requests and the `application` tag.
- Traces with OpenTelemetry (OTLP export is turned on by the Docker stack) and logs with `traceId`, `spanId`, `tenant`
  and `userId`.
