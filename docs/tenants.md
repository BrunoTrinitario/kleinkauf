# Tenants

Cómo funciona el multi-tenant: qué es un tenant, cómo se reconoce en cada request, cómo se da de alta y cómo se
administra.

## Conceptos

- **Tenant:** un emprendimiento que usa la plataforma (una tienda). Sus datos están aislados de los demás.
- **Un schema por tenant:** cada tenant tiene su propio schema de Postgres en **cada** base: en `users` (sus
  usuarios y roles), en `products` (sus productos, stock, combos...) y en `sales` (sus pedidos, comprobantes y
  configuración de ventas). Los nombres de tabla son los mismos en todos los schemas.
- **Archivos por tenant:** los comprobantes de pago que guarda sales van a una carpeta por tenant
  (`uploads/sales/receipts/<schema>/<número de pedido>/`).
- **Schema `public`:** es la plataforma. En la base `users` guarda la tabla de **tenants** y los usuarios
  **superadmin**. No pertenece a ninguna tienda.
- **URL del tenant:** cada tenant tiene la URL de su frontend, `<scheme>://<identificador>.<platform.domain>`
  (en desarrollo `http://mitienda.localhost:2999`). **El gateway reconoce al tenant por esa URL**, que llega en el
  header `Origin` de cada request.

### Datos de un tenant (tabla `tenant` en `users`, schema `public`)

| Campo | Qué es |
|---|---|
| `id` | Id numérico |
| `schema` | Nombre del schema (`mi_tienda`). Único |
| `url` | URL del frontend (`http://mi-tienda.localhost:2999`). Se usa para reconocerlo |
| `config_id` | UUID de referencia a la configuración propia del tenant (se va a usar más adelante) |
| `enable` | Si está habilitado. Deshabilitado → el gateway responde 403 |
| `created_at` | Fecha de alta |
| `deleted_at` | Soft delete. Borrado → no existe para la app (404) |

## Cómo se reconoce el tenant en cada request

```
Navegador (Origin: http://mi-tienda.localhost:2999)
   │
   ▼
gateway
   1. ClientHeadersFilter   borra los headers internos que mande el cliente
                            (X-User-Id, X-Forwarded-Host, X-Open-Path, X-Tenant-Name)
   2. CorsConfig            CORS: orígenes de tenants, subdominios de la plataforma y el
                            frontend de la plataforma. Va antes de los filtros que cortan
                            para que sus 401/404/403/429 salgan con las cabeceras CORS
   3. RequestFilter         rate limit por IP y origen (ventanas de 1 minuto)
   4. MsFilter              /api/internal/**: valida el token interno
   5. TenantGatewayFilter   busca el tenant por el Origin:
                              caché local (1 min) → Redis tenant:url:<url> → users
                            no existe o borrado → 404 · deshabilitado → 403
                            agrega X-Forwarded-Host: <schema>
                            (las rutas /api/v1/platform/** no tienen tenant y se saltean)
   6. JwtFilter             rutas no públicas: valida el JWT y que su claim "tenant" sea
                            el schema de la request (o "public" en /api/v1/platform/**);
                            agrega X-User-Id. En POST /api/v1/sales/orders el token es
                            opcional, pero si viene se valida igual
   │
   ▼
users / products / sales
   TenantFilter (decor-common)   lee X-Forwarded-Host y lo deja en TenantContext
   Hibernate multi-tenant        TenantIdentifierResolver → schema del TenantContext
                                 DatabaseMultiTenantConnectionProvider → conexión con ese schema
```

- En **products** y **sales** el tenant es obligatorio (sin `X-Forwarded-Host` responde 404). En **users** es
  opcional (`decor.tenant.required=false`): sin tenant trabaja sobre `public` (plataforma y endpoints internos).
- Cuando sales llama a products (reservar stock, vendidos) le pasa el mismo `X-Forwarded-Host`: la reserva se hace en
  el schema de la tienda del pedido.
- Los microservicios **confían** en `X-Forwarded-Host` y `X-User-Id` porque solo los pone el gateway: por eso el
  gateway borra los que mande el cliente.

### Tokens atados al tenant

Los ids de usuario se repiten entre tenants (el usuario 1 de una tienda y el usuario 1 de otra son personas
distintas). Por eso:

- El **JWT** que emite users lleva el claim `tenant` con el schema donde se logueó (`public` para el superadmin).
- El gateway rechaza con **401** un token cuyo `tenant` no coincide con el de la request.
- El **token de reset de contraseña** también lleva el tenant, y users lo valida al usarlo.

## Alta de un tenant

Toda la lógica está en **decorusers** (`/api/v1/platform`).

### Autoservicio (público)

```http
POST /api/v1/platform/tenants
Content-Type: application/json

{
  "identifier": "mi-tienda",
  "ownerName": "Ana",
  "ownerEmail": "ana@mail.com",
  "ownerPassword": "secreta"
}
```

```json
{
  "id": 7,
  "identifier": "mi-tienda",
  "schema": "mi_tienda",
  "url": "http://mi-tienda.localhost:2999",
  "configId": "4f6c1c1e-...",
  "enabled": true,
  "createdAt": "2026-11-01T10:00:00"
}
```

Qué hace:

1. **Valida el identificador:** 3 a 30 caracteres, empieza con letra, solo `a-z`, `0-9` y `-`, no termina en `-`.
   No puede ser reservado (`www`, `api`, `app`, `admin`, `platform`, `public`, `mail`, `static`, `assets`, `cdn`,
   `status`, `help`, `support`, `blog`, `dev`, `test`, `staging`, `root`, `information-schema`) ni empezar con `pg-`.
   Esta validación estricta es lo que permite usarlo en `CREATE SCHEMA` sin riesgo de inyección SQL.
2. **Valida al dueño:** nombre, email y contraseña obligatorios.
3. **Verifica que no exista:** el schema (`-` pasa a `_`) no puede estar usado, **ni siquiera por un tenant borrado**.
4. **Crea el tenant** habilitado, con su URL y un `configId` (UUID).
5. **Crea al dueño** como `ADMINISTRADOR` y `EMPLEADO` dentro del schema nuevo (`TenantOwnerCreator`). Al abrir la
   conexión, users crea el schema y sus tablas.
6. Si falla la creación del dueño, **se deshace el alta** (sin dueño nadie podría administrar el tenant).

El frontend redirige a la URL del tenant y el dueño inicia sesión ahí (su token queda atado a ese tenant).

| Caso | Respuesta |
|---|---|
| Alta correcta | 201 con el tenant |
| Identificador inválido, reservado o faltan datos del dueño | 400 |
| Identificador en uso (también si dos altas llegan al mismo tiempo) | 409 |
| Más de 3 altas por minuto desde la misma IP | 429 |

### Schemas

- **users:** el schema se crea al dar de alta (al guardar al dueño).
- **products:** el schema se crea solo la primera vez que llega una request del tenant. Al arrancar, products también
  migra los schemas de todos los tenants existentes (y completa los slugs de los productos que no tengan).
- **sales:** igual que products (se crea con la primera request y se migran todos al arrancar).
- Las tablas se crean y actualizan con Hibernate (`hbm2ddl.auto=update`), por schema.

### Tareas sin request

El vencimiento de pedidos de sales (`ExpirationJob`, cada 1 hora) no tiene una request que traiga el tenant: pide la
lista de tenants a users (Redis → users) y recorre uno por uno, poniendo cada schema en el `TenantContext` mientras lo
procesa. Saltea los borrados; los deshabilitados se siguen procesando, así su stock reservado no queda trabado.

## Superadmin y panel de la plataforma

- Usuario con rol `SUPERADMIN` en el schema `public`. **No se puede asignar desde una tienda.**
- El primero se crea al arrancar users si están las variables `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD` y ese
  usuario todavía no existe.
- Se loguea en `POST /api/v1/platform/auth/login` (token con `tenant = "public"`). Un usuario sin rol `SUPERADMIN`
  recibe 401.

| Endpoint | Qué hace |
|---|---|
| `GET /api/v1/platform/admin/tenants` | Lista de tenants (sin los borrados), del más nuevo al más viejo |
| `GET /api/v1/platform/admin/tenants/{id}` | Detalle |
| `POST /api/v1/platform/admin/tenants` | Alta (igual que el autoservicio) |
| `PUT /api/v1/platform/admin/tenants/{id}/enabled` | `{"enabled": false}` deshabilita; `true` habilita |
| `DELETE /api/v1/platform/admin/tenants/{id}` | Soft delete (204) |

El gateway reconoce las rutas de plataforma por la ruta (`/api/v1/platform/**`) y nunca les agrega tenant. Como
defensa extra, users verifica además del rol que la request **no** traiga tenant: si llegara con uno, responde 404.

## Estados de un tenant

| Estado | Qué pasa en el gateway | Datos |
|---|---|---|
| Habilitado | Funciona normalmente | — |
| Deshabilitado | **403** "The tenant is disabled." en todas sus requests | Intactos; se puede volver a habilitar |
| Borrado (soft delete) | **404** "The tenant was not found.", como si no existiera | El schema y los datos quedan en la base; el identificador queda reservado |

Al deshabilitar o borrar, users borra de Redis las claves de ese tenant. El gateway tiene además un caché local de
1 minuto, así que **el cambio tarda hasta 1 minuto** en aplicarse.

Con el tenant deshabilitado o borrado nadie puede entrar a sus pedidos (el gateway corta antes). Los pedidos que
estaban esperando el pago igual vencen y devuelven su stock.

## Cachés de tenants

| Dónde | Clave | Qué guarda | Duración |
|---|---|---|---|
| Redis (escribe users) | `tenant:<schema>` | Datos del tenant | 1 hora |
| Redis (escribe users) | `tenant:url:<url>` | Datos del tenant, por URL (lo lee el gateway) | 1 hora |
| Redis (escribe users) | `gateway:cors:<url>` | Que la URL es de un tenant (CORS) | 5 horas |
| Gateway, local | `tenant:url:<url>`, `cors:<url>` | Lo mismo, en memoria | 1 minuto |
| products, local | `tenant:<schema>` | Datos del tenant | 10 minutos |
| sales, local | `tenant:<schema>` | Datos del tenant | 10 minutos |

## Configuración

| Servicio | Propiedad | Para qué | Valor en dev |
|---|---|---|---|
| users | `platform.scheme` | Esquema de la URL de los tenants | `http` |
| users | `platform.domain` | Dominio base de los subdominios | `localhost:2999` |
| users | `platform.superadmin.email` / `.password` | Primer superadmin (`SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`) | vacío (no se crea) |
| users | `decor.tenant.required` | Tenant opcional en users | `false` |
| gateway | `platform.url` | Frontend de la plataforma (alta de tenants, panel), permitido por CORS | `http://localhost:2999` |
| gateway | `limit.signup` | Altas de tenants por minuto por IP | `3` |

## Limitaciones conocidas

- **Rate limit por `X-Forwarded-For`:** la IP se toma de ese header, que el cliente puede falsificar. Si el gateway
  queda expuesto directo a internet, se puede esquivar el límite de altas.
- **users migra el schema en cada conexión** de un tenant (levanta un `EntityManagerFactory` para `hbm2ddl update`).
  Funciona, pero es costoso; products solo lo hace si el schema no existe.
- **Tenants creados a mano** antes del alta siguen funcionando con la `url` que tengan cargada; en el panel su
  identificador se deriva del schema.
- **Tokens anteriores** al alta de tenants no tienen el claim `tenant`: hay que volver a iniciar sesión.
- **Zona horaria única:** los reportes de ventas cortan los días en una sola zona para todas las tiendas
  (`sales.reports.zone`, Argentina). Una zona por tienda está anotada para después en [ventas.md](ventas.md).
