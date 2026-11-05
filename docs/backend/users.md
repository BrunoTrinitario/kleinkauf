# users (kleinkaufusers)

Spring Boot (servlet) en el puerto **3001**, registrado en Eureka como `users-microservice`. Base Postgres `users`:
un schema por tienda y `public` para la plataforma.

Convenciones comunes (headers, errores, paginación, fechas): [README](README.md). Temas de fondo:
[tenants.md](../tenants.md), [permisos.md](../permisos.md), [panel-admin.md](../panel-admin.md).

## Qué hace

- **Autenticación de las tiendas:** login, registro de clientes, perfil propio y recupero de contraseña por mail.
  Emite los JWT, atados a la tienda.
- **Usuarios de la tienda:** el administrador los lista, crea, edita, borra y les da o saca roles.
- **Permisos por rol:** qué áreas del panel puede usar `EMPLEADO` y `CLIENTE` en cada tienda.
- **Panel de la tienda:** resumen de usuarios (total, activos, por rol, altas por período).
- **Plataforma:** alta de tiendas (autoservicio y superadmin), habilitar, deshabilitar y borrar tiendas, login de
  superadmins, su gestión y el panel de la plataforma (resumen y uso por tienda).
- **Para los demás servicios:** busca tiendas (por URL o schema) y roles de un usuario, y deja en Redis tiendas y
  roles para que no tengan que preguntarle cada vez.

### Conceptos

| Concepto | Qué es |
|---|---|
| Rol | `ADMINISTRADOR` (el dueño, tiene todo), `EMPLEADO`, `CLIENTE`. Un usuario puede tener varios. `SUPERADMIN` existe solo en `public` y no se asigna desde una tienda |
| Área | `PRODUCTOS`=1, `PRECIOS`=2, `STOCK`=4, `COMBOS`=8, `USUARIOS`=16. Los permisos de un rol son una máscara de bits (`5` = PRODUCTOS + STOCK) |
| Permisos por defecto | `ADMINISTRADOR`: todo (31, no se configura). `EMPLEADO`: `STOCK` (4). `CLIENTE`: nada (0). `USUARIOS` no se delega |
| Dueño de una tienda | Se crea con el alta de la tienda, con los roles `ADMINISTRADOR` y `EMPLEADO` |
| Borrado | Lógico (`deleted_at`): un usuario o rol borrado no aparece en ningún lado, pero su email sigue ocupado |
| Activo | Entró (login) en los últimos 30 días |

### Reglas de validación

Las mismas para registro, alta desde el panel, dueño de una tienda nueva, superadmins y cambios de perfil. El
frontend tiene una copia (`frontend/src/lib/validation.ts`): si cambian, cambiar las dos.

| Dato | Regla | Error (400) |
|---|---|---|
| Nombre | Obligatorio, hasta 100 caracteres (sin contar espacios de los bordes) | `The name is required and can have up to 100 characters.` |
| Email | `usuario@dominio.ext`, sin espacios, hasta 254 caracteres | `The email is not valid.` |
| Contraseña | 8 a 72 caracteres (72 bytes), con al menos una letra y un número | `The password must have 8 to 72 characters, with at least one letter and one number.` |
| Identificador de tienda | 3 a 30 caracteres: empieza con letra, minúsculas, números y guiones, no termina en guion (`^[a-z][a-z0-9-]{1,28}[a-z0-9]$`) | `The identifier must have 3 to 30 characters, start with a letter and only use lowercase letters, numbers and hyphens.` |
| Identificador reservado | `www`, `api`, `app`, `admin`, `platform`, `public`, `mail`, `static`, `assets`, `cdn`, `status`, `help`, `support`, `blog`, `dev`, `test`, `staging`, `root`, `information-schema` y todo lo que empieza con `pg-` | `The identifier is reserved.` |

El email de un usuario tiene que ser único **dentro de la tienda** (otro usuario en otra tienda puede usar el mismo).

## Flujos

### Login y uso del token

1. `POST /api/v1/auth/login` desde la tienda (con su `Origin`) → `{ name, id, email, token, refreshToken, roles }`.
   El email no distingue mayúsculas.
2. Mandar `Authorization: Bearer <token>` en cada request. El token dura **1 hora** y sirve solo en esa tienda.
3. `roles` trae cada rol con su máscara de permisos: el frontend arma el menú con eso (por ejemplo, mostrar
   "Stock" si algún rol tiene el bit 4).
4. Vencido: `POST /api/v1/auth/refresh` con el `refreshToken` (dura **7 días**) devuelve tokens nuevos sin pedir la
   contraseña. El frontend lo hace solo ante un 401 y repite la llamada.
5. Cada refresh se usa **una vez**: renovar devuelve uno nuevo y el viejo deja de servir a los 30 segundos (por si
   otra pestaña renueva a la vez). Se guardan en Redis (`refresh:<tenant>:<userId>:<jti>`, 7 días).
6. El refresh deja de servir si se cierra esa sesión (`POST /auth/logout`), si la contraseña cambia (lleva una huella
   de la contraseña) o si el usuario se borra. Con un refresh vencido o que ya no sirve, hay que volver a loguearse.

Cada token lleva su tipo en el claim `type` (`access`, `refresh` o `password`, ver `TokenConstants` en common): el
gateway solo deja entrar con el de sesión, y cada endpoint de users acepta solo el suyo.

Los superadmins hacen lo mismo con `POST /api/v1/platform/auth/login` desde el frontend de la plataforma (sin tienda).

### Alta de una tienda

`POST /api/v1/platform/tenants` (autoservicio, sin login) o `POST /api/v1/platform/admin/tenants` (superadmin):

1. Valida el identificador y los datos del dueño.
2. Crea el tenant en `public.tenant`: schema `mi_tienda` (los guiones pasan a `_`), URL
   `<platform.scheme>://mi-tienda.<platform.domain>` (`http://mi-tienda.localhost:2999`), habilitado.
3. Crea el schema de la tienda en la base `users` con sus tablas y el dueño con los roles `ADMINISTRADOR` y
   `EMPLEADO`. Si falla, se deshace el alta.
4. Publica el evento `tenant.created` (lo ve el panel de la plataforma en vivo).

products y sales crean su schema solos con la primera request de la tienda. Después del alta, el dueño entra en la
URL de su tienda con el email y la contraseña que cargó.

### Recupero de contraseña

1. `POST /api/v1/auth/forgot-password` con el email, desde la tienda. Responde siempre `true` (no dice si el email
   existe).
2. Si el usuario existe, users manda un mail con el link
   `<URL de la tienda>/reset-password?token=<token>`. El token dura **15 minutos**, es de esa tienda y sirve **una
   sola vez**: lleva una huella de la contraseña actual y deja de servir cuando la contraseña cambia. Es de tipo
   `password` (claim `type`): no sirve para entrar, y uno de sesión no sirve para cambiar la contraseña.
3. La página `/reset-password` **del frontend** lee el token, pide la contraseña nueva y llama a
   `POST /api/v1/auth/forgot-password/{token}` con `{ "newPassword": "..." }`.

En el frontend: "¿Olvidaste tu contraseña?" en el login lleva a `/recuperar`, que pide el mail. Solo en las tiendas: el
gateway no atiende `/api/v1/auth/**` sin tienda, así que la plataforma no tiene recupero.

Si el mail no se puede mandar, el error se ignora (no hay reintentos). El mail sale por Resend con el nombre de la
tienda como remitente y el dueño como `Reply-To`; ver [mail.md](../mail.md).

### Alta y baja de superadmins

El primer superadmin se crea al arrancar si están `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD`. Los demás los crea otro
superadmin con una contraseña temporal. Desactivar no borra: marca `deleted_at` en el usuario y en su rol, y se puede
reactivar. No se puede desactivar uno mismo ni al último activo.

## Autenticación

### POST /api/v1/auth/login

**Acceso:** Público · **Rate limit:** 5 por minuto

Loguea a un usuario de la tienda y devuelve el token.

```bash
curl -X POST http://localhost:3000/api/v1/auth/login \
  -H 'Origin: http://demo.localhost:2999' \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"demo1234"}'
```

Respuesta **200**:

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
  ]
}
```

| Código | Cuándo | Mensaje |
|---|---|---|
| 401 | Email o contraseña incorrectos (o el usuario está borrado) | `Invalid username or password.` |

Actualiza el último login (`lastLogin`), que usan los paneles para "activos".

### POST /api/v1/auth/refresh

**Acceso:** Público (con el refresh token) · **Rate limit:** el del login, 5 por minuto

Renueva la sesión: devuelve lo mismo que el login, con tokens nuevos (también el refresh) y los roles al día. No
actualiza el último login. El refresh que se manda deja de servir a los 30 segundos: hay que guardar el nuevo.

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

| Código | Cuándo | Mensaje |
|---|---|---|
| 401 | Token inválido, vencido, de otra tienda, de otro tipo, ya usado, de una sesión cerrada, de un usuario borrado o de antes de cambiar la contraseña | `The access token is invalid.` |

### POST /api/v1/auth/logout

**Acceso:** Público (con el refresh token)

Cierra la sesión: el refresh deja de servir. El token de 1 hora sigue valiendo hasta que vence (el gateway no los
guarda). Responde **204** siempre, aunque el refresh ya no sirviera, así el frontend sale igual.

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

### POST /api/v1/auth/register

**Acceso:** Público

Registra un cliente (rol `CLIENTE`) en la tienda y lo deja logueado.

```bash
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H 'Origin: http://demo.localhost:2999' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana Pérez","email":"ana@mail.com","password":"secreta123"}'
```

Respuesta **201**: igual que el login, con `roles: [{ "type": "CLIENTE", "permissions": 0 }]`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Nombre, email o contraseña inválidos | Ver [reglas](#reglas-de-validación) |
| 409 | Email usado en la tienda | `The email is already in use.` |

### GET /api/v1/auth/me

**Acceso:** Login

Los datos del usuario logueado.

```bash
curl http://localhost:3000/api/v1/auth/me \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Respuesta **200** (`FullUserDto`, el mismo formato que los usuarios del panel):

```json
{ "id": 1, "name": "Dueño Demo", "email": "admin@demo.local",
  "roles": [ { "type": "ADMINISTRADOR", "permissions": 31 } ],
  "avatar": "0b6f…e2.png" }
```

`avatar` es la foto de perfil (archivo en [`/users/avatars/`](#get-usersavatarsarchivo)) o `null`. Viene también
en el login, el refresh y los usuarios del panel.

| Código | Cuándo | Mensaje |
|---|---|---|
| 404 | El usuario se borró después de loguearse | `The user was not found.` |

### PATCH /api/v1/auth/me

**Acceso:** Login

Cambia el nombre o la contraseña propios (lo usa la página de ajustes del frontend). Los campos en `null` no se
cambian; el email no se edita.

```bash
curl -X PATCH http://localhost:3000/api/v1/auth/me \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana P.","password":null}'
```

Respuesta **200**: `FullUserDto`. Errores: 400 por [validación](#reglas-de-validación), 404
`The user was not found.`

### DELETE /api/v1/auth/me

**Acceso:** Login

El usuario se borra a sí mismo (borrado lógico, con sus roles). Lo usa la página de ajustes ("Eliminar mi
cuenta"). Respuesta **200** con el texto `The user has been deleted`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 404 | Usuario inexistente | `The user was not found.` |
| 409 | Es el último `ADMINISTRADOR` de la tienda | `The store needs at least one ADMINISTRADOR: the last one cannot be deleted or lose the role.` |

### POST /api/v1/auth/me/avatar

**Acceso:** Login (cualquier rol)

Sube la foto de perfil propia y reemplaza la anterior, cuyo archivo se borra. Multipart con el campo `file`: PNG, JPG,
WEBP o GIF de hasta 1 MB. El tipo se valida por el contenido, no por el nombre ni el `Content-Type`; no se aceptan
SVG porque pueden traer scripts.

```bash
curl -X POST http://localhost:3000/api/v1/auth/me/avatar \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -F 'file=@foto.png'
```

Respuesta **200**: `FullUserDto` con el `avatar` nuevo.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Sin archivo o vacío | `The photo file is required.` |
| 413 | Más de 1 MB | `The photo must be 1 MB or smaller.` |
| 415 | No es PNG, JPG, WEBP ni GIF | `Only JPG, PNG, WEBP or GIF photos are allowed.` |

### DELETE /api/v1/auth/me/avatar

**Acceso:** Login

Quita la foto propia y borra el archivo. Respuesta **200**: `FullUserDto` con `avatar: null` (también si no tenía).

### GET /users/avatars/{archivo}

**Acceso:** Público, sin tienda (el navegador no manda `Origin` al cargar un `<img>`), igual que las imágenes de
productos. Los nombres son aleatorios y cada foto nueva tiene uno nuevo, así que se sirven con caché de 30 días.

Las fotos están en disco en `avatar.path` (`uploads/users/avatars`), una carpeta para todas las tiendas. Los archivos
solo se tocan cuando el cambio en la base es definitivo: al confirmarse se borra la foto reemplazada y, si la
transacción falla, la que se acababa de guardar. Al borrar una cuenta (`DELETE /auth/me` o `DELETE /users/{id}`) se
borra también su foto.

### POST /api/v1/auth/forgot-password

**Acceso:** Público

Pide el mail de recupero. Ver [flujo](#recupero-de-contraseña).

```bash
curl -X POST http://localhost:3000/api/v1/auth/forgot-password \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com"}'
```

Respuesta **200**: `true`, exista o no el email.

### POST /api/v1/auth/forgot-password/{token}

**Acceso:** Público (con el token del mail)

Cambia la contraseña con el token del mail.

```bash
curl -X POST http://localhost:3000/api/v1/auth/forgot-password/eyJhbGciOi... \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"newPassword":"nueva1234"}'
```

Respuesta **200**: `FullUserDto` del usuario.

| Código | Cuándo | Mensaje |
|---|---|---|
| 401 | Token inválido, vencido, de otra tienda, de sesión o ya usado | `The access token is invalid.` |
| 400 | Contraseña inválida | `The password must have 8 to 72 characters, with at least one letter and one number.` |

## Usuarios de la tienda

Todo `/api/v1/users/**` es **solo `ADMINISTRADOR`** (el área `USUARIOS` no se delega).

### GET /api/v1/users

**Acceso:** ADMINISTRADOR

Listado paginado del panel, por nombre.

| Parámetro | Default | Qué hace |
|---|---|---|
| `q` | — | Busca en nombre y email |
| `role` | — | `ADMINISTRADOR`, `EMPLEADO` o `CLIENTE` (sin distinguir mayúsculas) |
| `page` | 0 | |
| `size` | 20 | Máximo 100 |

```bash
curl 'http://localhost:3000/api/v1/users?q=rios&role=CLIENTE&page=0&size=20' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Respuesta **200**:

```json
{
  "items": [
    { "id": 12, "name": "Valentina Ríos", "email": "valentina.rios@demo.local",
      "roles": [ { "type": "CLIENTE", "permissions": 0 } ] }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | `page` < 0 o `size` fuera de 1–100 | `Invalid page: page must be 0 or more and size between 1 and 100.` |
| 400 | Rol inexistente | `The role provided is invalid.` |

### POST /api/v1/users

**Acceso:** ADMINISTRADOR

Crea un usuario con los roles que se le pasen. Los roles inexistentes (o `SUPERADMIN`) se ignoran.

```bash
curl -X POST http://localhost:3000/api/v1/users \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Juan Gómez","email":"juan@demo.local","password":"empleado1","roles":["EMPLEADO"]}'
```

Respuesta **201**: `FullUserDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Datos inválidos | Ver [reglas](#reglas-de-validación) |
| 409 | Email usado en la tienda | `The email is already in use.` |

### GET /api/v1/users/{id}

**Acceso:** ADMINISTRADOR

Respuesta **200**: `FullUserDto`. Error: 404 `The user was not found.`

### PATCH /api/v1/users/{id}

**Acceso:** ADMINISTRADOR

Cambia el nombre o la contraseña de un usuario. Los campos en `null` no se cambian; el email no se puede cambiar.

```json
{ "name": "Juan G.", "password": "otra1234" }
```

Respuesta **200**: `FullUserDto`. Errores: 400 por [validación](#reglas-de-validación), 404
`The user was not found.`

### DELETE /api/v1/users/{id}

**Acceso:** ADMINISTRADOR

Borra (lógico) el usuario y sus roles, y saca sus roles de Redis. Respuesta **200** con el texto
`The user has been deleted`. Errores: 404 `The user was not found.` · 409 si es el último `ADMINISTRADOR` de la
tienda (mismo mensaje que `DELETE /auth/me`).

### GET /api/v1/users/{id}/roles

**Acceso:** ADMINISTRADOR

Respuesta **200**: `[ { "type": "EMPLEADO", "permissions": 4 } ]`.

### POST /api/v1/users/{id}/roles?roleName=

**Acceso:** ADMINISTRADOR

Le da un rol (`ADMINISTRADOR`, `EMPLEADO` o `CLIENTE`). Si ya lo tuvo y se lo sacaron, lo reactiva; si ya lo tiene,
no hace nada.

```bash
curl -X POST 'http://localhost:3000/api/v1/users/12/roles?roleName=EMPLEADO' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Respuesta **200**: `FullUserDto` con los roles nuevos.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Rol inexistente | `The role provided is invalid.` |
| 404 | Usuario inexistente | `The user was not found.` |

### DELETE /api/v1/users/{id}/roles?roleName=

**Acceso:** ADMINISTRADOR

Le saca un rol. Respuesta **200**: `FullUserDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Rol inexistente | `The role provided is invalid.` |
| 404 | Usuario inexistente o sin ese rol | `The user was not found.` |
| 409 | Se le saca `ADMINISTRADOR` al último administrador de la tienda | `The store needs at least one ADMINISTRADOR: the last one cannot be deleted or lose the role.` |

### GET /api/v1/users/dashboard

**Acceso:** ADMINISTRADOR

Panel de la tienda: usuarios, activos, por rol y altas por período. Siempre al día.

| Parámetro | Default | Qué hace |
|---|---|---|
| `from`, `to` | Últimos 30 días hasta hoy | `yyyy-MM-dd`, inclusive |
| `groupBy` | `DAY` | `DAY`, `WEEK` o `MONTH` |

Los días son en hora argentina (`users.dashboard.zone`): un alta del lunes a las 22 hs es del lunes aunque en UTC ya
sea martes.

```bash
curl 'http://localhost:3000/api/v1/users/dashboard?from=2026-10-01&to=2026-10-31&groupBy=WEEK' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Respuesta **200**:

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

- `byRole` trae los tres roles siempre; un usuario con varios roles cuenta en cada uno.
- `registrations` cuenta los usuarios que siguen en la tienda; `period` es el primer día del período.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Fecha mal formada o `groupBy` inválido | `Invalid value for from.` (o `to`, `groupBy`) |
| 400 | `from` > `to` o más de 1.000 períodos | `Invalid range: from must not be after to, with up to 1000 periods.` |

## Permisos por rol

### GET /api/v1/users/roles/permissions

**Acceso:** ADMINISTRADOR

Respuesta **200**:

```json
[
  { "role": "ADMINISTRADOR", "permissions": 31 },
  { "role": "EMPLEADO", "permissions": 4 },
  { "role": "CLIENTE", "permissions": 0 }
]
```

### PUT /api/v1/users/roles/{role}/permissions

**Acceso:** ADMINISTRADOR

Configura los permisos de `EMPLEADO` o `CLIENTE` en la tienda. Se aplica en hasta 1 minuto (caché de roles de los
servicios).

```bash
curl -X PUT http://localhost:3000/api/v1/users/roles/EMPLEADO/permissions \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"permissions":5}'
```

Respuesta **200**: `{ "role": "EMPLEADO", "permissions": 5 }`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Sin `permissions`, o con bits fuera de 1–8 (incluye `USUARIOS`=16) | `Invalid permissions: use PRODUCTOS=1, PRECIOS=2, STOCK=4 and COMBOS=8 (USUARIOS cannot be delegated).` |
| 400 | `ADMINISTRADOR` o `SUPERADMIN` | `Only the EMPLEADO and CLIENTE permissions can be configured.` |
| 404 | Rol inexistente | `The role provided is invalid.` |

## Plataforma

Rutas sin tienda: se llaman desde el frontend de la plataforma (`http://localhost:2999`). Si una request llega con
tienda, responden **404** `This endpoint is only available on the platform.` Las fechas van con offset.

### POST /api/v1/platform/tenants

**Acceso:** Público · **Rate limit:** 3 por minuto

Alta de una tienda por autoservicio. Ver [flujo](#alta-de-una-tienda).

```bash
curl -X POST http://localhost:3000/api/v1/platform/tenants \
  -H 'Origin: http://localhost:2999' -H 'Content-Type: application/json' \
  -d '{"identifier":"mi-tienda","ownerName":"Ana Pérez","ownerEmail":"ana@mail.com","ownerPassword":"secreta123"}'
```

Respuesta **201**:

```json
{
  "id": 7,
  "identifier": "mi-tienda",
  "schema": "mi_tienda",
  "url": "http://mi-tienda.localhost:2999",
  "configId": "3f1c6a2e-...",
  "enabled": true,
  "createdAt": "2026-11-03T13:00:00Z"
}
```

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Identificador inválido | `The identifier must have 3 to 30 characters, start with a letter and only use lowercase letters, numbers and hyphens.` |
| 400 | Identificador reservado | `The identifier is reserved.` |
| 400 | Falta un dato del dueño | `The owner name, email and password are required.` |
| 400 | Datos del dueño inválidos | Ver [reglas](#reglas-de-validación) |
| 409 | Identificador usado (también por una tienda borrada) | `The identifier is already in use.` |

### POST /api/v1/platform/auth/login

**Acceso:** Público · **Rate limit:** 5 por minuto

Login del superadmin. Mismo body y respuesta que [el de las tiendas](#post-apiv1authlogin), con
`roles: [{ "type": "SUPERADMIN", "permissions": 0 }]` y un token de `public`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 401 | Credenciales incorrectas, superadmin desactivado o usuario sin rol `SUPERADMIN` | `Invalid username or password.` |

### POST /api/v1/platform/auth/refresh

**Acceso:** Público (con el refresh token) · **Rate limit:** el del login

Igual que [el de las tiendas](#post-apiv1authrefresh), con un refresh de `public`. Un superadmin desactivado ya no
tiene el rol, así que no se renueva: 401 `The access token is invalid.`

### POST /api/v1/platform/auth/logout

**Acceso:** Público (con el refresh token)

Igual que [el de las tiendas](#post-apiv1authlogout), con un refresh de `public`. **204**.

### GET /api/v1/platform/admin/tenants

**Acceso:** SUPERADMIN

Las tiendas no borradas, de la más nueva a la más vieja. Respuesta **200**: lista de `PlatformTenantDto` (el
formato del alta).

### GET /api/v1/platform/admin/tenants/{id}

**Acceso:** SUPERADMIN

Respuesta **200**: `PlatformTenantDto`. Error: 404 `The tenant was not found.` (no existe o está borrada).

### POST /api/v1/platform/admin/tenants

**Acceso:** SUPERADMIN

Igual que el alta por autoservicio, pero sin rate limit de alta. Mismo body, respuesta y errores.

### PUT /api/v1/platform/admin/tenants/{id}/enabled

**Acceso:** SUPERADMIN

Habilita o deshabilita una tienda. Deshabilitada, el gateway responde 403 a todo lo de esa tienda (tarda hasta 1
minuto). Publica `tenant.enabled` o `tenant.disabled`.

```bash
curl -X PUT http://localhost:3000/api/v1/platform/admin/tenants/7/enabled \
  -H 'Origin: http://localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"enabled":false}'
```

Respuesta **200**: `PlatformTenantDto`. Error: 404 `The tenant was not found.`

### DELETE /api/v1/platform/admin/tenants/{id}

**Acceso:** SUPERADMIN

Borra (lógico) la tienda: los datos quedan, el gateway responde 404 y el identificador no se puede volver a usar.
Publica `tenant.deleted`. Respuesta **204**. Error: 404 `The tenant was not found.`

### GET /api/v1/platform/admin/dashboard

**Acceso:** SUPERADMIN

Tiendas por estado y altas por período (incluidas las borradas). Mismos parámetros y errores que
[el panel de la tienda](#get-apiv1usersdashboard).

```json
{
  "tenants": { "total": 42, "enabled": 39, "disabled": 3, "deleted": 5 },
  "registrations": { "from": "2026-10-04", "to": "2026-11-02", "groupBy": "DAY",
    "periods": [ { "period": "2026-10-04", "count": 1 } ] }
}
```

`total`, `enabled` y `disabled` son de las tiendas no borradas.

### GET /api/v1/platform/admin/usage?sort=

**Acceso:** SUPERADMIN

Usuarios, activos y última actividad de cada tienda no borrada, de mayor a menor. Se guarda 5 minutos en Redis.

| `sort` | Ordena por |
|---|---|
| `users` (default) | Usuarios |
| `activeUsers` | Activos (login en 30 días) |
| `lastActivity` | Última actividad (las tiendas sin actividad al final) |
| `createdAt` | Fecha de alta |

```json
[
  { "id": 7, "identifier": "mi-tienda", "schema": "mi_tienda", "enabled": true,
    "createdAt": "2026-10-01T10:00:00Z", "users": 14, "activeUsers": 9,
    "lastActivity": "2026-11-03T12:58:00Z" }
]
```

`lastActivity` es lo más reciente entre el último login y la última request (marcas del gateway, últimos 5
minutos); `null` si nadie entró. Los productos por tienda los da products (`/api/v1/platform/products/usage`): el
frontend junta las dos listas por `schema`.

Error: 400 `Invalid sort: use users, activeUsers, lastActivity or createdAt.`

### GET /api/v1/platform/admin/superadmins

**Acceso:** SUPERADMIN

Todos los superadmins, también los desactivados, por fecha de alta.

```json
[ { "id": 1, "name": "Superadmin", "email": "superadmin@kleinkauf.local", "active": true,
    "createdAt": "2026-10-01T10:00:00Z", "lastLogin": "2026-11-03T12:00:00Z" } ]
```

### POST /api/v1/platform/admin/superadmins

**Acceso:** SUPERADMIN

Crea un superadmin con contraseña temporal (no se le pide cambiarla).

```json
{ "name": "Lucía", "email": "lucia@kleinkauf.local", "password": "temporal123" }
```

Respuesta **201**: `SuperadminDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Datos inválidos o faltantes | Ver [reglas](#reglas-de-validación) |
| 409 | Email usado, también por un superadmin desactivado | `The email is already in use.` |

### PUT /api/v1/platform/admin/superadmins/{id}/active

**Acceso:** SUPERADMIN

Desactiva (`{"active": false}`) o reactiva (`true`) un superadmin. Desactivado no puede loguearse, y su token deja
de servir en hasta 1 minuto. Si ya estaba así no cambia nada.

Respuesta **200**: `SuperadminDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Desactivarse a sí mismo | `You cannot deactivate yourself.` |
| 409 | Es el último activo | `The last active superadmin cannot be deactivated.` |
| 404 | No existe o no es superadmin | `The superadmin was not found.` |

## Reportes de bugs

"Reportar un problema" del footer de las tiendas. Se guardan en `public.bug_reports` y los leen los superadmins en el
panel de la plataforma (`/bug-reports`).

### POST /api/v1/bug-reports

**Acceso:** Público, con login opcional (rate limit de 3 por minuto)

La tienda sale del `Origin` (el gateway la resuelve como en cualquier ruta de tienda). Con la sesión abierta queda el
usuario, y si no se manda `email` se usa el de su cuenta. `pageUrl` se recorta a 500 caracteres y el `User-Agent` a 300.

```json
{ "description": "El carrito no suma bien el envío", "email": "ana@mail.com", "pageUrl": "https://demo.kleinkauf.site/carrito" }
```

Respuesta **201** sin cuerpo.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Descripción de menos de 10 o más de 2000 caracteres | `The description must have 10 to 2000 characters.` |
| 400 | Email inválido | `The email is not valid.` |

### GET /api/v1/platform/admin/bug-reports?status=&page=&size=

**Acceso:** SUPERADMIN

Los más nuevos primero. `status`: `pending` (por defecto), `resolved` o `all`. `size`: 20 por defecto, máximo 100.
`pending` es cuántos quedan sin resolver en total, sea cual sea el filtro.

```json
{ "items": [ { "id": 4, "store": "demo", "storeUrl": "https://demo.kleinkauf.site", "userId": 7,
    "reporterName": "Ana", "email": "ana@mail.com", "description": "El carrito no suma bien el envío",
    "pageUrl": "https://demo.kleinkauf.site/carrito", "userAgent": "Mozilla/5.0 ...",
    "createdAt": "2026-11-04T12:30:00Z", "resolvedAt": null } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1, "pending": 1 }
```

Errores: 400 `Invalid value for status.` y 400 `Invalid page: page must be 0 or more and size between 1 and 100.`

### PUT /api/v1/platform/admin/bug-reports/{id}/resolved

**Acceso:** SUPERADMIN

Marca el reporte como resuelto (`{"resolved": true}`) o lo vuelve a pendiente (`false`). Marcar de nuevo uno resuelto
no cambia la fecha en que se resolvió.

Respuesta **200**: el reporte. **404** `The bug report was not found.`

## Internos

No los llama el frontend ni pasan por el gateway: los llaman los servicios directo por Eureka, con
`X-Internal-Token` (ver [common.md](common.md)). users pide el token en todo `/api/internal/**`.

| Método | Ruta | Quién lo usa | Qué devuelve |
|---|---|---|---|
| GET | `/api/internal/tenants/id?url=` | gateway | `TenantDto` de la tienda con esa URL, o vacío si no existe o está borrada |
| GET | `/api/internal/tenants/id?schema=` | products, sales | `TenantDto` por schema. Sin `url` ni `schema`: 400 `Falta el parametro url o schema` |
| GET | `/api/internal/tenants/cors?url=` | gateway | `true` si la URL es de una tienda no borrada (aunque esté deshabilitada) |
| GET | `/api/internal/tenants/all` | products y sales (migran los schemas al arrancar; sales también para el vencimiento) | Todas las tiendas no borradas |
| GET | `/api/internal/users/{id}` | observability (nombres para la auditoría) | `FullUserDto`; 404 `The user was not found.` |
| GET | `/api/internal/users/roles/{id}` | products, sales, observability | `[{ "type", "permissions" }]` del usuario en la tienda de `X-Forwarded-Host` (sin tienda: `public`) |
| POST | `/api/internal/mail/order-confirmation` | sales | Manda al comprador el mail de confirmación de un pedido online (`OrderConfirmationMailDto`). Una sola vez por pedido (marca en Redis `mail:order:<schema>:<número>`, 2 días). 204 aunque el mail falle; 400 si falta el número o el email es inválido |

`TenantDto`:

```json
{ "id": 7, "configId": "3f1c6a2e-...", "schema": "mi_tienda", "enable": true,
  "createdAt": "2026-10-01T10:00:00", "deletedAt": null }
```

## Cómo se comunica

- **Recibe** del gateway `X-Forwarded-Host` (la tienda; sin él trabaja en `public`) y `X-User-Id`. Arma los roles
  del usuario con la tabla `roles` y su máscara de permisos.
- **Redis** (lo leen el gateway, products, sales y observability):

| Key | Contenido | Dura | Se borra |
|---|---|---|---|
| `user:<id>:<schema>` (hash `roles`) | Roles del usuario con su máscara | 1 hora | Al cambiar sus roles, borrarlo o cambiar los permisos de la tienda |
| `tenant:<schema>` (hash `data`) | `TenantDto` | 1 hora | Al habilitar, deshabilitar o borrar la tienda |
| `tenant:url:<url>` (hash `data`) | `TenantDto` por URL (el gateway) | 1 hora | Igual |
| `gateway:cors:<url>` | URL permitida por CORS | 5 horas | Igual |
| `platform:usage:users` | Uso por tienda del panel de la plataforma | 5 minutos | — |
| `activity:<schema>:<id>` | Última request (la escribe el gateway; users solo la lee) | 5 minutos | — |

- **Eventos** (Redis Streams `kleinkauf:events`): `tenant.created`, `tenant.enabled`, `tenant.disabled`,
  `tenant.deleted`, con `{ id, identifier, schema }`.
- **Auditoría:** `User`, `Role`, `RolePermission` y `Tenant` son `@Audited` (altas, cambios y bajas van al outbox;
  el último login no cuenta). Desactivar y reactivar superadmins se registra a mano. Lo de tiendas y superadmins
  queda con tenant `public`. Ver [auditoria.md](../auditoria.md).
- **Mail:** SMTP (Resend) para el recupero de contraseña y la confirmación de compra. Ver [mail.md](../mail.md).
- **Llama a products** (`GET /api/v1/catalog/store`) para el nombre de la tienda que va de remitente en los mails.
  Si products no responde, usa el nombre derivado del identificador.

## Modelo de datos

Base `users`. Hibernate crea las tablas con `hbm2ddl` (sin Flyway). Los nombres de columnas sin `@Column` quedan en
minúsculas sin guion bajo (`lastlogin`, `userid`).

Al arrancar, `TenantDataSource` migra `public` y el schema de cada tienda no borrada (como products y sales), así un
cambio en las entidades llega a todas en el deploy. Una tienda nueva crea su schema en la primera conexión.

**En el schema de cada tienda:**

| Tabla | Columnas | Notas |
|---|---|---|
| `usuarios` | `id`, `name`, `email` (único), `password` (bcrypt), `created_at`, `lastlogin`, `deleted_at` | Borrado lógico |
| `roles` | `id`, `type`, `userid`, `deleted_at` | Único (`userid`, `type`); borrado lógico |
| `role_permissions` | `id`, `type` (único), `permissions` | Solo las filas que configuró el administrador; sin fila = permisos por defecto |

**En `public`:** `bug_reports` (`id`, `tenant_schema`, `user_id`, `reporter_name`, `email`, `description`,
`page_url`, `user_agent`, `created_at`, `resolved_at`), `tenant` (`id`, `schema`, `url`, `config_id`, `enable`, `created_at`, `deleted_at`) y los superadmins
en `usuarios` y `roles` con el rol `SUPERADMIN`. Ver [tenants.md](../tenants.md#datos-de-un-tenant-tabla-tenant-en-users-schema-public).

## Configuración

| Propiedad | Valor en desarrollo | Para qué |
|---|---|---|
| `server.port` / `management.server.port` | `3001` / `9001` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/users` | Base |
| `spring.data.redis.*` | `localhost:6379` | Caché y eventos |
| `jwt.secret` | — | Firma los JWT. Tiene que ser el mismo que el del gateway. **Cambiarlo fuera de desarrollo** |
| `api.internal.key` | — | Token interno. **Cambiarlo fuera de desarrollo** |
| `kleinkauf.tenant.required` | `false` | Sin tienda trabaja en `public` (plataforma e internos) |
| `users.dashboard.zone` | `America/Argentina/Buenos_Aires` | Zona de los días de los paneles (altas por período) |
| `platform.scheme`, `platform.domain` | `http`, `localhost:2999` | Arman la URL de cada tienda nueva y el link del recupero |
| `platform.superadmin.email` / `.password` | `${SUPERADMIN_EMAIL}` / `${SUPERADMIN_PASSWORD}` | Primer superadmin, al arrancar |
| `spring.mail.*` | `smtp.resend.com:587`, usuario `resend`, contraseña `${MAIL_PASSWORD}` | Servidor de mail (Resend). Sin `MAIL_PASSWORD` no sale ningún mail |
| `mail.from.address` | `${MAIL_FROM:no-reply@mail.kleinkauf.site}` | Dirección de todos los mails |
| `mail.from.platform-name` | `KleinKauf` | Remitente de los mails sin tienda |
| `products.url` | `http://products-microservice/` | Nombre de la tienda para el remitente |

Levantarlo solo: `cd backend/kleinkaufusers && ./gradlew bootRun` (con Postgres, Redis y Eureka arriba). Ver el
[README del proyecto](../../README.md#cómo-correrlo).

## Límites conocidos

- Cerrar sesión corta el refresh pero no el token de 1 hora, que sigue valiendo hasta que vence. No hay "cerrar
  todas mis sesiones" (cambiar la contraseña lo hace).
- Los refresh emitidos antes de guardarlos en Redis (sin `jti`) ya no sirven: esas sesiones vuelven a loguearse.
- Si el mail del recupero falla, queda en el log como `ERROR` (`MailService`) pero no se reintenta.
- Un rol inexistente responde 400 si viene como parámetro (`?roleName=`, `?role=`) y 404 si viene en la ruta
  (`/roles/{role}/permissions`).

## Para limpiar

Nada por ahora. El 2026-11-03 se borraron `GET /api/v1/users/all` y `GET /api/internal/users/all` (sin uso), y
`PUT /auth/update` y `DELETE /auth/delete` pasaron a `PATCH /auth/me` y `DELETE /auth/me`.
