# gateway

Spring Cloud Gateway (WebFlux, reactive) on port **3000**, registered in Eureka as `gateway-microservice`. It is the
**only public entry point**: the frontend never talks to a service directly. It has no endpoints of its own: it
routes, recognizes the store, validates the login and protects.

Shared conventions (headers, errors, dates): [README](README.md). How the store is recognized, in detail:
[tenants.md](../features/tenants.md).

## What it does

- **Routes** each prefix to its service, through Eureka (`lb://...`).
- **Recognizes the store** by the `Origin` header and passes the schema to the services in `X-Forwarded-Host`.
- **Validates the JWT** on routes that need login and passes the user to the services in `X-User-Id`. The token must
  belong to the request's store.
- **Protects**: removes internal headers sent by the client, rate limits by IP and origin, allows CORS only for store
  and platform URLs, requires the internal token on `/api/internal/**`.
- **Observability**: marks the last activity of each logged-in user and anonymous store visitors in Redis, passes the
  client IP in `X-Client-Ip` (for the audit log) and tags HTTP metrics with the store.

The services **trust** `X-Forwarded-Host` and `X-User-Id` because only the gateway sets them.

## A request's path

Filters run in this order. Each one can stop the request; what it answers is on the right.

| # | Filter | What it does | If it stops the request |
|---|---|---|---|
| 1 | `ClientHeadersFilter` | Removes `X-User-Id`, `X-Forwarded-Host`, `X-Open-Path`, `X-Tenant-Name` and `X-Client-Ip` sent by the client. Sets `X-Client-Ip` to the connection's IP | — |
| 2 | `CorsConfig` | CORS (see below). Runs before the filters that stop requests, so their 401, 404/403 and 429 carry CORS headers and the browser lets the code be read. Answers preflights without going further | **403** without CORS headers if the origin is not accepted |
| — | `UnknownRouteFilter` | If the path does not match any of the gateway's [routes](#routes), stops it before Spring Security. A missing endpoint inside a service still requires login | **404**, empty body |
| 3 | `RequestFilter` | Rate limit by route type, IP and origin (see below) | **429**, empty body |
| 4 | `MsFilter` | `/api/internal/**`: validates the internal token (`X-Internal-Token`) | **401**, empty body |
| 5 | `TenantGatewayFilter` | Looks up the store by `Origin` and adds `X-Forwarded-Host: <schema>`. Skipped on `/api/v1/platform/**`, `/products/images/**`, `/users/avatars/**` and the platform frontend's `/ws` | **404** `The tenant was not found.` (missing or deleted) · **403** `The tenant is disabled.` · **503** if users does not answer |
| 6 | `JwtFilter` | On routes that need login, validates `Authorization: Bearer`, that it is a session token (`type` claim: neither the password recovery token nor the refresh token), that the token's `tenant` claim is the request's schema (`public` on `/api/v1/platform/**`), and adds `X-User-Id`. On public routes it adds `X-Open-Path: true`; on optional-login routes it still validates a token if present | **401**, empty body (no token, expired, invalid or from another store) |
| 7 | `ActivityFilter` | Marks `activity:<tenant>:<userId>` in Redis (5 min TTL, at most every 30 s per user), or `visitor:<tenant>:<id>` for anonymous visitors. Does not hold the request | — |
| — | Routing | Sends the request to the service | **503** if the service is not in Eureka |

`OPTIONS` always passes the JWT filter. Requests on the management port (9000) skip every filter.

## Routes

| Prefix | Service | Store |
|---|---|---|
| `/api/v1/products/**`, `/api/v1/bundles/**`, `/api/v1/catalog/**`, `/api/v1/store/**` | products | Yes |
| `/products/images/**` | products | No |
| `/users/avatars/**` | users | No |
| `GET /share/{store}/products/{slug}`, `GET /share/{store}/sitemap.xml` | products (`/share/products/{slug}`, `/share/sitemap.xml`) | Yes, from the **path** instead of `Origin` (a crawler opens it). No login. Missing store 404, disabled 403 |
| `/api/v1/platform/products/**` | products | No |
| `/api/v1/sales/**` | sales | Yes |
| `/api/v1/users/**`, `/api/v1/auth/**` | users | Yes |
| `POST /api/v1/bug-reports` | users | Yes (the store it is reported from) |
| `/api/v1/observability/**` | observability | Yes |
| `/api/v1/platform/observability/**` | observability | No |
| `/ws` | observability (WebSocket) | Depends on the ticket |
| `/api/v1/platform/**` (the rest) | users | No |

The products and observability platform routes have `order(-1)` to win over `/api/v1/platform/**`. `/api/internal/**`
is not routed: services call each other through Eureka.

### Public (no token)

Listed in `PermittedUrls.getExcludedPaths()`. Without a method they apply to any; `{id}` is a number, `{code}` one
segment and `{any}` anything.

| Path |
|---|
| `/api/v1/auth/login`, `/api/v1/auth/register`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/forgot-password/{any}` |
| `POST /api/v1/platform/tenants`, `POST /api/v1/platform/auth/login`, `POST /api/v1/platform/auth/refresh`, `POST /api/v1/platform/auth/logout` |
| `GET /api/v1/products/{id}/metadata` |
| `GET /api/v1/catalog/products`, `GET /api/v1/catalog/featured`, `GET /api/v1/catalog/products/{any}`, `GET /api/v1/catalog/categories`, `GET /api/v1/catalog/bundles`, `GET /api/v1/catalog/store` |
| `GET /api/v1/sales/checkout`, `POST /api/v1/sales/orders`, `POST /api/v1/sales/orders/lookup`, `POST /api/v1/sales/orders/{code}/receipt`, `POST /api/v1/sales/orders/{code}/cancel` |
| `POST /api/v1/bug-reports` |
| `/products/images/{any}` |
| `GET:/users/avatars/{any}` |
| `GET /share/{code}/products/{code}`, `GET /share/{code}/sitemap.xml` (see [products.md](products.md#share-link)) |
| `GET /ws` (uses a ticket, see [observability.md](observability.md#websocket)) |

A route being public at the gateway does not mean the service lets it through without login: the service applies its
own rules. Everything not on the list requires a token.

**Optional login:** `POST /api/v1/sales/orders` and `POST /api/v1/bug-reports`. Without a token they go through as a
guest; with a token the order or report is linked to the user, and an invalid token answers 401.

## Rate limit

**1-minute** windows per route type, connection IP and `Origin`. An `X-Forwarded-For` sent by the client does not
count; behind Caddy, `server.forward-headers-strategy=framework` builds the connection IP from the header Caddy sets.
It is counted in **Redis** (`RateLimitCounter`: `INCR` of `rate_limit:<type>:<origin>_<ip>:<minute>`, expiring after
2 minutes), so every gateway instance shares the counter. If Redis fails or takes more than 300 ms, it counts in that
instance's memory (Caffeine) and logs it once a minute: the limit still applies, but per gateway.

| Type | Routes | Limit per minute | Property |
|---|---|---|---|
| login | `/api/v1/auth/login`, `/api/v1/platform/auth/login` and both `/auth/refresh` | 5 | `limit.login` |
| signup | `/api/v1/platform/tenants`, `/api/v1/auth/register`, `/api/v1/auth/forgot-password/**`, `/api/v1/bug-reports` (they send emails or reach the superadmins) | 3 | `limit.signup` |
| order | `/api/v1/sales/orders` (create) | 5 | `limit.order` |
| lookup | `/api/v1/sales/orders/lookup`, `/api/v1/sales/orders/{code}/cancel` | 10 | `limit.lookup` |
| receipt | `/api/v1/sales/orders/{code}/receipt` | 5 | `limit.receipt` |
| global | Everything else | 300 | `limit.global` |
| image | `/products/images/**`, `/users/avatars/**` | No limit | — |

With a limit of 5, 5 requests pass and the sixth gets **429** with an empty body.

## CORS

- An `Origin` is accepted if it is:
  - the URL of an existing store (even a disabled one);
  - `platform.url` (the platform frontend);
  - a **platform subdomain** shaped like an identifier (`<identifier>.<host of platform.url>`, same scheme and port,
    e.g. `http://mystore.localhost:2999`), **even if the store does not exist or was deleted**. That way the 404 from
    `TenantGatewayFilter` arrives readable and the frontend shows "this store does not exist". It does not ask users.

  Any other gets **403** without CORS headers and the browser blocks the request.
- `CorsConfig` runs second (`@Order(-150)`), before rate limit, tenant and JWT: the responses those filters cut short
  already carry CORS headers. Preflights are answered there and do not count towards the rate limit.
- With an accepted origin: credentials allowed, every header and method.
- The lookup goes to the local cache, then Redis and finally users; only allowed origins are cached.
- Spring's own CORS is off (`spring.webflux.cors.enabled=false`): `CorsConfig` does it.

## Configuration and resilience

| Property | Development value | Purpose |
|---|---|---|
| `server.port` | `3000` | |
| `management.server.port` | `9000` | `/health`, `/ready`, `/metrics` (internal) |
| `jwt.secret` | — | Validates the JWTs issued by users. **Change it outside development** |
| `api.internal.key` | — | Internal token between services. **Change it outside development** |
| `platform.url` | `http://localhost:2999` | Platform frontend: CORS and the superadmin's `/ws` |
| `tenant.url` | `lb://users-microservice/api/internal/tenants` | Where stores are looked up |
| `users.url`, `products.url`, `sales.url`, `observability.url`, `observability.ws-url` | `lb://...` | Route targets |
| `spring.data.redis.host` / `.port` | `localhost:6379` | Stores, CORS, rate limit and last activity |
| `limit.*` | see above | Rate limit |
| `http.client.timeout` | `3s` | Timeout of calls to users |
| `resilience4j.*.instances.users.*` | see below | Circuit breaker and retries towards users |

- **Store lookup:** local cache (Caffeine, 1 minute) → Redis (`tenant:url:<url>`, written by users) →
  `GET /api/internal/tenants/id?url=` in users. Disabling or deleting a store takes up to 1 minute to apply.
- **Towards users:** circuit breaker (if 50% of the last 10 calls fail it opens for 10 s and then tries 3), up to 3
  attempts 200 ms apart and a 3 s timeout. 4xx responses do not count as failures and are not retried.
- **Metrics:** HTTP metrics carry the `tenant` tag (the schema, `none` without a store) and the WS uses `uri="/ws"`.
  observability uses that tag for per-store traffic.
- Each request is logged to the console (`LoggingFilter`, `ForwardTimingFilter`).

## Known limitations

- Responses cut by the gateway (401, store 404/403, 429) have an empty or plain-text body: the frontend has to decide
  by the status code.
- With Redis down the rate limit counts per instance (in memory) until it comes back.
- The IP for the rate limit and `X-Client-Ip` is the connection's. Behind a proxy other than Caddy, keep
  `server.forward-headers-strategy=framework` (otherwise it would be the proxy's) and make the proxy overwrite the
  client's `X-Forwarded-For`.
