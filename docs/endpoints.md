# Microservicios y endpoints

Qué hace cada microservicio y la lista completa de endpoints. Para el detalle de los tenants y de los permisos ver
[tenants.md](tenants.md) y [permisos.md](permisos.md).

## Arquitectura

```
                    Frontend de cada tienda (http://<tienda>.localhost:3000)
                    Frontend de la plataforma (http://localhost:3000)
                                       │
                                       ▼
                            ┌──────────────────────┐
                            │  gateway  :3000      │  única entrada pública
                            └──────────┬───────────┘
                         Eureka (lb://) │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
          ┌──────────────────┐   tokens internos   ┌──────────────────┐
          │ decorusers :3001 │ ◄────────────────── │ products  :3002  │
          └────────┬─────────┘   (Resilience4j)    └────────┬─────────┘
                   │                                        │
           Postgres "users"          Redis           Postgres "products"
           (un schema por tenant + public)           (un schema por tenant)

          eureka :8761  ─  registro de servicios
          decor-common  ─  librería compartida (no es un servicio)
```

Todos los endpoints se llaman a través del **gateway** (`http://localhost:3000` en desarrollo). El gateway reconoce la
tienda por el header `Origin`, así que las llamadas tienen que salir desde la URL de la tienda (o mandar ese `Origin`).

## Microservicios

### eureka (`:8761`)

Registro de servicios (Spring Cloud Netflix Eureka). gateway, users y products se registran y se encuentran por
nombre (`lb://users-microservice`, `lb://products-microservice`). No tiene endpoints propios de la app.

### gateway (`:3000`, `gateway-microservice`)

Spring Cloud Gateway (WebFlux, reactivo). No tiene endpoints propios: enruta y protege.

- **Enrutamiento:** `/api/v1/products/**`, `/api/v1/bundles/**`, `/api/v1/catalog/**` y `/products/images/**` →
  products; `/api/v1/users/**`, `/api/v1/auth/**`, `/api/v1/platform/**` y `/api/internal/tenants/**` → users.
- **Tenant:** lo reconoce por el `Origin`, responde 404 si no existe o está borrado y 403 si está deshabilitado, y le
  pasa el schema a los servicios en `X-Forwarded-Host`.
- **Autenticación:** valida el JWT en las rutas no públicas (y que sea del tenant de la request) y pasa el usuario en
  `X-User-Id`.
- **Seguridad:** borra los headers internos que mande el cliente; CORS solo para orígenes de tenants y de la
  plataforma; rate limit por IP y origen (login 5/min, alta de tenants 3/min, resto 300/min, sin contar imágenes).
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
- Catálogo público paginado, con caché en Redis.
- Consulta tenants y roles a users (con circuit breaker, reintentos y caché).

### decor-common (librería)

No es un servicio: es un build compuesto de Gradle que usan los tres. Trae DTOs (`TenantDto`, `RoleDto`), headers,
Redis, caché Caffeine, tokens internos (`InternalTokenService`), áreas de permisos (`PermissionArea`) y, para users y
products, el multi-tenant de Hibernate, el `TenantFilter`, `CustomUserDetails` y el manejo de errores.

## Convenciones

**Acceso** (columna de las tablas):

| Valor | Significa |
|---|---|
| Público | Sin token |
| Login | Cualquier usuario logueado en la tienda (`Authorization: Bearer <token>`) |
| `PRODUCTOS`, `PRECIOS`, `STOCK`, `COMBOS` | Permiso del área (el `ADMINISTRADOR` los tiene todos). Ver [permisos.md](permisos.md) |
| ADMINISTRADOR | Rol administrador de la tienda |
| SUPERADMIN | Superadmin de la plataforma (sin tenant) |
| Interno | Solo entre servicios, con token interno (`X-internal-request: true` y `X-Internal-Token`) |

**Errores:** en general la respuesta es el código HTTP con el mensaje en texto (en inglés). La importación de CSV
devuelve JSON con los errores por fila. Un archivo más grande que el límite de subida responde 413.

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

### Internos — `/api/internal`

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/internal/tenants/all` | Interno | Todos los tenants (sin borrados). Lo usa products al arrancar |
| GET | `/api/internal/tenants/id?url=...` o `?schema=...` | Interno | Un tenant por URL (gateway) o por schema (products) |
| GET | `/api/internal/tenants/cors?url=...` | Interno | Si la URL es de un tenant (CORS del gateway) |
| GET | `/api/internal/users/all` | Interno | Todos los usuarios |
| GET | `/api/internal/users/{id}` | Interno | Un usuario |
| GET | `/api/internal/users/roles/{id}` | Interno | Roles de un usuario con sus permisos (lo usa products) |

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
| GET | `/api/v1/products/{id}/pricing` | `PRECIOS` | Precio, costo, margen y margen % |
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
| GET | `/api/v1/catalog/products?q&category&sort&page&size` | Público | Productos paginados (24 por página, máximo 100). `q` busca en nombre, descripción y categoría; `sort` = `name`, `price_asc` o `price_desc` (sin precio, al final). Devuelve `{items, page, size, totalItems, totalPages}` |
| GET | `/api/v1/catalog/products/{slug}` | Público | Detalle por slug (para los links compartidos) |
| GET | `/api/v1/catalog/categories` | Público | Categorías con su cantidad de productos |

El catálogo no muestra el stock exacto (`availability`: `DISPONIBLE`, `POCAS_UNIDADES` o `AGOTADO`) ni el costo. Un
producto sin precio viene con `price: null` ("consultar"). Las respuestas se cachean en Redis por tenant y se
invalidan con cada cambio de productos.

### Imágenes

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/products/images/{archivo}` | Público | Archivo de imagen de un producto |
