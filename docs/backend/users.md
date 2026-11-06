# users (kleinkaufusers)

Spring Boot (servlet) on port **3001**, registered in Eureka as `users-microservice`. Postgres database `users`: one
schema per store and `public` for the platform.

Shared conventions (headers, errors, pagination, dates): [README](README.md). Background:
[tenants.md](../features/tenants.md), [permissions.md](../features/permissions.md),
[admin-dashboards.md](../features/admin-dashboards.md).

## What it does

- **Store authentication:** login, customer sign-up, own profile and password recovery by email. Issues the JWTs,
  bound to the store.
- **Store users:** the admin lists, creates, edits and deletes them, and grants or removes roles.
- **Permissions per role:** which panel areas `EMPLEADO` and `CLIENTE` can use in each store.
- **Store panel:** user summary (total, active, by role, sign-ups per period).
- **Platform:** store sign-up (self-service and superadmin), enabling, disabling and deleting stores, superadmin login
  and management, and the platform panel (summary and usage per store).
- **Bug reports** sent from the stores.
- **Email:** password recovery and order confirmation.
- **For the other services:** looks up stores (by URL or schema) and a user's roles, and keeps stores and roles in Redis
  so they do not have to ask every time.

### Concepts

| Concept | What it is |
|---|---|
| Role | `ADMINISTRADOR` (the owner, has everything), `EMPLEADO`, `CLIENTE`. A user can have several. `SUPERADMIN` exists only in `public` and cannot be granted from a store |
| Area | `PRODUCTOS`=1, `PRECIOS`=2, `STOCK`=4, `COMBOS`=8, `USUARIOS`=16. A role's permissions are a bit mask (`5` = PRODUCTOS + STOCK) |
| Default permissions | `ADMINISTRADOR`: everything (31, not configurable). `EMPLEADO`: `STOCK` (4). `CLIENTE`: nothing (0). `USUARIOS` cannot be delegated |
| Store owner | Created with the store, with the `ADMINISTRADOR` and `EMPLEADO` roles. A store always keeps at least one `ADMINISTRADOR` |
| Deletion | Soft (`deleted_at`): a deleted user or role shows up nowhere, but its email stays taken |
| Active | Logged in during the last 30 days |

### Validation rules

The same for sign-up, creation from the panel, a new store's owner, superadmins and profile changes. The frontend has
a copy (`frontend/src/lib/validation.ts`): if they change, change both.

| Field | Rule | Error (400) |
|---|---|---|
| Name | Required, up to 100 characters (not counting surrounding spaces) | `The name is required and can have up to 100 characters.` |
| Email | `user@domain.ext`, no spaces, up to 254 characters | `The email is not valid.` |
| Password | 8 to 72 characters (72 bytes), with at least one letter and one digit | `The password must have 8 to 72 characters, with at least one letter and one number.` |
| Store identifier | 3 to 30 characters: starts with a letter, lower case, digits and hyphens, does not end with a hyphen (`^[a-z][a-z0-9-]{1,28}[a-z0-9]$`) | `The identifier must have 3 to 30 characters, start with a letter and only use lowercase letters, numbers and hyphens.` |
| Reserved identifier | `www`, `api`, `app`, `admin`, `platform`, `public`, `mail`, `static`, `assets`, `cdn`, `status`, `help`, `support`, `blog`, `dev`, `test`, `staging`, `root`, `information-schema` and anything starting with `pg-` | `The identifier is reserved.` |

A user's email must be unique **within the store** (a user in another store can use the same one).

## Flows

### Login and using the token

1. `POST /api/v1/auth/login` from the store (with its `Origin`) → `{ name, id, email, token, refreshToken, roles }`.
   The email is case-insensitive.
2. Send `Authorization: Bearer <token>` on each request. The token lasts **1 hour** and only works in that store.
3. `roles` carries each role with its permission mask: the frontend builds the menu from it (for example, show "Stock"
   if some role has bit 4).
4. Expired: `POST /api/v1/auth/refresh` with the `refreshToken` (lasts **7 days**) returns new tokens without asking for
   the password. The frontend does it on its own on a 401 and repeats the call.
5. Each refresh token is used **once**: renewing returns a new one and the old one stops working 30 seconds later (in
   case another tab renews at the same time). They are kept in Redis (`refresh:<tenant>:<userId>:<jti>`, 7 days).
6. A refresh token stops working if that session is closed (`POST /auth/logout`), if the password changes (it carries a
   fingerprint of the password) or if the user is deleted. With an expired or no longer valid refresh token, the user
   has to log in again.

Each token carries its type in the `type` claim (`access`, `refresh` or `password`, see `TokenConstants` in common): the
gateway only lets the session one through, and each users endpoint accepts only its own.

Superadmins do the same with `POST /api/v1/platform/auth/login` from the platform frontend (no store).

### Creating a store

`POST /api/v1/platform/tenants` (self-service, no login) or `POST /api/v1/platform/admin/tenants` (superadmin):

1. Validates the identifier and the owner's data.
2. Creates the tenant in `public.tenant`: schema `my_store` (hyphens become `_`), URL
   `<platform.scheme>://my-store.<platform.domain>` (`http://my-store.localhost:2999`), enabled.
3. Creates the store's schema in the `users` database with its tables, and the owner with the `ADMINISTRADOR` and
   `EMPLEADO` roles. If it fails, the sign-up is rolled back.
4. Publishes the `tenant.created` event (seen live on the platform panel).

products and sales create their schema on their own with the store's first request. After sign-up, the owner logs in
at their store's URL with the email and password they entered.

### Password recovery

1. `POST /api/v1/auth/forgot-password` with the email, from the store. It always answers `true` (it does not reveal
   whether the email exists).
2. If the user exists, users sends an email with the link `<store URL>/reset-password?token=<token>`. The token lasts
   **15 minutes**, belongs to that store and works **only once**: it carries a fingerprint of the current password and
   stops working when the password changes. It is a `password` token (`type` claim): it cannot be used to log in, and a
   session token cannot change the password.
3. The **frontend's** `/reset-password` page reads the token, asks for the new password and calls
   `POST /api/v1/auth/forgot-password/{token}` with `{ "newPassword": "..." }`.

In the frontend: "¿Olvidaste tu contraseña?" on the login leads to `/recuperar`, which asks for the email. Stores only:
the gateway does not serve `/api/v1/auth/**` without a store, so the platform has no recovery.

If the email cannot be sent, the error is logged and not retried. It goes out through Resend with the store's name as
sender and the owner as `Reply-To`; see [mail.md](../mail.md).

### Creating and deactivating superadmins

The first superadmin is created at startup if `SUPERADMIN_EMAIL` and `SUPERADMIN_PASSWORD` are set. The rest are created
by another superadmin with a temporary password. Deactivating does not delete: it sets `deleted_at` on the user and its
role, and can be reverted. Nobody can deactivate themselves or the last active one.

## Authentication

### POST /api/v1/auth/login

**Access:** Public · **Rate limit:** 5 per minute

Logs in a store user and returns the token.

```bash
curl -X POST http://localhost:3000/api/v1/auth/login \
  -H 'Origin: http://demo.localhost:2999' \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"demo1234"}'
```

Response **200**:

```json
{
  "name": "Dueño Demo",
  "id": 1,
  "email": "admin@demo.local",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "roles": [
    { "type": "ADMINISTRADOR", "permissions": 31 },
    { "type": "EMPLEADO", "permissions": 4 }
  ],
  "avatar": null
}
```

| Status | When | Message |
|---|---|---|
| 401 | Wrong email or password (or the user is deleted) | `Invalid username or password.` |

Updates the last login (`lastLogin`), which the dashboards use for "active".

### POST /api/v1/auth/refresh

**Access:** Public (with the refresh token) · **Rate limit:** the login one, 5 per minute

Renews the session: returns the same as login, with new tokens (the refresh token too) and up-to-date roles. It does
not update the last login. The refresh token sent stops working 30 seconds later: keep the new one.

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

| Status | When | Message |
|---|---|---|
| 401 | Invalid, expired, from another store, of another type, already used, from a closed session, of a deleted user or from before a password change | `The access token is invalid.` |

### POST /api/v1/auth/logout

**Access:** Public (with the refresh token)

Closes the session: the refresh token stops working. The 1-hour token remains valid until it expires (the gateway does
not store them). Always answers **204**, even if the refresh token was already invalid, so the frontend logs out
anyway.

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

### POST /api/v1/auth/register

**Access:** Public · **Rate limit:** 3 per minute

Signs up a customer (`CLIENTE` role) in the store and leaves them logged in.

```bash
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H 'Origin: http://demo.localhost:2999' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana Pérez","email":"ana@mail.com","password":"secret123"}'
```

Response **201**: like login, with `roles: [{ "type": "CLIENTE", "permissions": 0 }]`.

| Status | When | Message |
|---|---|---|
| 400 | Invalid name, email or password | See [rules](#validation-rules) |
| 409 | Email used in the store | `The email is already in use.` |

### GET /api/v1/auth/me

**Access:** Login

The logged-in user's data.

```bash
curl http://localhost:3000/api/v1/auth/me \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Response **200** (`FullUserDto`, the same format as the panel's users):

```json
{ "id": 1, "name": "Dueño Demo", "email": "admin@demo.local",
  "roles": [ { "type": "ADMINISTRADOR", "permissions": 31 } ],
  "avatar": "0b6f…e2.png" }
```

`avatar` is the profile photo (a file in [`/users/avatars/`](#get-usersavatarsfile)) or `null`. It also comes with
login, refresh and the panel's users.

| Status | When | Message |
|---|---|---|
| 404 | The user was deleted after logging in | `The user was not found.` |

### PATCH /api/v1/auth/me

**Access:** Login

Changes your own name or password (used by the frontend's settings page). `null` fields do not change; the email cannot
be edited.

```bash
curl -X PATCH http://localhost:3000/api/v1/auth/me \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana P.","password":null}'
```

Response **200**: `FullUserDto`. Errors: 400 for [validation](#validation-rules), 404 `The user was not found.`

### DELETE /api/v1/auth/me

**Access:** Login

Users delete themselves (soft delete, roles included). Used by the settings page ("Eliminar mi cuenta"). Response
**200** with the text `The user has been deleted`.

| Status | When | Message |
|---|---|---|
| 404 | User does not exist | `The user was not found.` |
| 409 | They are the store's last `ADMINISTRADOR` | `The store needs at least one ADMINISTRADOR: the last one cannot be deleted or lose the role.` |

### POST /api/v1/auth/me/avatar

**Access:** Login (any role)

Uploads your own profile photo and replaces the previous one, whose file is deleted. Multipart with the `file` field:
PNG, JPG, WEBP or GIF up to 1 MB. The type is checked by content, not by name or `Content-Type`; SVG is not accepted
because it can carry scripts.

```bash
curl -X POST http://localhost:3000/api/v1/auth/me/avatar \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -F 'file=@photo.png'
```

Response **200**: `FullUserDto` with the new `avatar`.

| Status | When | Message |
|---|---|---|
| 400 | No file or empty | `The photo file is required.` |
| 413 | Larger than 1 MB | `The photo must be 1 MB or smaller.` |
| 415 | Not PNG, JPG, WEBP or GIF | `Only JPG, PNG, WEBP or GIF photos are allowed.` |

### DELETE /api/v1/auth/me/avatar

**Access:** Login

Removes your own photo and deletes the file. Response **200**: `FullUserDto` with `avatar: null` (also if there was
none).

### GET /users/avatars/{file}

**Access:** Public, without a store (browsers do not send `Origin` when loading an `<img>`), like product images. File
names are random and each new photo gets a new one, so they are served with a 30-day cache.

Photos live on disk at `avatar.path` (`uploads/users/avatars`), one folder for every store. Files are only touched once
the database change is final: on commit the replaced photo is deleted and, if the transaction fails, the one just saved
is. Deleting an account (`DELETE /auth/me` or `DELETE /users/{id}`) also deletes its photo. Superadmins have no photo.

### POST /api/v1/auth/forgot-password

**Access:** Public · **Rate limit:** 3 per minute

Requests the recovery email. See the [flow](#password-recovery).

```bash
curl -X POST http://localhost:3000/api/v1/auth/forgot-password \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com"}'
```

Response **200**: `true`, whether the email exists or not.

### POST /api/v1/auth/forgot-password/{token}

**Access:** Public (with the email's token)

Changes the password with the email's token.

```bash
curl -X POST http://localhost:3000/api/v1/auth/forgot-password/eyJhbGciOi... \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"newPassword":"newpass1234"}'
```

Response **200**: the user's `FullUserDto`.

| Status | When | Message |
|---|---|---|
| 401 | Invalid, expired, from another store, a session token or already used | `The access token is invalid.` |
| 400 | Invalid password | `The password must have 8 to 72 characters, with at least one letter and one number.` |

## Store users

All of `/api/v1/users/**` is **`ADMINISTRADOR` only** (the `USUARIOS` area cannot be delegated).

### GET /api/v1/users

**Access:** ADMINISTRADOR

The panel's paginated list, by name.

| Parameter | Default | What it does |
|---|---|---|
| `q` | — | Searches name and email |
| `role` | — | `ADMINISTRADOR`, `EMPLEADO` or `CLIENTE` (case-insensitive) |
| `page` | 0 | |
| `size` | 20 | At most 100 |

```bash
curl 'http://localhost:3000/api/v1/users?q=rios&role=CLIENTE&page=0&size=20' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Response **200**:

```json
{
  "items": [
    { "id": 12, "name": "Valentina Ríos", "email": "valentina.rios@demo.local",
      "roles": [ { "type": "CLIENTE", "permissions": 0 } ], "avatar": null }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

| Status | When | Message |
|---|---|---|
| 400 | `page` < 0 or `size` outside 1–100 | `Invalid page: page must be 0 or more and size between 1 and 100.` |
| 400 | Unknown role | `The role provided is invalid.` |

### POST /api/v1/users

**Access:** ADMINISTRADOR

Creates a user with the given roles. Unknown roles (and `SUPERADMIN`) are ignored.

```bash
curl -X POST http://localhost:3000/api/v1/users \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Juan Gómez","email":"juan@demo.local","password":"employee1","roles":["EMPLEADO"]}'
```

Response **201**: `FullUserDto`.

| Status | When | Message |
|---|---|---|
| 400 | Invalid data | See [rules](#validation-rules) |
| 409 | Email used in the store | `The email is already in use.` |

### GET /api/v1/users/{id}

**Access:** ADMINISTRADOR

Response **200**: `FullUserDto`. Error: 404 `The user was not found.` (The sales panel uses it to show who made each
change.)

### PATCH /api/v1/users/{id}

**Access:** ADMINISTRADOR

Changes a user's name or password. `null` fields do not change; the email cannot be changed.

```json
{ "name": "Juan G.", "password": "other1234" }
```

Response **200**: `FullUserDto`. Errors: 400 for [validation](#validation-rules), 404 `The user was not found.`

### DELETE /api/v1/users/{id}

**Access:** ADMINISTRADOR

Soft-deletes the user and their roles, and removes their roles from Redis. Response **200** with the text
`The user has been deleted`. Errors: 404 `The user was not found.` · 409 if they are the store's last `ADMINISTRADOR`
(same message as `DELETE /auth/me`).

### GET /api/v1/users/{id}/roles

**Access:** ADMINISTRADOR

Response **200**: `[ { "type": "EMPLEADO", "permissions": 4 } ]`.

### POST /api/v1/users/{id}/roles?roleName=

**Access:** ADMINISTRADOR

Grants a role (`ADMINISTRADOR`, `EMPLEADO` or `CLIENTE`). If they had it and it was removed, it is reactivated; if they
already have it, nothing happens.

```bash
curl -X POST 'http://localhost:3000/api/v1/users/12/roles?roleName=EMPLEADO' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Response **200**: `FullUserDto` with the new roles.

| Status | When | Message |
|---|---|---|
| 400 | Unknown role | `The role provided is invalid.` |
| 404 | User does not exist | `The user was not found.` |

### DELETE /api/v1/users/{id}/roles?roleName=

**Access:** ADMINISTRADOR

Removes a role. Response **200**: `FullUserDto`.

| Status | When | Message |
|---|---|---|
| 400 | Unknown role | `The role provided is invalid.` |
| 404 | User does not exist or does not have that role | `The user was not found.` |
| 409 | Removing `ADMINISTRADOR` from the store's last admin | `The store needs at least one ADMINISTRADOR: the last one cannot be deleted or lose the role.` |

### GET /api/v1/users/dashboard

**Access:** ADMINISTRADOR

Store panel: users, active, by role and sign-ups per period. Always up to date.

| Parameter | Default | What it does |
|---|---|---|
| `from`, `to` | Last 30 days up to today | `yyyy-MM-dd`, inclusive |
| `groupBy` | `DAY` | `DAY`, `WEEK` or `MONTH` |

Days are in Argentina time (`users.dashboard.zone`): a sign-up on Monday at 10 pm belongs to Monday even if in UTC it is
already Tuesday.

```bash
curl 'http://localhost:3000/api/v1/users/dashboard?from=2026-10-01&to=2026-10-31&groupBy=WEEK' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Response **200**:

```json
{
  "total": 14,
  "active": 9,
  "activeWindowDays": 30,
  "byRole": { "ADMINISTRADOR": 1, "EMPLEADO": 5, "CLIENTE": 8 },
  "registrations": {
    "from": "2026-10-01", "to": "2026-10-31", "groupBy": "WEEK",
    "periods": [ { "period": "2026-09-29", "count": 0 }, { "period": "2026-10-06", "count": 3 } ]
  }
}
```

- `byRole` always has the three roles; a user with several roles counts in each.
- `registrations` counts users still in the store; `period` is the period's first day.

| Status | When | Message |
|---|---|---|
| 400 | Malformed date or invalid `groupBy` | `Invalid value for from.` (or `to`, `groupBy`) |
| 400 | `from` > `to` or more than 1,000 periods | `Invalid range: from must not be after to, with up to 1000 periods.` |

## Permissions per role

### GET /api/v1/users/roles/permissions

**Access:** ADMINISTRADOR

Response **200**:

```json
[
  { "role": "ADMINISTRADOR", "permissions": 31 },
  { "role": "EMPLEADO", "permissions": 4 },
  { "role": "CLIENTE", "permissions": 0 }
]
```

### PUT /api/v1/users/roles/{role}/permissions

**Access:** ADMINISTRADOR

Configures the `EMPLEADO` or `CLIENTE` permissions in the store. Applies within 1 minute (the services' role cache).

```bash
curl -X PUT http://localhost:3000/api/v1/users/roles/EMPLEADO/permissions \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"permissions":5}'
```

Response **200**: `{ "role": "EMPLEADO", "permissions": 5 }`.

| Status | When | Message |
|---|---|---|
| 400 | No `permissions`, or bits outside 1–8 (including `USUARIOS`=16) | `Invalid permissions: use PRODUCTOS=1, PRECIOS=2, STOCK=4 and COMBOS=8 (USUARIOS cannot be delegated).` |
| 400 | `ADMINISTRADOR` or `SUPERADMIN` | `Only the EMPLEADO and CLIENTE permissions can be configured.` |
| 404 | Unknown role | `The role provided is invalid.` |

## Platform

Routes without a store: called from the platform frontend (`http://localhost:2999`). If a request arrives with a store,
they answer **404** `This endpoint is only available on the platform.` Dates carry an offset.

### POST /api/v1/platform/tenants

**Access:** Public · **Rate limit:** 3 per minute

Self-service store sign-up. See the [flow](#creating-a-store).

```bash
curl -X POST http://localhost:3000/api/v1/platform/tenants \
  -H 'Origin: http://localhost:2999' -H 'Content-Type: application/json' \
  -d '{"identifier":"my-store","ownerName":"Ana Pérez","ownerEmail":"ana@mail.com","ownerPassword":"secret123"}'
```

Response **201**:

```json
{
  "id": 7,
  "identifier": "my-store",
  "schema": "my_store",
  "url": "http://my-store.localhost:2999",
  "configId": "3f1c6a2e-...",
  "enabled": true,
  "createdAt": "2026-11-03T13:00:00Z"
}
```

| Status | When | Message |
|---|---|---|
| 400 | Invalid identifier | `The identifier must have 3 to 30 characters, start with a letter and only use lowercase letters, numbers and hyphens.` |
| 400 | Reserved identifier | `The identifier is reserved.` |
| 400 | Missing owner data | `The owner name, email and password are required.` |
| 400 | Invalid owner data | See [rules](#validation-rules) |
| 409 | Identifier in use (also by a deleted store) | `The identifier is already in use.` |

### POST /api/v1/platform/auth/login

**Access:** Public · **Rate limit:** 5 per minute

Superadmin login. Same body and response as [the store one](#post-apiv1authlogin), with
`roles: [{ "type": "SUPERADMIN", "permissions": 0 }]` and a `public` token.

| Status | When | Message |
|---|---|---|
| 401 | Wrong credentials, deactivated superadmin or user without the `SUPERADMIN` role | `Invalid username or password.` |

### POST /api/v1/platform/auth/refresh

**Access:** Public (with the refresh token) · **Rate limit:** the login one

Same as [the store one](#post-apiv1authrefresh), with a `public` refresh token. A deactivated superadmin no longer has
the role, so it is not renewed: 401 `The access token is invalid.`

### POST /api/v1/platform/auth/logout

**Access:** Public (with the refresh token)

Same as [the store one](#post-apiv1authlogout), with a `public` refresh token. **204**.

### GET /api/v1/platform/admin/tenants

**Access:** SUPERADMIN

Non-deleted stores, newest first. Response **200**: list of `PlatformTenantDto` (the sign-up format).

### GET /api/v1/platform/admin/tenants/{id}

**Access:** SUPERADMIN

Response **200**: `PlatformTenantDto`. Error: 404 `The tenant was not found.` (missing or deleted).

### POST /api/v1/platform/admin/tenants

**Access:** SUPERADMIN

Same as self-service sign-up, but without the sign-up rate limit. Same body, response and errors.

### PUT /api/v1/platform/admin/tenants/{id}/enabled

**Access:** SUPERADMIN

Enables or disables a store. While disabled, the gateway answers 403 to everything for that store (takes up to 1
minute). Publishes `tenant.enabled` or `tenant.disabled`.

```bash
curl -X PUT http://localhost:3000/api/v1/platform/admin/tenants/7/enabled \
  -H 'Origin: http://localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"enabled":false}'
```

Response **200**: `PlatformTenantDto`. Error: 404 `The tenant was not found.`

### DELETE /api/v1/platform/admin/tenants/{id}

**Access:** SUPERADMIN

Soft-deletes the store: the data stays, the gateway answers 404 and the identifier cannot be reused. Publishes
`tenant.deleted`. Response **204**. Error: 404 `The tenant was not found.`

### GET /api/v1/platform/admin/dashboard

**Access:** SUPERADMIN

Stores by state and sign-ups per period (deleted ones included). Same parameters and errors as
[the store panel](#get-apiv1usersdashboard).

```json
{
  "tenants": { "total": 42, "enabled": 39, "disabled": 3, "deleted": 5 },
  "registrations": { "from": "2026-10-04", "to": "2026-11-02", "groupBy": "DAY",
    "periods": [ { "period": "2026-10-04", "count": 1 } ] }
}
```

`total`, `enabled` and `disabled` are about non-deleted stores.

### GET /api/v1/platform/admin/usage?sort=

**Access:** SUPERADMIN

Users, active users and last activity of each non-deleted store, highest first. Kept 5 minutes in Redis.

| `sort` | Sorts by |
|---|---|
| `users` (default) | Users |
| `activeUsers` | Active users (login in 30 days) |
| `lastActivity` | Last activity (stores without activity last) |
| `createdAt` | Sign-up date |

```json
[
  { "id": 7, "identifier": "my-store", "schema": "my_store", "enabled": true,
    "createdAt": "2026-10-01T10:00:00Z", "users": 14, "activeUsers": 9,
    "lastActivity": "2026-11-03T12:58:00Z" }
]
```

`lastActivity` is the most recent of the last login and the last request (gateway marks, last 5 minutes); `null` if
nobody ever logged in. Products per store come from products (`/api/v1/platform/products/usage`): the frontend joins
both lists by `schema`.

Error: 400 `Invalid sort: use users, activeUsers, lastActivity or createdAt.`

### GET /api/v1/platform/admin/superadmins

**Access:** SUPERADMIN

Every superadmin, deactivated ones included, by creation date.

```json
[ { "id": 1, "name": "Superadmin", "email": "superadmin@kleinkauf.local", "active": true,
    "createdAt": "2026-10-01T10:00:00Z", "lastLogin": "2026-11-03T12:00:00Z" } ]
```

### POST /api/v1/platform/admin/superadmins

**Access:** SUPERADMIN

Creates a superadmin with a temporary password (they are not asked to change it).

```json
{ "name": "Lucía", "email": "lucia@kleinkauf.local", "password": "temporary123" }
```

Response **201**: `SuperadminDto`.

| Status | When | Message |
|---|---|---|
| 400 | Invalid or missing data | See [rules](#validation-rules) |
| 409 | Email in use, also by a deactivated superadmin | `The email is already in use.` |

### PUT /api/v1/platform/admin/superadmins/{id}/active

**Access:** SUPERADMIN

Deactivates (`{"active": false}`) or reactivates (`true`) a superadmin. Once deactivated they cannot log in, and their
token stops working within 1 minute. If it was already in that state, nothing changes.

Response **200**: `SuperadminDto`.

| Status | When | Message |
|---|---|---|
| 400 | Deactivating yourself | `You cannot deactivate yourself.` |
| 409 | It is the last active one | `The last active superadmin cannot be deactivated.` |
| 404 | Does not exist or is not a superadmin | `The superadmin was not found.` |

## Bug reports

"Reportar un problema" in the stores' footer. Stored in `public.bug_reports` and read by the superadmins on the platform
panel (`/bug-reports`).

### POST /api/v1/bug-reports

**Access:** Public, optional login (rate limit of 3 per minute)

The store comes from `Origin` (the gateway resolves it like any store route). With an open session the user is kept,
and if no `email` is sent the account's is used. `pageUrl` is cut to 500 characters and the `User-Agent` to 300.

```json
{ "description": "The cart does not add the shipping correctly", "email": "ana@mail.com", "pageUrl": "https://demo.kleinkauf.site/carrito" }
```

Response **201** with no body.

| Status | When | Message |
|---|---|---|
| 400 | Description shorter than 10 or longer than 2000 characters | `The description must have 10 to 2000 characters.` |
| 400 | Invalid email | `The email is not valid.` |

### GET /api/v1/platform/admin/bug-reports?status=&page=&size=

**Access:** SUPERADMIN

Newest first. `status`: `pending` (default), `resolved` or `all`. `size`: 20 by default, at most 100. `pending` is how
many are unresolved in total, whatever the filter.

```json
{ "items": [ { "id": 4, "store": "demo", "storeUrl": "https://demo.kleinkauf.site", "userId": 7,
    "reporterName": "Ana", "email": "ana@mail.com", "description": "The cart does not add the shipping correctly",
    "pageUrl": "https://demo.kleinkauf.site/carrito", "userAgent": "Mozilla/5.0 ...",
    "createdAt": "2026-11-04T12:30:00Z", "resolvedAt": null } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1, "pending": 1 }
```

Errors: 400 `Invalid value for status.` and 400 `Invalid page: page must be 0 or more and size between 1 and 100.`

### PUT /api/v1/platform/admin/bug-reports/{id}/resolved

**Access:** SUPERADMIN

Marks the report as resolved (`{"resolved": true}`) or back to pending (`false`). Marking an already resolved one again
does not change its resolution date.

Response **200**: the report. **404** `The bug report was not found.`

## Internal

Not called by the frontend and not routed by the gateway: the services call them directly through Eureka, with
`X-Internal-Token` (see [common.md](common.md)). users requires the token on all of `/api/internal/**`.

| Method | Path | Used by | Returns |
|---|---|---|---|
| GET | `/api/internal/tenants/id?url=` | gateway | `TenantDto` of the store with that URL, or empty if it does not exist or was deleted |
| GET | `/api/internal/tenants/id?schema=` | products, sales | `TenantDto` by schema. Without `url` or `schema`: 400 `Falta el parametro url o schema` |
| GET | `/api/internal/tenants/cors?url=` | gateway | `true` if the URL belongs to a non-deleted store (even a disabled one) |
| GET | `/api/internal/tenants/all` | products and sales (they migrate the schemas at startup; sales also for expiration) | Every non-deleted store |
| GET | `/api/internal/users/{id}` | observability (names for the audit log) | `FullUserDto`; 404 `The user was not found.` |
| GET | `/api/internal/users/roles/{id}` | products, sales, observability | `[{ "type", "permissions" }]` of the user in the `X-Forwarded-Host` store (without a store: `public`) |
| POST | `/api/internal/mail/order-confirmation` | sales | Sends the buyer the confirmation email of an online order (`OrderConfirmationMailDto`). Only once per order (Redis mark `mail:order:<schema>:<number>`, 2 days). 204 even if the email fails; 400 if the number is missing or the email is invalid |

`TenantDto`:

```json
{ "id": 7, "configId": "3f1c6a2e-...", "schema": "my_store", "enable": true,
  "createdAt": "2026-10-01T10:00:00", "deletedAt": null }
```

## How it communicates

- **Receives** from the gateway `X-Forwarded-Host` (the store; without it, it works on `public`) and `X-User-Id`. It
  builds the user's roles from the `roles` table and their permission mask.
- **Redis** (read by the gateway, products, sales and observability):

| Key | Contents | TTL | Removed |
|---|---|---|---|
| `user:<id>:<schema>` (hash `roles`) | The user's roles with their mask | 1 hour | When their roles change, they are deleted or the store's permissions change |
| `tenant:<schema>` (hash `data`) | `TenantDto` | 1 hour | When the store is enabled, disabled or deleted |
| `tenant:url:<url>` (hash `data`) | `TenantDto` by URL (for the gateway) | 1 hour | Same |
| `gateway:cors:<url>` | URL allowed by CORS | 5 hours | Same |
| `platform:usage:users` | Usage per store for the platform panel | 5 minutes | — |
| `refresh:<schema>:<userId>:<jti>` | Valid refresh tokens | 7 days | On use (30 s grace), logout or password change |
| `mail:order:<schema>:<number>` | Order confirmation already sent | 2 days | If sending fails |
| `activity:<schema>:<id>` | Last request (written by the gateway; users only reads it) | 5 minutes | — |

- **Events** (Redis Streams `kleinkauf:events`): `tenant.created`, `tenant.enabled`, `tenant.disabled`,
  `tenant.deleted`, with `{ id, identifier, schema }`.
- **Audit log:** `User`, `Role`, `RolePermission` and `Tenant` are `@Audited` (creates, updates and deletes go to the
  outbox; last login does not count). Deactivating and reactivating superadmins is recorded by hand. Store and
  superadmin actions are stored with tenant `public`. See [audit.md](../features/audit.md).
- **Email:** SMTP (Resend) for password recovery and order confirmation. See [mail.md](../mail.md).
- **Calls products** (`GET /api/v1/catalog/store`) for the store name used as email sender. If products does not answer,
  it uses the name derived from the identifier.

## Data model

`users` database. Hibernate creates the tables with `hbm2ddl` (no Flyway). Column names without `@Column` stay lower
case without underscores (`lastlogin`, `userid`).

At startup, `TenantDataSource` migrates `public` and the schema of each non-deleted store (like products and sales), so
an entity change reaches all of them on deploy. A new store creates its schema on the first connection.

**In each store's schema:**

| Table | Columns | Notes |
|---|---|---|
| `usuarios` | `id`, `name`, `email` (unique), `password` (bcrypt), `avatar`, `created_at`, `lastlogin`, `deleted_at` | Soft delete |
| `roles` | `id`, `type`, `userid`, `deleted_at` | Unique (`userid`, `type`); soft delete |
| `role_permissions` | `id`, `type` (unique), `permissions` | Only the rows the admin configured; no row = default permissions |

**In `public`:** `bug_reports` (`id`, `tenant_schema`, `user_id`, `reporter_name`, `email`, `description`, `page_url`,
`user_agent`, `created_at`, `resolved_at`), `tenant` (`id`, `schema`, `url`, `config_id`, `enable`, `created_at`,
`deleted_at`) and the superadmins in `usuarios` and `roles` with the `SUPERADMIN` role. See
[tenants.md](../features/tenants.md#tenant-data-tenant-table-in-users-public-schema).

## Configuration

| Property | Development value | Purpose |
|---|---|---|
| `server.port` / `management.server.port` | `3001` / `9001` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/users` | Database |
| `spring.data.redis.*` | `localhost:6379` | Cache and events |
| `jwt.secret` | — | Signs the JWTs. Must match the gateway's. **Change it outside development** |
| `api.internal.key` | — | Internal token. **Change it outside development** |
| `kleinkauf.tenant.required` | `false` | Without a store it works on `public` (platform and internal) |
| `users.dashboard.zone` | `America/Argentina/Buenos_Aires` | Time zone of the dashboard days (sign-ups per period) |
| `platform.scheme`, `platform.domain` | `http`, `localhost:2999` | Build each new store's URL and the recovery link |
| `platform.superadmin.email` / `.password` | `${SUPERADMIN_EMAIL}` / `${SUPERADMIN_PASSWORD}` | First superadmin, at startup |
| `avatar.path` | `uploads/users/avatars` | Profile photo folder |
| `spring.mail.*` | `smtp.resend.com:587`, user `resend`, password `${MAIL_PASSWORD}` | Mail server (Resend). Without `MAIL_PASSWORD` no email goes out |
| `mail.from.address` | `${MAIL_FROM:no-reply@mail.kleinkauf.site}` | Address of every email |
| `mail.from.platform-name` | `KleinKauf` | Sender of emails without a store |
| `products.url` | `http://products-microservice/` | Store name for the sender |

Running it alone: `cd backend/kleinkaufusers && ./gradlew bootRun` (with Postgres, Redis and Eureka up). See
[getting started](../getting-started.md).

## Known limitations

- Logging out cuts the refresh token but not the 1-hour token, which stays valid until it expires. There is no "log out
  all my sessions" (changing the password does it).
- If the recovery email fails, it is logged as `ERROR` (`MailService`) but not retried.
- An unknown role answers 400 when it comes as a parameter (`?roleName=`, `?role=`) and 404 when it is in the path
  (`/roles/{role}/permissions`).
