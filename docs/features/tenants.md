# Tenants

How multi-tenancy works: what a tenant is, how it is recognized on each request, how one is created and how the
platform manages them.

## Concepts

- **Tenant:** a business that uses the platform (a store). Its data is isolated from every other tenant.
- **One schema per tenant:** each tenant has its own Postgres schema in **each** database: in `users` (its users and
  roles), in `products` (its products, stock, bundles...) and in `sales` (its orders, receipts and sales settings).
  Table names are the same in every schema.
- **Files per tenant:** the payment receipts stored by sales go to one folder per tenant
  (`uploads/sales/receipts/<schema>/<order number>/`).
- **`public` schema:** the platform itself. In the `users` database it holds the **tenants** table and the
  **superadmin** users. It does not belong to any store.
- **Tenant URL:** each tenant has the URL of its frontend, `<scheme>://<identifier>.<platform.domain>` (in development
  `http://mystore.localhost:2999`). **The gateway recognizes the tenant by that URL**, which arrives in the `Origin`
  header of every request.

### Tenant data (`tenant` table in `users`, `public` schema)

| Field | What it is |
|---|---|
| `id` | Numeric id |
| `schema` | Schema name (`my_store`). Unique |
| `url` | Frontend URL (`http://my-store.localhost:2999`). Used to recognize the tenant |
| `config_id` | UUID referencing the tenant's own configuration (reserved for later use) |
| `enable` | Whether it is enabled. Disabled → the gateway answers 403 |
| `created_at` | Creation date |
| `deleted_at` | Soft delete. Deleted → it does not exist for the app (404) |

## How the tenant is recognized on each request

```
Browser (Origin: http://my-store.localhost:2999)
   │
   ▼
gateway
   1. ClientHeadersFilter   removes the internal headers sent by the client
                            (X-User-Id, X-Forwarded-Host, X-Open-Path, X-Tenant-Name)
   2. CorsConfig            CORS: tenant origins, platform subdomains and the platform
                            frontend. Runs before the filters that reject requests so that
                            their 401/404/403/429 responses carry the CORS headers
   3. RequestFilter         rate limit by IP and origin (1-minute windows)
   4. MsFilter              /api/internal/**: validates the internal token
   5. TenantGatewayFilter   looks up the tenant by Origin:
                              local cache (1 min) → Redis tenant:url:<url> → users
                            missing or deleted → 404 · disabled → 403
                            adds X-Forwarded-Host: <schema>
                            (/api/v1/platform/** routes have no tenant and are skipped)
   6. JwtFilter             non-public routes: validates the JWT and that its "tenant" claim
                            matches the request's schema (or "public" on /api/v1/platform/**);
                            adds X-User-Id. On POST /api/v1/sales/orders the token is
                            optional, but it is validated if present
   │
   ▼
users / products / sales
   TenantFilter (kleinkauf-common)   reads X-Forwarded-Host and stores it in TenantContext
   Hibernate multi-tenancy           TenantIdentifierResolver → schema from TenantContext
                                     DatabaseMultiTenantConnectionProvider → connection on that schema
```

- In **products** and **sales** the tenant is required (without `X-Forwarded-Host` they answer 404). In **users** it
  is optional (`kleinkauf.tenant.required=false`): without a tenant it works on `public` (platform and internal
  endpoints).
- When sales calls products (reserving stock, sold counters) it forwards the same `X-Forwarded-Host`, so the
  reservation happens in the schema of the order's store.
- The microservices **trust** `X-Forwarded-Host` and `X-User-Id` because only the gateway sets them; that is why the
  gateway strips any the client sends.

### Tokens bound to the tenant

User ids repeat across tenants (user 1 of one store and user 1 of another are different people). Therefore:

- The **JWT** issued by users carries a `tenant` claim with the schema where the user logged in (`public` for the
  superadmin).
- The gateway rejects with **401** a token whose `tenant` does not match the request's.
- The **password reset token** also carries the tenant, and users checks it when the token is used.

## Creating a tenant

All the logic lives in **kleinkaufusers** (`/api/v1/platform`).

### Self-service (public)

```http
POST /api/v1/platform/tenants
Content-Type: application/json

{
  "identifier": "my-store",
  "ownerName": "Ana",
  "ownerEmail": "ana@mail.com",
  "ownerPassword": "secret"
}
```

```json
{
  "id": 7,
  "identifier": "my-store",
  "schema": "my_store",
  "url": "http://my-store.localhost:2999",
  "configId": "4f6c1c1e-...",
  "enabled": true,
  "createdAt": "2026-11-01T10:00:00"
}
```

What it does:

1. **Validates the identifier:** 3 to 30 characters, starts with a letter, only `a-z`, `0-9` and `-`, does not end
   with `-`. It cannot be reserved (`www`, `api`, `app`, `admin`, `platform`, `public`, `mail`, `static`, `assets`,
   `cdn`, `status`, `help`, `support`, `blog`, `dev`, `test`, `staging`, `root`, `information-schema`) nor start with
   `pg-`. This strict validation is what makes it safe to use in `CREATE SCHEMA` without SQL injection risk.
2. **Validates the owner:** name, email and password are required.
3. **Checks it does not exist:** the schema (`-` becomes `_`) cannot be in use, **not even by a deleted tenant**.
4. **Creates the tenant**, enabled, with its URL and a `configId` (UUID).
5. **Creates the owner** as `ADMINISTRADOR` and `EMPLEADO` inside the new schema (`TenantOwnerCreator`). Opening the
   connection makes users create the schema and its tables.
6. If creating the owner fails, **the whole sign-up is rolled back** (without an owner nobody could manage the
   tenant).

The frontend redirects to the tenant URL and the owner logs in there (the token is bound to that tenant).

| Case | Response |
|---|---|
| Successful sign-up | 201 with the tenant |
| Invalid or reserved identifier, or missing owner data | 400 |
| Identifier in use (also when two sign-ups arrive at the same time) | 409 |
| More than 3 sign-ups per minute from the same IP | 429 |

### Schemas

- **users:** the schema is created on sign-up (when the owner is saved). On startup users migrates the schemas of all
  existing tenants.
- **products:** the schema is created the first time a request for the tenant arrives. On startup products also
  migrates the schemas of all existing tenants (and fills in missing product slugs).
- **sales:** same as products (created on the first request, all migrated on startup).
- Tables are created and updated by Hibernate (`hbm2ddl.auto=update`), per schema.

### Jobs without a request

Order expiration in sales (`ExpirationJob`, every hour) has no request carrying a tenant: it asks users for the tenant
list (Redis → users) and walks through them one by one, putting each schema in the `TenantContext` while it works on
it. Deleted tenants are skipped; disabled ones are still processed so their reserved stock does not get stuck.

## Superadmin and platform panel

- A user with the `SUPERADMIN` role in the `public` schema. **It cannot be granted from a store.**
- The first one is created when users starts, if `SUPERADMIN_EMAIL` and `SUPERADMIN_PASSWORD` are set and that user
  does not exist yet.
- Logs in at `POST /api/v1/platform/auth/login` (token with `tenant = "public"`). A user without the `SUPERADMIN` role
  gets 401.

| Endpoint | What it does |
|---|---|
| `GET /api/v1/platform/admin/tenants` | Tenant list (without deleted ones), newest first |
| `GET /api/v1/platform/admin/tenants/{id}` | Detail |
| `POST /api/v1/platform/admin/tenants` | Create (same as self-service) |
| `PUT /api/v1/platform/admin/tenants/{id}/enabled` | `{"enabled": false}` disables; `true` enables |
| `DELETE /api/v1/platform/admin/tenants/{id}` | Soft delete (204) |

The gateway recognizes platform routes by their path (`/api/v1/platform/**`) and never adds a tenant to them. As an
extra defense, besides the role users also checks that the request does **not** carry a tenant: if it does, it
answers 404.

## Tenant states

| State | What the gateway does | Data |
|---|---|---|
| Enabled | Works normally | — |
| Disabled | **403** "The tenant is disabled." on every request | Untouched; can be enabled again |
| Deleted (soft delete) | **404** "The tenant was not found.", as if it did not exist | Schema and data stay in the database; the identifier stays reserved |

When a tenant is disabled or deleted, users removes that tenant's keys from Redis. The gateway also has a 1-minute
local cache, so **the change takes up to 1 minute** to apply.

With the tenant disabled or deleted nobody can reach its orders (the gateway stops them first). Orders that were
waiting for payment still expire and return their stock.

## Tenant caches

| Where | Key | What it holds | TTL |
|---|---|---|---|
| Redis (written by users) | `tenant:<schema>` | Tenant data | 1 hour |
| Redis (written by users) | `tenant:url:<url>` | Tenant data by URL (read by the gateway) | 1 hour |
| Redis (written by users) | `gateway:cors:<url>` | That the URL belongs to a tenant (CORS) | 5 hours |
| Gateway, local | `tenant:url:<url>`, `cors:<url>` | The same, in memory | 1 minute |
| products, local | `tenant:<schema>` | Tenant data | 10 minutes |
| sales, local | `tenant:<schema>` | Tenant data | 10 minutes |

## Configuration

| Service | Property | Purpose | Dev value |
|---|---|---|---|
| users | `platform.scheme` | Scheme of tenant URLs | `http` |
| users | `platform.domain` | Base domain for the subdomains | `localhost:2999` |
| users | `platform.superadmin.email` / `.password` | First superadmin (`SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`) | empty (not created) |
| users | `kleinkauf.tenant.required` | Tenant is optional in users | `false` |
| gateway | `platform.url` | Platform frontend (sign-up, panel), allowed by CORS | `http://localhost:2999` |
| gateway | `limit.signup` | Tenant sign-ups per minute per IP | `3` |

## Known limitations

- **Tenants created by hand** before self-service existed keep working with whatever `url` they have; in the panel
  their identifier is derived from the schema.
- **Single time zone:** sales reports cut days in one zone for every store (`sales.reports.zone`, Argentina).
