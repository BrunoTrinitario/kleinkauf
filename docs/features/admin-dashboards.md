# Admin dashboards

Summary endpoints for the **store admin** and for the **platform admin** (superadmin). Sales reports
([sales.md](sales.md), `/api/v1/sales/admin/reports/**`) and real time ([observability.md](observability.md), `store.*`
channels) are shown on the same pages.

In the frontend: `/admin` is the store summary (sales, products, stock and users; each block loads on its own and if a
service fails the rest keeps working) and `/dashboard` is the platform panel.

## Decisions

| Topic | Decision |
|---|---|
| Who builds the summary | **Each service its own**: products for products and stock, users for users and tenants. The frontend calls them in parallel; if a service is down, the rest of the panel works |
| Store panel access | **`ADMINISTRADOR` only** (the whole panel, not by area) |
| Platform panel | **Tenants**, **usage per tenant** and **superadmin management**. The superadmin does not see orders or revenue of the stores |
| Active user | **Logged in during the last 30 days** (`lastLogin`) |
| Periods | **`from`, `to` and `groupBy=DAY\|WEEK\|MONTH`** parameters. Defaults to the last 30 days by day. **Every period is returned, including the ones with 0**. Days are cut in Argentina time (`DashboardTime`, `users.dashboard.zone`) |
| Usage per tenant | products exposes **its own platform endpoint** (counts products per schema); users gives users and last activity per tenant |
| Last activity | The most recent of the **last login** and the **last request** marked by the gateway (`activity:<tenant>:<userId>`, see [observability.md](observability.md)): a user with an open session does not log in again |
| Usage order | **Selectable** (`sort`), descending; by products by default |
| Cache | **Only usage per tenant**, 5 minutes in Redis. Store panels are always up to date |
| Inventory value | Products **without cost or price are excluded and counted** separately |
| Creating superadmins | Another superadmin creates them with a **temporary password**; they do **not** have to change it on first login |
| Deactivating superadmins | With **`deletedAt`** (soft delete); reactivating = clearing `deletedAt`. Nobody can deactivate themselves or the last active one |

## Store panel (ADMINISTRADOR)

Each service builds its panel with count and sum queries: products and users are never loaded in full.

### products — `GET /api/v1/products/dashboard`

```json
{
  "products": {
    "total": 120,
    "byCategory": [ { "category": "Mesas", "count": 18 }, { "category": "Sillas", "count": 30 } ],
    "withoutPrice": 7,
    "withoutImages": 12,
    "featured": 6
  },
  "stock": {
    "low": 9,
    "outOfStock": 3,
    "inventoryAtCost": "1250000.00",
    "inventoryAtPrice": "2380000.00",
    "excludedWithoutCost": 15,
    "excludedWithoutPrice": 7,
    "recentMovements": [
      { "id": 40, "productId": 12, "productName": "Mesa ratona", "type": "ENTRADA", "quantity": 5, "stockAfter": 9, "reason": "Compra", "reference": null, "userId": 3, "createdAt": "2026-11-02T10:00:00" }
    ]
  }
}
```

| Field | How it is computed |
|---|---|
| `total` | Products not deleted |
| `byCategory` | Count per category (no category → `null`, last), highest first, ties by name |
| `withoutPrice` | Empty `price` ("ask for price") |
| `withoutImages` | No image at all |
| `featured` | Marked as featured |
| `low` | `stock <= minStock` (same rule as `GET /products/stock/low` and the catalog); includes out-of-stock ones |
| `outOfStock` | `stock <= 0` |
| `inventoryAtCost` | Σ `stock × cost` of products with a cost (and stock > 0) |
| `inventoryAtPrice` | Σ `stock × price` of products with a price (and stock > 0) |
| `excludedWithoutCost` / `excludedWithoutPrice` | Products with stock left out of the value for lacking a cost / price |
| `recentMovements` | The **last 10** stock movements. `reference` is the order number for movements caused by sales; the product name is kept even if the product was deleted |

Amounts are numbers, always with 2 decimals and `0.00` when there is nothing to add up.

### users — `GET /api/v1/users/dashboard?from&to&groupBy`

```json
{
  "total": 14,
  "active": 9,
  "activeWindowDays": 30,
  "byRole": { "ADMINISTRADOR": 2, "EMPLEADO": 5, "CLIENTE": 8 },
  "registrations": {
    "from": "2026-10-04",
    "to": "2026-11-02",
    "groupBy": "DAY",
    "periods": [ { "period": "2026-10-04", "count": 0 }, { "period": "2026-10-05", "count": 2 } ]
  }
}
```

- `byRole` always includes the three roles (0 if empty). A user with several roles counts in each.
- `registrations` only counts users **still in the store** (unlike the platform, which includes deleted stores): for
  the owner what matters is the current customer base.
- Invalid `groupBy` or dates, `from > to` or more than 1,000 periods → 400.

## Platform panel (SUPERADMIN)

Everything under `/api/v1/platform/**`, without a tenant. In users it is `/api/v1/platform/admin/**`; in products,
`/api/v1/platform/products/**`. If a request arrives with a tenant, both answer 404.

Dates in platform responses (`createdAt`, `lastActivity`, `lastLogin`) carry an **offset** (`OffsetDateTime`): they are
stored in server time (UTC in Docker) and without an offset the browser showed them 3 hours off.

### users — `GET /api/v1/platform/admin/dashboard?from&to&groupBy`

```json
{
  "tenants": { "total": 42, "enabled": 39, "disabled": 3, "deleted": 5 },
  "registrations": { "from": "…", "to": "…", "groupBy": "WEEK", "periods": [ { "period": "2026-10-27", "count": 4 } ] }
}
```

- `total`, `enabled`, `disabled`: tenants not deleted. `deleted`: soft-deleted.
- `registrations`: sign-ups by `createdAt`, **including those deleted later** (it is the sign-up history).

### Usage per tenant

The frontend builds it by joining two lists by `schema`:

| Service | Endpoint | Data per tenant | `sort` |
|---|---|---|---|
| users | `GET /api/v1/platform/admin/usage?sort=` | `id`, `identifier`, `schema`, `enabled`, `createdAt`, `users`, `activeUsers`, `lastActivity` | `users`, `activeUsers`, `lastActivity`, `createdAt` (default `users`) |
| products | `GET /api/v1/platform/products/usage?sort=` | `schema`, `products`, `bundles` | `products` (default), `bundles` |

- **Every non-deleted tenant** is returned (no pagination: enough for hundreds of stores).
- Each service walks the schemas with plain SQL (`JdbcTemplate`, no Hibernate). users takes the schema from the tenant
  table; products from `information_schema` (schemas with a `product` table), so it does not depend on users. Both
  validate it with `^[a-z][a-z0-9_]*$` before building the SQL.
- A tenant that never opened its store has no schema in products and does not appear there; the frontend counts it
  as 0.
- **Cache**: each service keeps its list in Redis for 5 minutes (`platform:usage:users`, `platform:usage:products`).
  If Redis fails, it is computed anyway. Invalid `sort` → 400.

### Superadmin management (users)

| Method | Path | What it does |
|---|---|---|
| GET | `/api/v1/platform/admin/superadmins` | List with `id`, `name`, `email`, `active`, `createdAt`, `lastLogin` (including deactivated ones), by creation date |
| POST | `/api/v1/platform/admin/superadmins` | `{name, email, password}` → 201. Creates the user in `public` with the `SUPERADMIN` role. Same name, email and password rules as any user |
| PUT | `/api/v1/platform/admin/superadmins/{id}/active` | `{"active": false}` deactivates, `true` reactivates. Returns the superadmin. If it was already in that state, nothing changes |

| Case | Response |
|---|---|
| Missing name, email or password | 400 |
| Email already used, **also by a deactivated superadmin** | 409 |
| Deactivating yourself | 400 |
| Deactivating the last active superadmin | 409 |
| Id that is not a superadmin | 404 |

- **Deactivating** = `deleted_at` on the user **and on its `SUPERADMIN` role**. The role must be deleted too because
  users' `HeaderAuthFilter` looks up roles by user id without checking whether the user is deleted: if only the user
  were deleted, its token would keep working. Its roles are also removed from Redis.
- **Reactivating** = clearing `deleted_at` on the user and its `SUPERADMIN` role. Since `@SQLRestriction` hides deleted
  rows, the list, reactivation and the duplicate email check use **plain SQL** on `public` (`SuperadminService`).
- A deactivated superadmin cannot log in. In products their token may keep working for up to 1 more minute (local
  role cache).
- A script creates a superadmin directly on the server; see [deployment.md](../deployment.md).

## Known limitations

- "Active" is still the last login within 30 days: a user who works with a session opened more than 30 days ago counts
  as inactive. Last activity does use requests, but the `activity:*` keys last 5 minutes, so they do not cover a
  30-day window.
