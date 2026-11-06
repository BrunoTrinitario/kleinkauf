# Permissions

How the system decides what each user can do inside a store (tenant).

## Summary

- Each user has one or more **roles** in their store: `ADMINISTRADOR` (admin), `EMPLEADO` (employee) or `CLIENTE`
  (customer). The role names are Spanish because they are stored values.
- Each role has a **permission mask by area** (a number where each bit enables an area).
- **`ADMINISTRADOR` always has every permission** and is the only one who configures the other roles.
- `EMPLEADO` and `CLIENTE` permissions are configured **per store**: each business decides what its staff can do.
- Separately there is `SUPERADMIN`, who manages the platform (not a store) and lives in the `public` schema. See
  [tenants.md](tenants.md).
- **Sales** has no area yet: the sales panel belongs to `ADMINISTRADOR` only.

## Areas

| Area | Bit | What it enables |
|---|---|---|
| `PRODUCTOS` (products) | 1 | Create, edit and delete products, their images, metadata and featured flag |
| `PRECIOS` (prices) | 2 | See cost and margin, edit price and cost |
| `STOCK` | 4 | See and record stock movements, see the low stock alert |
| `COMBOS` (bundles) | 8 | Create, edit and delete bundles and change their price |
| `USUARIOS` (users) | 16 | Manage the store's users. **Cannot be delegated**: `ADMINISTRADOR` only |

The mask is the sum of the enabled areas' bits:

| Mask | Areas |
|---|---|
| `0` | None |
| `4` | STOCK |
| `5` | PRODUCTOS + STOCK |
| `7` | PRODUCTOS + PRECIOS + STOCK |
| `15` | Every delegable area (PRODUCTOS + PRECIOS + STOCK + COMBOS) |
| `31` | All (`ADMINISTRADOR` only) |

The areas are defined in `kleinkauf-common` (`com.kleinkauf.common.security.PermissionArea`).

## Permissions per role

| Role | Permissions | Configurable? |
|---|---|---|
| `ADMINISTRADOR` | Always `31` (all) | No: this way nobody gets locked out of their own store |
| `EMPLEADO` | `4` (STOCK) by default | Yes, any combination from `0` to `15` |
| `CLIENTE` | `0` (none) by default | Yes, any combination from `0` to `15` |
| `SUPERADMIN` | `0` in stores | No (manages the platform, not a store) |

- If a user has **several roles**, their permissions are the **union** of their roles' masks.
- `USUARIOS` (16) **cannot be granted** to `EMPLEADO` or `CLIENTE`. Anyone who could manage users without being an
  admin could grant themselves `ADMINISTRADOR`.

## Configuring permissions

Only an `ADMINISTRADOR` of the store (from the store's URL and with their token). In the frontend this is the
permission matrix on the users page.

### Read each role's permissions

```http
GET /api/v1/users/roles/permissions
```

```json
[
  { "role": "ADMINISTRADOR", "permissions": 31 },
  { "role": "EMPLEADO", "permissions": 4 },
  { "role": "CLIENTE", "permissions": 0 }
]
```

### Change a role's permissions

```http
PUT /api/v1/users/roles/EMPLEADO/permissions
Content-Type: application/json

{ "permissions": 7 }
```

```json
{ "role": "EMPLEADO", "permissions": 7 }
```

| Case | Response |
|---|---|
| `EMPLEADO` or `CLIENTE` role with a mask between `0` and `15` | 200 |
| Mask with `USUARIOS` (16), unknown bits (32, 64...) or negative | 400 |
| `ADMINISTRADOR` or `SUPERADMIN` role | 400 (not configurable) |
| Unknown role | 404 |
| Missing `permissions` in the body | 400 |
| User who is not `ADMINISTRADOR` | 403 |

## Permission required by each endpoint

### products

| Endpoint | Permission |
|---|---|
| `POST /api/v1/products` | PRODUCTOS (without PRECIOS, price and cost are **ignored**) |
| `PUT /api/v1/products/{id}` | PRODUCTOS |
| `DELETE /api/v1/products/{id}` | PRODUCTOS |
| `POST /api/v1/products/{id}/images`, `DELETE /api/v1/products/images/{imageId}` | PRODUCTOS |
| `PUT /api/v1/products/{id}/metadata` | PRODUCTOS |
| `PUT /api/v1/products/{id}/featured` | PRODUCTOS |
| `GET` / `PUT /api/v1/products/{id}/pricing` | PRECIOS (includes units sold) |
| `GET /api/v1/products/export`, `GET /api/v1/products/import/template`, `POST /api/v1/products/import` | PRODUCTOS **and** PRECIOS (the file carries price and cost) |
| `POST` / `GET /api/v1/products/{id}/stock/movements`, `GET /api/v1/products/stock/low` | STOCK |
| `POST`, `PUT`, `DELETE /api/v1/bundles/**` (including `PUT /{id}/price`) | COMBOS |
| `GET /api/v1/products`, `/{id}`, `/featured` and `GET /api/v1/bundles/**` (they show exact stock) | Any panel area (PRODUCTOS, PRECIOS, STOCK or COMBOS) |
| `GET /api/v1/catalog/**`, `GET /api/v1/products/{id}/images`, `GET /api/v1/products/{id}/metadata` | None: public |
| `/api/internal/products/**` (stock reservations and sold counters) | Internal microservice token (`INTERNAL` role); no user |

### sales

| Endpoint | Permission |
|---|---|
| `GET /api/v1/sales/checkout`, `POST /api/v1/sales/orders` | None: public. If the buyer is logged in, the order is linked to their user |
| `POST /api/v1/sales/orders/lookup`, `POST /api/v1/sales/orders/{number}/receipt`, `POST /api/v1/sales/orders/{number}/cancel` | None, but they require the **order's email** (404 if it does not match) |
| `GET /api/v1/sales/orders/mine` | Any logged-in user (sees only their own orders) |
| `/api/v1/sales/admin/**` (orders, in-store sales, status changes, receipts, reports and settings) | `ADMINISTRADOR` role. An `EMPLEADO` cannot get in even with every area |

### users

| Endpoint | Permission |
|---|---|
| `/api/v1/users/**` (user management, dashboard and permission settings) | `ADMINISTRADOR` role (USUARIOS area, not delegable) |
| `/api/v1/platform/admin/**` | `SUPERADMIN` role |

## How it works internally

```
users                                   products and sales
─────                                   ──────────────────
role_permissions (per schema)
  │ RolePermissionService.maskFor(role)
  ▼
RoleDto { type, permissions }  ──►  Redis  user:<id>:<tenant>  ──►      local cache (1 minute)
  │                                                                       │
  ▼                                                                       ▼
HeaderAuthFilter                                                  HeaderAuthFilter
  │                                                                       │
  └─► CustomUserDetails (kleinkauf-common): ROLE_<role> + PERM_<area> ◄──┘
                     │
                     ▼
              SecurityConfig: hasRole(...) / hasAuthority("PERM_STOCK")
```

1. **Where it is stored:** in the `role_permissions` table of each store's schema, one row per role (`type`,
   `permissions`). A role without a row uses its default.
2. **Who computes it:** users (`RolePermissionService.maskFor`). `ADMINISTRADOR` always returns 31.
3. **How it travels:** in `RoleDto.permissions`, next to the role type. It is returned by login, `GET /api/v1/auth/me`,
   the role endpoints and the internal endpoint used by products and sales (`/api/internal/users/roles/{id}`).
4. **How it is applied:** `CustomUserDetails` (in `kleinkauf-common`) turns roles into `ROLE_<role>` authorities and
   the union of masks into `PERM_<area>`. Each service's `SecurityConfig` requires those authorities.
5. **Cases the URL alone cannot decide:** creating a product checks inside the service whether the user has PRECIOS
   (`SecurityUtil.hasPermission`) to decide whether price and cost are used or ignored.

## How long a change takes to apply

- **users:** immediately (it reads permissions from the database on every request).
- **products and sales:** **under 1 minute**. When permissions change, users deletes the cached roles of every user of
  that store from Redis (`user:*:<tenant>`, with `SCAN`), and products and sales keep roles in their local cache for
  only 1 minute.
