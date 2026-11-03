# Microservicios y endpoints

Qué hace cada microservicio y la lista completa de endpoints. Para el detalle de los tenants y de los permisos ver
[tenants.md](tenants.md) y [permisos.md](permisos.md).

## Arquitectura

```
                    Frontend de cada tienda (http://<tienda>.localhost:2999)
                    Frontend de la plataforma (http://localhost:2999)
                                       │
                                       ▼
                            ┌──────────────────────┐
                            │  gateway  :3000      │  única entrada pública
                            └──────────┬───────────┘
                         Eureka (lb://) │
          ┌─────────────────────────────┼─────────────────────────────┐
          ▼                             ▼                             ▼
┌──────────────────┐  tokens  ┌──────────────────┐  tokens  ┌───────────────────┐
│ decorusers :3001 │ ◄─────── │ products  :3002  │ ◄─────── │ decor-sales :3003 │
└────────┬─────────┘ internos └────────┬─────────┘ internos └─────────┬─────────┘
         │  ▲        (Resilience4j)    │      (stock y vendidos)      │
         │  └─────────────────── roles y tenants ─────────────────────┤
         │                             │                              │
 Postgres "users"     Redis    Postgres "products"            Postgres "sales"
 (schema por tenant + public)  (schema por tenant)            (schema por tenant)

          decor-observability :3004  ─  salud, métricas, eventos, auditoría y WS (Postgres "observability")
          eureka :8761  ─  registro de servicios
          decor-common  ─  librería compartida (no es un servicio)
```

Todos los endpoints se llaman a través del **gateway** (`http://localhost:3000` en desarrollo). El gateway reconoce la
tienda por el header `Origin`, así que las llamadas tienen que salir desde la URL de la tienda (o mandar ese `Origin`).

## Microservicios

### eureka (`:8761`)

Registro de servicios (Spring Cloud Netflix Eureka). gateway, users, products, sales y observability se registran y
se encuentran por nombre (`lb://users-microservice`, `lb://products-microservice`, `lb://sales-microservice`,
`lb://observability-microservice`). No tiene endpoints propios de la app.

Todos los servicios tienen además `/health`, `/ready`, `/metrics` y `/status` en un **puerto de gestión** interno
(el del servicio + 6000) que el gateway no expone. Ver [observabilidad.md](observabilidad.md).

### gateway (`:3000`, `gateway-microservice`)

Spring Cloud Gateway (WebFlux, reactivo). No tiene endpoints propios: enruta y protege.

- **Enrutamiento:** `/api/v1/products/**`, `/api/v1/bundles/**`, `/api/v1/catalog/**`, `/products/images/**` y
  `/api/v1/platform/products/**` → products; `/api/v1/users/**`, `/api/v1/auth/**`, el resto de `/api/v1/platform/**`
  y `/api/internal/tenants/**` → users;
  `/api/v1/sales/**` → sales; `/api/v1/platform/observability/**`, `/api/v1/observability/**` y el WebSocket `/ws`
  → observability.
- **Tenant:** lo reconoce por el `Origin`, responde 404 si no existe o está borrado y 403 si está deshabilitado, y le
  pasa el schema a los servicios en `X-Forwarded-Host`.
- **Autenticación:** valida el JWT en las rutas no públicas (y que sea del tenant de la request) y pasa el usuario en
  `X-User-Id`. `/ws` no lleva JWT: se entra con un ticket de un solo uso. En `POST /api/v1/sales/orders` el login es opcional: si viene un token válido pasa el usuario; si es
  inválido, 401.
- **Observabilidad:** marca la última actividad de cada usuario en Redis y pasa la IP de la conexión en
  `X-Client-Ip` (para la auditoría).
- **Seguridad:** borra los headers internos que mande el cliente; CORS solo para orígenes de tenants y de la
  plataforma; rate limit por IP y origen (login 5/min, alta de tenants 3/min, crear pedidos 5/min, consultar o
  cancelar un pedido 10/min, subir comprobantes 5/min, resto 300/min, sin contar imágenes).
- **Resiliencia:** las consultas a users (tenants y CORS) tienen circuit breaker, reintentos y timeout.

### decorusers (`:3001`, `users-microservice`)

Usuarios, autenticación y tenants. Base `users`.

- Login, registro, perfil y recupero de contraseña (con mail).
- Gestión de usuarios y roles de la tienda (`ADMINISTRADOR`, `EMPLEADO`, `CLIENTE`) y estadísticas.
- Permisos por área de cada rol (`role_permissions`).
- Plataforma: alta de tenants (autoservicio), superadmin y panel de tenants.
- Endpoints internos para que los demás servicios consulten tenants y roles.
- Emite los JWT (atados al tenant) y guarda en Redis tenants y roles.

### products (`:3002`, `products-microservice`)

Catálogo y operación de la tienda. Base `products`.

- Productos con imágenes, metadatos, precio, costo, destacados y slug.
- Movimientos de stock con alerta de stock bajo.
- Combos (bundles) con precio propio, precio de lista y disponibilidad.
- Importar y exportar productos en CSV.
- Catálogo público paginado (productos y combos, orden por más vendidos), con caché en Redis.
- Reservas de stock y contador de vendidos para los pedidos de sales (endpoints internos, idempotentes por número de
  pedido).
- Consulta tenants y roles a users (con circuit breaker, reintentos y caché).

### decor-sales (`:3003`, `sales-microservice`)

Ventas de la tienda online y del local. Base `sales`. Detalle completo en [ventas.md](ventas.md).

- Checkout: pedidos de invitados o usuarios logueados, con reserva de stock en products y aviso si cambió un precio.
- Pago por transferencia: el comprador sube el comprobante y el administrador lo valida o lo rechaza.
- Estados del pedido con historial, cancelación del comprador y vencimiento automático cada hora.
- Ventas del local cargadas por el administrador (precio cambiado, descuento, medio de pago, ajuste si falta stock).
- Reportes por período y ranking de productos, por canal y medio de pago.
- Llama a products (stock y vendidos) y a users (roles y tenants) con circuit breaker y reintentos.

### decor-observability (`:3004`, `observability-microservice`)

Salud, métricas, eventos y auditoría. Base `observability` (un schema por tienda y `public`). Detalle en
[observabilidad.md](observabilidad.md) y [auditoria.md](auditoria.md).

- Lee cada 10 s `/ready` y `/metrics` de cada instancia y arma la salud y el tráfico por servicio.
- Guarda la auditoría que mandan users, products y sales (Redis Streams) y la deja consultar.
- WebSocket `/ws` con ticket corto para los paneles de la plataforma y de la tienda en tiempo real.
- Consulta roles y nombres a users (con circuit breaker y reintentos).

### decor-common (librería)

No es un servicio: es un build compuesto de Gradle que usan todos. Trae DTOs (`TenantDto`, `RoleDto`, los de reservas
de stock entre sales y products), headers, Redis, caché Caffeine, tokens internos (`InternalTokenService`), áreas de
permisos (`PermissionArea`) y, para users, products y sales, el multi-tenant de Hibernate, el `TenantFilter`,
`CustomUserDetails` y el manejo de errores. También la configuración común de Actuator, trazas y logs, el
publicador de eventos y la captura de auditoría (`@Audited`, outbox).

## Convenciones

**Acceso** (columna de las tablas):

| Valor | Significa |
|---|---|
| Público | Sin token |
| Público (email) | Sin token; pide el email del pedido (sales). Si no coincide responde 404 |
| Login | Cualquier usuario logueado en la tienda (`Authorization: Bearer <token>`) |
| `PRODUCTOS`, `PRECIOS`, `STOCK`, `COMBOS` | Permiso del área (el `ADMINISTRADOR` los tiene todos). Ver [permisos.md](permisos.md) |
| ADMINISTRADOR | Rol administrador de la tienda |
| SUPERADMIN | Superadmin de la plataforma (sin tenant) |
| Interno | Solo entre servicios, con token interno (`X-internal-request: true` y `X-Internal-Token`) |

**Errores:** en general la respuesta es el código HTTP con el mensaje en texto (en inglés). La importación de CSV
devuelve JSON con los errores por fila y crear un pedido con precios viejos devuelve JSON con los precios nuevos. Un
archivo más grande que el límite de subida responde 413.

## decorusers

### Autenticación — `/api/v1/auth`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Público | Login en la tienda. Body `{email, password}`. Devuelve `{name, id, email, token, roles}` (roles con su máscara de permisos) |
| POST | `/api/v1/auth/register` | Público | Registro como `CLIENTE` y login. Body `{name, email, password}` |
| GET | `/api/v1/auth/me` | Login | Datos del usuario logueado y sus roles |
| PUT | `/api/v1/auth/update` | Login | Cambia su nombre o contraseña. Body `{name, password}` |
| DELETE | `/api/v1/auth/delete` | Login | Borra su propia cuenta |
| POST | `/api/v1/auth/forgot-password` | Público | Pide el mail de recupero. Body `{email}` |
| POST | `/api/v1/auth/forgot-password/{token}` | Público | Cambia la contraseña con el token del mail (válido 15 min y solo en su tienda). Body `{newPassword}` |

### Usuarios de la tienda — `/api/v1/users`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/users?q&role&page&size` | ADMINISTRADOR | Usuarios paginados (20 por página, máximo 100), ordenados por nombre. `q` busca en nombre y email; `role` = `ADMINISTRADOR`, `EMPLEADO` o `CLIENTE`. Devuelve `{items, page, size, totalItems, totalPages}`; cada usuario con sus roles. Página o rol inválidos: 400 |
| GET | `/api/v1/users/all` | ADMINISTRADOR | Lista completa de usuarios con sus roles (sin paginar; el panel usa el listado paginado) |
| POST | `/api/v1/users` | ADMINISTRADOR | Crea un usuario. Body `{name, email, password, roles: ["EMPLEADO"]}` |
| GET | `/api/v1/users/{id}` | ADMINISTRADOR | Detalle de un usuario |
| PATCH | `/api/v1/users/{id}` | ADMINISTRADOR | Cambia nombre o contraseña. Body `{name, password}` |
| DELETE | `/api/v1/users/{id}` | ADMINISTRADOR | Borra un usuario |
| GET | `/api/v1/users/{id}/roles` | ADMINISTRADOR | Roles del usuario |
| POST | `/api/v1/users/{id}/roles?roleName=EMPLEADO` | ADMINISTRADOR | Le asigna un rol (`SUPERADMIN` no se puede asignar) |
| DELETE | `/api/v1/users/{id}/roles?roleName=EMPLEADO` | ADMINISTRADOR | Le quita un rol |
| GET | `/api/v1/users/stats?startDate&endDate` | ADMINISTRADOR | Estadísticas: total, activos y registros por período |

### Permisos por rol — `/api/v1/users/roles`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/users/roles/permissions` | ADMINISTRADOR | Máscara de permisos de cada rol |
| PUT | `/api/v1/users/roles/{EMPLEADO\|CLIENTE}/permissions` | ADMINISTRADOR | Configura la máscara. Body `{"permissions": 5}` |

### Plataforma — `/api/v1/platform` (sin tenant)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/platform/tenants` | Público | Alta de un tenant con su dueño. Body `{identifier, ownerName, ownerEmail, ownerPassword}` |
| POST | `/api/v1/platform/auth/login` | Público | Login del superadmin. Body `{email, password}` |
| GET | `/api/v1/platform/admin/tenants` | SUPERADMIN | Lista de tenants (sin borrados) |
| GET | `/api/v1/platform/admin/tenants/{id}` | SUPERADMIN | Detalle de un tenant |
| POST | `/api/v1/platform/admin/tenants` | SUPERADMIN | Alta de un tenant |
| PUT | `/api/v1/platform/admin/tenants/{id}/enabled` | SUPERADMIN | Habilita o deshabilita. Body `{"enabled": false}` |
| DELETE | `/api/v1/platform/admin/tenants/{id}` | SUPERADMIN | Soft delete del tenant |
| GET | `/api/v1/platform/admin/dashboard?from&to&groupBy` | SUPERADMIN | Tenants por estado y altas por período (`DAY`/`WEEK`/`MONTH`). Ver [panel-admin.md](panel-admin.md) |
| GET | `/api/v1/platform/admin/usage?sort=` | SUPERADMIN | Usuarios, activos (30 días) y último login por tenant. `sort`: `users`, `activeUsers`, `lastActivity`, `createdAt` |
| GET | `/api/v1/platform/admin/superadmins` | SUPERADMIN | Superadmins, incluidos los desactivados |
| POST | `/api/v1/platform/admin/superadmins` | SUPERADMIN | Alta con contraseña temporal. Body `{name, email, password}` |
| PUT | `/api/v1/platform/admin/superadmins/{id}/active` | SUPERADMIN | Desactiva o reactiva. Body `{"active": false}`. No a sí mismo ni al último activo |

### Internos — `/api/internal`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/internal/tenants/all` | Interno | Todos los tenants (sin borrados). Lo usan products y sales al arrancar (sales también en el vencimiento) |
| GET | `/api/internal/tenants/id?url=...` o `?schema=...` | Interno | Un tenant por URL (gateway) o por schema (products y sales) |
| GET | `/api/internal/tenants/cors?url=...` | Interno | Si la URL es de un tenant (CORS del gateway) |
| GET | `/api/internal/users/all` | Interno | Todos los usuarios |
| GET | `/api/internal/users/{id}` | Interno | Un usuario |
| GET | `/api/internal/users/roles/{id}` | Interno | Roles de un usuario con sus permisos (lo usan products y sales) |

## products

### Productos — `/api/v1/products`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/products?q&category&page&size` | Cualquier área del panel (`PRODUCTOS`, `PRECIOS`, `STOCK` o `COMBOS`) | Productos del panel paginados (20 por página, máximo 100), ordenados por nombre, con stock exacto. `q` busca en nombre, descripción y categoría. Devuelve `{items, page, size, totalItems, totalPages}`. Página inválida: 400 |
| GET | `/api/v1/products/all` | Cualquier área del panel | Todos los productos con stock exacto, sin paginar. Lo reemplaza el listado paginado; queda por compatibilidad. Ya no es público: el catálogo usa `/api/v1/catalog` |
| GET | `/api/v1/products/featured` | Público | Destacados, por su orden |
| GET | `/api/v1/products/{id}` | Público | Detalle con metadatos e imágenes |
| GET | `/api/v1/products/search?name=...` | Público | Producto por nombre exacto |
| GET | `/api/v1/products/{id}/metadata` | Público | Metadatos (material, color, estilo, medidas) |
| GET | `/api/v1/products/{id}/images` | Login | Imágenes del producto |
| POST | `/api/v1/products` | `PRODUCTOS` | Crea un producto. Body `{name, category, description, stock, minStock, price, cost}`. Sin `PRECIOS`, precio y costo se ignoran. El stock inicial queda como movimiento de entrada |
| PUT | `/api/v1/products/{id}` | `PRODUCTOS` | Edita nombre, categoría, descripción y stock mínimo (el stock no: ver movimientos) |
| DELETE | `/api/v1/products/{id}` | `PRODUCTOS` | Borra el producto (soft delete) y sus imágenes |
| POST | `/api/v1/products/{id}/images` | `PRODUCTOS` | Sube imágenes (multipart `files`, hasta 1 MB cada una) |
| DELETE | `/api/v1/products/images/{imageId}` | `PRODUCTOS` | Borra una imagen |
| PUT | `/api/v1/products/{id}/metadata` | `PRODUCTOS` | Edita los metadatos |
| PUT | `/api/v1/products/{id}/featured` | `PRODUCTOS` | Marca o desmarca como destacado. Body `{"featured": true, "order": 1}` |
| GET | `/api/v1/products/{id}/pricing` | `PRECIOS` | Precio, costo, margen, margen % y unidades vendidas (`soldCount`) |
| PUT | `/api/v1/products/{id}/pricing` | `PRECIOS` | Cambia precio y costo. Body `{price, cost}` |

### Stock — `/api/v1/products`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/products/{id}/stock/movements` | `STOCK` | Registra un movimiento. Body `{type, quantity, reason}`: `ENTRADA`/`SALIDA` con la cantidad, `AJUSTE` con el stock contado (motivo obligatorio). Sin stock suficiente → 409 |
| GET | `/api/v1/products/{id}/stock/movements` | `STOCK` | Historial, del más nuevo al más viejo |
| GET | `/api/v1/products/stock/low` | `STOCK` | Productos con stock menor o igual a su mínimo |

### Importar y exportar — `/api/v1/products`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/products/export` | `PRODUCTOS` y `PRECIOS` | Descarga `productos.csv` (`;`, UTF-8 con BOM, números `15000,50`) |
| GET | `/api/v1/products/import/template` | `PRODUCTOS` y `PRECIOS` | Descarga la plantilla (solo encabezados) |
| POST | `/api/v1/products/import` | `PRODUCTOS` y `PRECIOS` | Sube el CSV (multipart `file`, hasta 5.000 filas y 5 MB). Crea o actualiza por nombre; todo o nada. 200 `{created, updated, errors: []}` o 400 con los errores por fila |

Columnas: `nombre;categoria;descripcion;precio;costo;material;color;estilo;alto;ancho;largo;peso` (solo `nombre` es
obligatoria; una celda vacía no cambia el dato).

### Combos — `/api/v1/bundles`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/bundles/all` | Público | Todos los combos con precio, precio de lista (`listPrice`) y cuántos se pueden armar (`available`) |
| GET | `/api/v1/bundles/{id}` | Público | Un combo |
| POST | `/api/v1/bundles` | `COMBOS` | Crea un combo. Body `{name, enabled, price}` |
| PUT | `/api/v1/bundles/{id}` | `COMBOS` | Habilita o deshabilita (alterna) |
| PUT | `/api/v1/bundles/{id}/price` | `COMBOS` | Cambia el precio. Body `{price}` |
| PUT | `/api/v1/bundles/{id}/product` | `COMBOS` | Agrega o actualiza un producto del combo. Body `{productId, quantity}` |
| PUT | `/api/v1/bundles/{id}/product/bulk` | `COMBOS` | Lo mismo para varios productos (lista) |
| DELETE | `/api/v1/bundles/{id}/product` | `COMBOS` | Quita un producto del combo. Body `{productId}` |
| DELETE | `/api/v1/bundles/{id}` | `COMBOS` | Borra el combo |

### Catálogo público — `/api/v1/catalog`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/catalog/products?q&category&sort&page&size` | Público | Productos paginados (24 por página, máximo 100). `q` busca en nombre, descripción y categoría; `sort` = `name`, `price_asc`, `price_desc` (sin precio, al final) o `best_sellers` (más vendidos, sin mostrar el número). Devuelve `{items, page, size, totalItems, totalPages}` |
| GET | `/api/v1/catalog/products/{slug}` | Público | Detalle por slug (para los links compartidos) |
| GET | `/api/v1/catalog/categories` | Público | Categorías con su cantidad de productos |
| GET | `/api/v1/catalog/bundles` | Público | Combos habilitados, con precio y todos sus productos vigentes: precio, precio de lista, disponibilidad, imagen y productos |

El catálogo no muestra el stock exacto (`availability`: `DISPONIBLE`, `POCAS_UNIDADES` o `AGOTADO`) ni el costo. Un
producto sin precio viene con `price: null` ("consultar"). Cada producto y combo trae su `id`, que es lo que usa el
carrito para armar el pedido. Las respuestas se cachean en Redis por tenant y se invalidan con cada cambio de
productos, combos, stock o vendidos.

### Imágenes

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/products/images/{archivo}` | Público | Archivo de imagen de un producto |

### Plataforma — `/api/v1/platform/products` (sin tenant)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/platform/products/usage?sort=` | SUPERADMIN | Productos y combos por schema (`sort`: `products` o `bundles`). Se junta con `/platform/admin/usage` de users. Ver [panel-admin.md](panel-admin.md) |

### Internos — `/api/internal/products`

Los usa sales. Todos son idempotentes por número de pedido (`reference`): reintentar no mueve el stock ni el contador
dos veces.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/internal/products/reservations` | Interno | Reserva el stock de un pedido. Body `{reference, items: [{type, id, quantity}], local}`. Todo o nada: sin stock, sin precio o no disponible → 409. Con `local: true` acepta ítems sin precio y, si falta stock, hace un `AJUSTE` y lo informa en `shortages`. Devuelve nombre, precio y costo de cada ítem |
| POST | `/api/internal/products/reservations/{reference}/release` | Interno | Devuelve todo el stock que salió con ese pedido. Si la reserva todavía no llegó, la deja anulada |
| POST | `/api/internal/products/reservations/{reference}/sold` | Interno | Suma los ítems al contador de vendidos. Body `{items}` |
| POST | `/api/internal/products/reservations/{reference}/unsold` | Interno | Los resta |

## decor-sales

Reglas, estados y ejemplos en [ventas.md](ventas.md).

### Checkout — `/api/v1/sales`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/v1/sales/checkout` | Público | Datos para transferir, plazo para pagar, tipos de entrega y topes del pedido |
| POST | `/api/v1/sales/orders` | Público (login opcional) | Crea el pedido y reserva el stock. Body `{items: [{type, id, quantity, price}], buyer, deliveryType, address, notes}`. 201 con número, total, vencimiento y datos para transferir. Precio cambiado → 409 con los precios nuevos |
| POST | `/api/v1/sales/orders/lookup` | Público (email) | El pedido. Body `{email, number}` |
| POST | `/api/v1/sales/orders/{number}/receipt` | Público (email) | Sube el comprobante (multipart `file` JPG/PNG/PDF hasta 5 MB y `email`; hasta 5 por pedido) → `PAGO_EN_REVISION` |
| POST | `/api/v1/sales/orders/{number}/cancel` | Público (email) | Cancela si todavía no se validó el pago. Body `{email}` |
| GET | `/api/v1/sales/orders/mine?page&size` | Login | Pedidos del usuario logueado, paginados |

### Panel de ventas — `/api/v1/sales/admin`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/sales/admin/orders` | ADMINISTRADOR | Venta del local ya cobrada. Body `{items: [{type, id, quantity, price}], discount, paymentMethod, status, buyer, deliveryType, address, notes}` |
| GET | `/api/v1/sales/admin/orders?status&channel&paymentMethod&stockReview&from&to&q&page&size` | ADMINISTRADOR | Lista paginada de pedidos con filtros |
| GET | `/api/v1/sales/admin/orders/{number}` | ADMINISTRADOR | Detalle con costos, historial y comprobantes |
| PUT | `/api/v1/sales/admin/orders/{number}/status` | ADMINISTRADOR | Valida, rechaza, avanza o cancela. Body `{status, reason}` (motivo obligatorio para rechazar o cancelar) |
| GET | `/api/v1/sales/admin/orders/{number}/receipts/{id}` | ADMINISTRADOR | Descarga un comprobante |
| GET | `/api/v1/sales/admin/reports/summary?from&to&groupBy&channel&paymentMethod` | ADMINISTRADOR | Totales por día, semana o mes con desglose por canal y medio de pago |
| GET | `/api/v1/sales/admin/reports/products?from&to&channel&sort&limit` | ADMINISTRADOR | Ranking de productos y combos por unidades, facturado o ganancia |
| GET | `/api/v1/sales/admin/settings` | ADMINISTRADOR | Configuración de ventas (datos para transferir y plazo de vencimiento) |
| PUT | `/api/v1/sales/admin/settings` | ADMINISTRADOR | Cambia la configuración. Body `{alias, cbu, holder, bank, expirationHours}` |

## decor-observability

Ver [observabilidad.md](observabilidad.md) (salud, tráfico y WebSocket) y [auditoria.md](auditoria.md) (filtros y
formato de la auditoría).

### Plataforma — `/api/v1/platform/observability` (sin tenant)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/platform/observability/ws-ticket` | SUPERADMIN | Ticket para el WS (un solo uso, 30 s): `{ticket, expiresIn}` |
| GET | `/api/v1/platform/observability/overview` | SUPERADMIN | Última lectura: salud de los servicios, tráfico, recursos y usuarios conectados |
| GET | `/api/v1/platform/observability/audit?from&to&userId&entity&entityId&action&page&size` | SUPERADMIN | Auditoría de la plataforma, paginada |

### Tienda — `/api/v1/observability`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/v1/observability/ws-ticket` | ADMINISTRADOR | Ticket para el WS de la tienda |
| GET | `/api/v1/observability/store/audit?from&to&userId&entity&action&page&size` | ADMINISTRADOR | Auditoría de la tienda, paginada |
| GET | `/api/v1/observability/store/audit/{entity}/{entityId}?page&size` | ADMINISTRADOR | Auditoría de una cosa puntual (ej. `Product/12`) |

### WebSocket — `/ws`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/ws?ticket=...` | Ticket | WebSocket de los paneles. Canales y mensajes en [observabilidad.md](observabilidad.md#websocket) |
