# Design decisions

The main decisions behind KleinKauf and why they were made, grouped by topic. Dates are when each was decided. The
details of how things work today are in the feature docs and the [API reference](backend/README.md).

## Foundations

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | Before adding features: a **shared library** and **Resilience4j** | Put the base in order before adding new services |
| 2026-11-01 | The library is a **Gradle composite build** (`kleinkauf-common`, via `includeBuild`) instead of a root multi-project build | Each service stays an independent Gradle project; nothing has to be published |
| 2026-11-01 | `kleinkauf-common` is **a single project**: shared code in `com.kleinkauf.common` and servlet code in `com.kleinkauf.common.servlet` | Simpler to handle in the IDE. Spring dependencies are `compileOnly` and the servlet part does not activate in the (reactive) gateway |
| 2026-11-01 | The library configures itself through **Spring Boot auto-configuration** | Services only add the dependency; every bean can be replaced |
| 2026-11-01 | Resilience4j on products → users and gateway → users (circuit breaker + retry + timeouts). 4xx do not count as failures | If users goes down, the rest answers fast with cache or a clear error instead of hanging |
| 2026-11-02 | `backend/settings.gradle` groups the backend projects with `includeBuild` (each stays independent) | Import the whole backend into the IDE at once |
| 2026-11-03 | Backend reference in `docs/backend/`, **one file per service** with full cards (curl, responses, literal errors) | Integrating the frontend or starting on a service is fast |

## Products, prices and stock

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | Price and cost as `BigDecimal`. **Price is public**; **cost and margin are only for the admin** (`/products/{id}/pricing`) | Avoid rounding errors with money and never expose cost in the catalog |
| 2026-11-01 | Stock **only changes through movements** (`ENTRADA`, `SALIDA`, `AJUSTE`); `PUT /products/{id}` no longer touches it | Traceability: who changed the stock, when and why |
| 2026-11-01 | Movements can be recorded by admin and employee; row lock (`PESSIMISTIC_WRITE`) per product | The employee is the one receiving goods; the lock stops two simultaneous movements from overwriting each other |
| 2026-11-01 | **Featured products**: `featured` + optional `featuredOrder`. Marking them is admin-only | Show chosen products on the home page, in the order the business wants |
| 2026-11-01 | **CSV-only import/export** (the file includes cost) | No heavy dependencies; Excel opens it anyway |
| 2026-11-01 | Columns: basic data, price, cost and metadata (**no stock**: it is handled through movements). Spanish headers | The owner understands the file and stock keeps its traceability |
| 2026-11-01 | Format: `;`, UTF-8 with BOM and **Argentine number format** (`15.000,50`); an ambiguous format like `15.5` is an error. Apache Commons CSV | Spanish-locale Excel opens it with a double click; nobody types 15 meaning 15,000 |
| 2026-11-01 | Import: **the name identifies the product**; if it exists it is updated and **an empty cell does not change the value**; if not, it is created with stock 0. **All or nothing**, with errors per row. Unknown columns = error | Works both to load the catalog and to bulk-update prices without overwriting data |
| 2026-11-01 | Limits: 5,000 rows and 5 MB. **Images stay at 1 MB** (own validation); a file over the limit → 413 | A huge file must not block the service |
| 2026-11-01 | CSV import/export requires PRODUCTOS **and** PRECIOS. Creating a product without PRECIOS **ignores** price and cost | The file carries cost; creating a product must not bypass the price permission |

## Bundles

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | A bundle has its own price; it also shows the list price (sum of its products) and how many can be put together with current stock | A bundle has no stock of its own: it depends on its products' |
| 2026-11-04 | The **bundle is created whole** in `POST /bundles` (name, products and price) and everything is validated before saving; each product of a bundle carries its price and image | A bundle without products is useless and building it over several requests left half-created bundles. With each product's price the admin can pick the bundle's and `/combos` can show the breakdown |
| 2026-11-04 | **Discounted bundles** (`PERCENT` or `AMOUNT` off the sum of products) besides the fixed price. With a discount the price is **computed on read** and not stored | It follows the products' prices without updating the bundles. Updating them on every product change would have meant covering every way of changing a price (endpoint, CSV) and could leave stale copies; orders already keep the price they charged |

## Tenants and platform

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | **Store sign-up** through public self-service and through a **superadmin**; all the logic in kleinkaufusers | A business can sign up on its own and the platform can manage stores |
| 2026-11-01 | Store URL = **automatic subdomain** `<identifier>.<platform.domain>`. The user chooses the identifier: 3–30 characters, starts with a letter, `a-z 0-9 -`, unique and not reserved; the schema uses `_` | The gateway recognizes the store by its URL; strict validation prevents SQL injection in `CREATE SCHEMA` |
| 2026-11-01 | Sign-up creates the **owner as ADMINISTRADOR and EMPLEADO**; the store is **active immediately**; it returns the data and the URL (no token) | Without an owner nobody could manage the store; the token is bound to the store, so the owner logs in on its subdomain |
| 2026-11-01 | **Superadmin** in the `public` schema with the `SUPERADMIN` role (cannot be granted from a store); the first one is created at startup from `SUPERADMIN_EMAIL` / `SUPERADMIN_PASSWORD` | Platform users kept apart from each store's users |
| 2026-11-01 | **Disabled** store → 403 at the gateway; **deleted** → 404 (it does not exist for the app, the identifier stays reserved) | users decides; the gateway only reads the flag it already gets |
| 2026-11-01 | **JWT bound to the store** (`tenant` claim; `public` for the superadmin) and checked at the gateway; same for the password reset token | User ids repeat across stores: a token from one store worked in another |
| 2026-11-01 | The gateway **removes internal headers** (`X-User-Id`, `X-Forwarded-Host`, `X-Open-Path`, `X-Tenant-Name`) sent by the client | The services trust those headers |
| 2026-11-01 | **Gateway tenant cache by URL with a 1-minute TTL** | Fewer calls to users; disabling or deleting takes up to 1 minute to apply |
| 2026-11-03 | `CorsConfig` runs **before** the filters that reject requests and accepts any **platform subdomain** shaped like an identifier, even if the store does not exist | The gateway's 401, 404/403 and 429 went out without CORS headers: the browser saw a network error and the frontend could not read the status |
| 2026-11-03 | A store's **last `ADMINISTRADOR`** cannot be deleted (not even by themselves) or lose the role: **409**, like the last superadmin | Without an admin nobody can manage the store or give the role back |

## Permissions

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | **Permissions by area, by role, configurable per store**: PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16 (bit mask, also in the API) | Finer than the role without the complexity of per-user permissions or custom roles |
| 2026-11-01 | The mask lives in a **per-store table** (`role_permissions`, one row per role); `roles.permission` (an int per user, always 0) is dropped | One source per role; the old column was `NOT NULL` and broke role creation when removed from the entity |
| 2026-11-01 | **ADMINISTRADOR always has everything** and is the only one who configures; EMPLEADO and CLIENTE are configurable; **USUARIOS cannot be delegated** | Nobody gets locked out and nobody can grant themselves ADMINISTRADOR |
| 2026-11-01 | Defaults as before: EMPLEADO = STOCK, CLIENTE = nothing | Nothing changes until the admin configures it |
| 2026-11-01 | Propagation: changing permissions removes the store's cached roles from Redis and the services cache roles for 1 minute | The change applies in under a minute |

## Catalog

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | **Public catalog** under `/api/v1/catalog` (paginated, search, category, sort, detail by slug and categories) | A frontend built on a dedicated, cacheable public API |
| 2026-11-01 | The catalog **does not show exact stock**: `DISPONIBLE`, `POCAS_UNIDADES` (stock ≤ minimum) or `AGOTADO`. No price = "ask" (`price: null`), last when sorting by price | Do not expose inventory; custom furniture has no fixed price |
| 2026-11-01 | **Fixed slug** per product (generated on creation, does not change with the name; if repeated, `-2`) | Shared links never break |
| 2026-11-01 | Search is case-insensitive but **accent-sensitive** (no `unaccent`). 24 per page, at most 100 | Simple, no Postgres extensions |
| 2026-11-01 | **Catalog cache in Redis** with a version per store (every change bumps it after commit) and a 1-hour TTL; if Redis fails it reads from the database | Try distributed caching without ever showing stale data |
| 2026-11-02 | **Bundles in the public catalog** (enabled, priced, with active products) and `id` on catalog products | The cart needs the id to build the order |
| 2026-11-02 | Sold counter on the product price (`PRECIOS` only) and `best_sellers` sort in the catalog without showing the number | Do not expose how much a store sells |
| 2026-11-03 | Old reads with **exact stock** (`GET /products/{id}`, `/featured`, `/bundles/**`) require a panel area; public featured products move to `GET /api/v1/catalog/featured` | The public catalog never shows exact stock |
| 2026-11-03 | Share link at **`/share/{store}/products/{slug}`**: the store goes in the path and the gateway resolves it by identifier; products returns an HTML page with Open Graph that redirects to the product page | WhatsApp and Instagram crawlers do not run JavaScript or send `Origin`; no Node server is needed for the frontend |

## Sales

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | **Sales in a new microservice, `kleinkauf-sales`**, guest or registered buyer, cart in the frontend, products and bundles | Decouple sales from products and practice service-to-service communication. Without sales the system is just a catalog |
| 2026-11-01 | Payment by **bank transfer with a receipt** validated **only by the admin**; Mercado Pago later | The simplest thing that works for a small business today |
| 2026-11-01 | The order **reserves stock when created** (synchronous, idempotent call to products) and returns it if cancelled or **after 48 h** without a receipt | Never sell the same item twice while waiting for the transfer |
| 2026-11-01 | It counts as a sale **when the payment is validated**. Reports by period, by product, profit (cost saved at the time of sale) and a sold counter only in the panel | Measure what was sold and earned without counting unpaid orders |
| 2026-11-01 | The guest follows their order with **email + random number** (`K7QM-4X2P`) | A sequential number could be enumerated and expose other buyers' data |
| 2026-11-01 | **In-store sales**: the admin enters already paid orders (channel `LOCAL`, `PAGADO` or `ENTREGADO`) with payment method, optional buyer, changed price or discount; without stock it still sells with an automatic `AJUSTE` and is flagged for review | Record every sale, not only online ones. Reports split by channel and payment method |
| 2026-11-02 | **Stock is reserved first** and then the order is saved; if something fails, sales asks to release. products remembers each reservation by number (`stock_reservations`): reserve, release and sold are **idempotent** and a release arriving before the reservation voids it | A timeout or a retry never leaves stock deducted without an order, nor deducts it twice |
| 2026-11-02 | Order number `XXXX-XXXX` without confusable characters, **unique per store** | It can be read out loud and cannot be enumerated |
| 2026-11-02 | Online order: up to **30 lines and 20 units** per line; buyer with name, email and phone; shipping with full address. Rate limit: 5 orders, 10 lookups or cancellations and 5 receipts per minute | Stop abuse of the public checkout |
| 2026-11-02 | If the price changed between cart and order, **409 with the new prices** and no order | The buyer never pays a different price without knowing |
| 2026-11-02 | **Optional login** when creating an order: the gateway passes the user if the token is valid and answers 401 if it is invalid | Link the order to "my orders" without forcing sign-up |
| 2026-11-02 | Receipts: JPG, PNG or PDF up to 5 MB (type by content), **up to 5 per order, never deleted**. The admin can validate without a receipt; rejecting and cancelling require a reason | Proof of each sale; covers payments by other means |
| 2026-11-02 | Expiration **every hour** across stores; an order under review does not expire | Free reserved stock without loading the service |
| 2026-11-02 | Orders use **optimistic locking** (`@Version`): admin, buyer and expiration never overwrite each other (409) | Avoid inconsistent states without locking rows |
| 2026-11-02 | In-store sale: discount as a **fixed amount** capped at the subtotal; limits of 100 lines and 999 units; **voiding returns everything sold**, including what was adjusted | The returned goods exist even if the system said they did not |
| 2026-11-02 | Reports: sale = paid order, dated by **payment** (`paid_at`); days in the **store's zone** (`sales.reports.zone`, Argentina); **no cache or CSV** for now | A late-night sale does not fall on the next day; numbers always up to date |

## Admin dashboards

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | Dashboards with today's data and **each service builds its own summary** (products: products and stock; users: users, stores, usage per store and superadmins). Store panel `ADMINISTRADOR` only | If a service is down the rest of the panel works; sales and observability plug in with the same format |
| 2026-11-01 | Periods with `from`, `to` and `groupBy` (day/week/month) including zeros; active = login within 30 days; usage per store cached 5 min; superadmins with a temporary password and deactivation through `deletedAt` | Series ready to chart; walking every schema is expensive; reuse the existing soft delete |
| 2026-11-03 | Store panel: user sign-ups count **only users still in the store** (the platform includes deleted stores) | For the owner, the current customer base is what matters |
| 2026-11-03 | Last activity per store = the most recent of the **last login** and the `activity:*` keys | A user with an open session does not log in again |
| 2026-11-04 | Dashboard periods in **Argentina time**. The platform does **not** add up orders and revenue across stores, and "active" stays as last login within 30 days | The superadmin does not see the stores' data |

## Observability and audit

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | Actuator with `/health`, `/ready` and `/metrics` on an internal management port; OpenTelemetry + Prometheus + Grafana + Tempo + Loki; a new `kleinkauf-observability` microservice with platform and store panels in real time over **plain WebSocket** with a short-lived ticket | Know how the platform and each store are doing live, without exposing metrics outside |
| 2026-11-01 | **Audit log** of data changes, platform actions and sensitive reads, with before/after of the changed fields, through an **outbox** to observability; kept **forever** and each sees their own (admin their store, superadmin the platform) | Control over what each user does without losing events or mixing stores' data |
| 2026-11-02 | **Management port = service port + 6000**; `/health` is liveness and `/ready` does not depend on other microservices; trace export and JSON logs only turn on with the Docker stack | A failing service does not take the others down, and without the stack there are no connection errors |
| 2026-11-02 | Development stack as a compose **overlay** (`docker-compose.observability.yml`) with **Alloy** as the trace and log collector; Prometheus finds instances through Eureka | One command and no hand-written service lists |
| 2026-11-02 | WS **revoked access** = every minute the roles of connected users are checked and the WS of anyone who lost them is closed. Live events through `kleinkauf:events` with no consumer group (each instance sees everything) | The same minute REST access is cut, without extra events between users and observability |
| 2026-11-02 | Audit: a **Hibernate listener** for before/after, **one outbox per service** (`public.outbox_events`, with a `tenant` column), only what a person does (no internal calls or background jobs), the user's name stored on receipt, and the CSV import as **one** summary entry | Atomic with the change without walking schemas, no noise, and the author is not lost if the user is deleted |
| 2026-11-03 | `store.activity`: `stock.movement` **only for manual movements**; `stock.low` **when crossing** the minimum with any movement | An order with several products does not flood the feed: `order.created` already arrives |
| 2026-11-03 | Per-store traffic **only from the gateway**; catalog usage with **daily counters in Redis** written by products (8 days) | Each request goes through the gateway once; per-product detail is not in the metrics |
| 2026-11-04 | Eureka stays a single instance. Trace sampling, Grafana alerts, retention, `/ready` healthchecks and usage history beyond 8 days stay as they are for now | Not needed at the current size |

## Security and sessions

| Date | Decision | Why |
|---|---|---|
| 2026-11-03 | The password recovery email links to `<store URL>/reset-password?token=` | An email link makes a GET: it has to open a frontend page, not the API |
| 2026-11-04 | **Refresh tokens** (7 days) with a `jti` stored in Redis, single use (30 s grace for other tabs), revoked on logout or password change | Sessions last without long-lived access tokens, and logging out really ends them |
| 2026-11-04 | JWTs carry their **type** (`access`, `refresh`, `password`); the recovery link works once | A session token cannot change a password and the email token cannot be used to log in |
| 2026-11-04 | Rate limit counted in **Redis**, shared by every gateway, by connection IP (not the client's `X-Forwarded-For`); in memory if Redis fails | The limit holds with several gateways and cannot be dodged with a forged header |
| 2026-11-04 | The gateway answers 404 on routes it does not route; users requires the internal token on all of `/api/internal/**`; products rejects anything without its own rule | Close everything by default |
