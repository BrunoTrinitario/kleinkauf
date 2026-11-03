# Panel de administración

Endpoints de resumen para el **administrador de la tienda** y para el **administrador de la plataforma**
(superadmin). Acá se define solo lo que exponen los servicios; el frontend está en [frontend.md](frontend.md).

> Estado: **backend implementado** en los dos paneles. El de la plataforma ya tiene frontend; el de la tienda
> todavía no. Las ventas ([ventas.md](ventas.md), `/api/v1/sales/admin/reports/**`) y el tiempo real
> ([observabilidad.md](observabilidad.md)) se suman a las mismas páginas.

## Decisiones

| Tema | Decisión |
|---|---|
| Alcance | **Con los datos de hoy** (productos, stock, usuarios y tenants), preparado para sumar ventas y observabilidad con el mismo formato |
| Quién arma el resumen | **Cada servicio el suyo**: products el de productos y stock, users el de usuarios y tenants. El frontend llama en paralelo; si un servicio se cae, el resto del panel funciona |
| Panel de la tienda | Secciones **Productos**, **Stock** y **Usuarios** |
| Acceso al panel de la tienda | **Solo `ADMINISTRADOR`** (todo el panel, no por áreas) |
| Panel de la plataforma | **Tenants**, **uso por tenant** y **gestión de superadmins** |
| Usuario activo | **Entró en los últimos 30 días** (`lastLogin`) |
| `GET /api/v1/users/stats` | **Se borra**: lo reemplaza el panel de usuarios (el frontend se reescribe) |
| Períodos | Parámetros **`from`, `to` y `groupBy=DAY\|WEEK\|MONTH`**. Por defecto, los últimos 30 días por día. Se devuelven **todos los períodos, también los que tienen 0** |
| Uso por tenant | products expone **su propio endpoint de plataforma** (cuenta productos por schema); users da usuarios y última actividad por tenant |
| Última actividad | El **último login** de los usuarios de la tienda. El gateway ya marca la última request (`activity:<tenant>:<userId>`, ver [observabilidad.md](observabilidad.md)); falta que este panel la use |
| Orden del uso por tenant | **Elegible** (`sort`), descendente; por defecto, por productos |
| Caché | **Solo el uso por tenant**, 5 minutos en Redis. Los paneles de la tienda siempre al día |
| Valor del inventario | Los productos **sin costo o sin precio se excluyen y se cuentan** aparte |
| Alta de superadmins | Otro superadmin lo crea con **contraseña temporal**; **no** tiene que cambiarla en el primer login |
| Desactivar superadmins | Con **`deletedAt`** (borrado lógico); reactivar = limpiar `deletedAt`. No puede desactivarse a sí mismo ni al último activo |

## Panel de la tienda (ADMINISTRADOR) — implementado

**Diferencias con el plan:**

- products: `recentMovements` trae también `id`, `stockAfter` y `reference` (el número de pedido en los movimientos
  de ventas), y el nombre del producto aunque esté borrado. Los importes van como números (como el resto de la API),
  siempre con 2 decimales y en `0.00` si no hay nada que sumar. En `byCategory` los empates van por nombre y "sin
  categoría" al final.
- users: `byRole` trae siempre `ADMINISTRADOR`, `EMPLEADO` y `CLIENTE` (en 0 si no hay). `registrations` cuenta las
  altas de los usuarios **que siguen en la tienda** (a diferencia de la plataforma, que incluye las tiendas borradas):
  `@SQLRestriction` oculta los borrados y para el dueño importa la base de clientes actual. Usa la misma utilidad de
  períodos que la plataforma (`util/Periods.java`) y los mismos errores (400).
- `/users/stats` se borró junto con `UserStatsDto`, `UserService.getServiceStats` y
  `UserRepository.findByCreatedAtBetween`.
- Cada servicio arma su panel con consultas de conteo y suma: no se cargan los productos ni los usuarios.

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
      { "productId": 12, "productName": "Mesa ratona", "type": "ENTRADA", "quantity": 5, "reason": "Compra", "userId": 3, "createdAt": "2026-11-02T10:00:00" }
    ]
  }
}
```

| Dato | Cómo se calcula |
|---|---|
| `total` | Productos no borrados |
| `byCategory` | Cantidad por categoría (sin categoría → `null`), de mayor a menor |
| `withoutPrice` | `price` vacío ("a consultar") |
| `withoutImages` | Sin ninguna imagen |
| `featured` | Marcados como destacados |
| `low` | `stock <= minStock` (mismo criterio que `GET /products/stock/low` y el catálogo); incluye los agotados |
| `outOfStock` | `stock <= 0` |
| `inventoryAtCost` | Σ `stock × cost` de los que tienen costo (y stock > 0) |
| `inventoryAtPrice` | Σ `stock × price` de los que tienen precio (y stock > 0) |
| `excludedWithoutCost` / `excludedWithoutPrice` | Productos con stock que quedaron afuera del valor por no tener costo / precio |
| `recentMovements` | Los **últimos 10** movimientos de stock |

- Seguridad: `hasRole("ADMINISTRADOR")` en el `SecurityConfig` de products, **antes** de las reglas generales de
  `/api/v1/products/**` (hoy los `GET` de esa ruta son `permitAll`).
- Ojo con el mapeo: `GET /api/v1/products/{id}` ya existe; Spring prefiere la ruta literal `/dashboard`, pero
  `PermittedUrls` de products usa la regex `^/\d+$`, así que `/dashboard` no queda abierto por error.

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

- `byRole`: un usuario con varios roles cuenta en cada uno.
- Ya queda cubierto por la regla existente `/api/v1/users/** → ADMINISTRADOR`.
- Se borran `GET /api/v1/users/stats`, `UserStatsDto`, `UserService.getServiceStats` y
  `UserRepository.findByCreatedAtBetween` (si nada más lo usa).

## Panel de la plataforma (SUPERADMIN) — implementado

Todo bajo `/api/v1/platform/**`, sin tenant (como el panel de tenants). En users es `/api/v1/platform/admin/**`; en
products, `/api/v1/platform/products/**`. Si una request llega con tenant, los dos responden 404.

**Diferencias con el plan** (lo de abajo ya está corregido):

- Las columnas reales son `lastlogin` (no `last_login`) y `roles.userid` (no `user_id`): lo confirmó la base.
- Los combos (`bundles`) **no tienen `deleted_at`**: se borran de verdad, así que se cuentan todos.
- products **no pide la lista de tenants a users**: cuenta los schemas de su propia base que tienen la tabla
  `product` (menos `public`). Un tenant que nunca abrió su tienda no tiene schema en products y no aparece; el
  frontend lo cuenta como 0. Así no depende de que users responda ni de la lista de tenants en Redis.
- La utilidad de períodos quedó en users (`util/Periods.java`), no en decor-common: sales ya tiene la suya
  (`ReportService`, con los días en la zona de la tienda) y no se tocó. Errores: `groupBy` o fechas inválidas, o
  `from > to`, o más de 1.000 períodos → 400.
- products no necesitó `decor.tenant.optional-paths`: el `TenantFilter` ya tenía `decor.tenant.public-paths`, y se
  agregó `/api/v1/platform/` ahí.
- Parámetros inválidos de `sort` → 400.
- Las fechas de las respuestas de plataforma (`createdAt`, `lastActivity`, `lastLogin`, también en
  `/admin/tenants`) van **con offset** (`OffsetDateTime`, `util/Dates.java`): se guardan en la hora del servidor (UTC
  en Docker) y sin offset el navegador las mostraba corridas 3 horas.
- **Pendiente:** los períodos de altas se arman con el día del servidor (UTC). Una tienda creada después de las 21
  (hora argentina) cae en el día siguiente. sales lo resuelve con una zona configurable (`SalesTime`); la plataforma
  podría hacer lo mismo.

### users — `GET /api/v1/platform/admin/dashboard?from&to&groupBy`

```json
{
  "tenants": { "total": 42, "enabled": 39, "disabled": 3, "deleted": 5 },
  "registrations": { "from": "…", "to": "…", "groupBy": "WEEK", "periods": [ { "period": "2026-10-27", "count": 4 } ] }
}
```

- `total`, `enabled`, `disabled`: tenants no borrados. `deleted`: borrados (soft delete).
- `registrations`: altas por `createdAt`, **incluidos los que después se borraron** (es la historia de altas).

### Uso por tenant

Se arma en el frontend juntando dos listas por `schema`:

| Servicio | Endpoint | Datos por tenant | `sort` |
|---|---|---|---|
| users | `GET /api/v1/platform/admin/usage?sort=` | `id`, `identifier`, `schema`, `enabled`, `createdAt`, `users`, `activeUsers`, `lastActivity` | `users`, `activeUsers`, `lastActivity`, `createdAt` (por defecto `users`) |
| products | `GET /api/v1/platform/products/usage?sort=` | `schema`, `products`, `bundles` | `products` (por defecto), `bundles` |

- Se devuelven **todos los tenants no borrados** (sin paginar: con cientos de tiendas alcanza). El "por defecto por
  productos" del ranking lo aplica el frontend tomando el orden de products.
- Cada servicio recorre los schemas con SQL directo (`JdbcTemplate`, sin Hibernate):
  - users (`PlatformDashboardService`): `SELECT COUNT(*), COUNT(*) FILTER (WHERE lastlogin >= ?), MAX(lastlogin) FROM <schema>.usuarios WHERE deleted_at IS NULL`
  - products (`PlatformUsageService`): `SELECT COUNT(*) FROM <schema>.product WHERE deleted_at IS NULL` y
    `SELECT COUNT(*) FROM <schema>.bundles`.
  - users toma el schema de la tabla de tenants; products, de `information_schema`. Los dos lo validan con
    `^[a-z][a-z0-9_]*$` antes de armar el SQL.
  - Si el schema todavía no tiene la tabla, cuenta 0 (users) o no aparece (products).
- `lastActivity` es el último login: `null` si nadie de la tienda entró. En los dos servicios los empates se
  ordenan por identificador / schema.
- **Caché**: cada servicio guarda su lista en Redis 5 minutos (`platform:usage:users`, `platform:usage:products`).
  Si Redis falla, se calcula igual.

### Gestión de superadmins (users)

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/platform/admin/superadmins` | Lista con `id`, `name`, `email`, `active`, `createdAt`, `lastLogin` (incluye los desactivados), por fecha de alta |
| POST | `/api/v1/platform/admin/superadmins` | `{name, email, password}` → 201. Crea el usuario en `public` con rol `SUPERADMIN`. Mismas reglas de nombre, email y contraseña que cualquier usuario |
| PUT | `/api/v1/platform/admin/superadmins/{id}/active` | `{"active": false}` desactiva, `true` reactiva. Devuelve el superadmin. Si ya estaba así, no cambia nada |

| Caso | Respuesta |
|---|---|
| Falta nombre, email o contraseña | 400 |
| Email ya usado, **también por un superadmin desactivado** | 409 |
| Desactivarse a sí mismo | 400 |
| Desactivar al último superadmin activo | 409 |
| Id que no es superadmin | 404 |

- `SUPERADMIN` no se puede asignar con `UserService.createUser` (`RoleUtility` lo excluye): se crea con los
  repositorios, como `SuperadminInitializer`.
- **Desactivar** = `deleted_at` en el usuario **y en su rol `SUPERADMIN`**. El rol hay que borrarlo también porque
  el `HeaderAuthFilter` de users busca los roles por id de usuario (`findByUser_Id`) sin mirar si el usuario está
  borrado: si solo se borrara el usuario, su token seguiría sirviendo. Además se borran sus roles de Redis
  (`user:<id>:null`).
- **Reactivar** = limpiar `deleted_at` del usuario y de su rol `SUPERADMIN`. Como `@SQLRestriction` oculta los
  borrados, la lista, la reactivación y el chequeo de email repetido van con **SQL directo** sobre `public`
  (`SuperadminService`).
- Un superadmin desactivado no puede loguearse (el login no lo encuentra). En products su token puede servir hasta
  1 minuto más (caché local de roles).

## Cambios por servicio

### decor-common

- `TenantFilter`: lista de **rutas sin tenant** (`decor.tenant.optional-paths`), para que products acepte
  `/api/v1/platform/**` sin `X-Forwarded-Host` aunque en el resto el tenant sea obligatorio.
- Utilidad de **períodos** (`GroupBy` `DAY`/`WEEK`/`MONTH`, `PeriodCountDto`, armado de la serie con ceros; la
  semana empieza el lunes). Va en la librería porque los reportes de [ventas](ventas.md) la van a usar igual.
  Validaciones: `from <= to` y un tope de períodos (por ejemplo 1.000) → 400.

### users

- `DashboardService` con el panel de usuarios, el de tenants y el uso por tenant.
- Endpoints: `/api/v1/users/dashboard`, `/api/v1/platform/admin/dashboard`, `/api/v1/platform/admin/usage` y
  `/api/v1/platform/admin/superadmins/**`, todos con `requirePlatformContext()` en los de plataforma.
- Consultas nuevas en `UserRepository`, `RoleRepository` y `TenantRepository` (conteos, fechas de alta, roles por
  tipo, superadmins incluidos los borrados).
- Borrar `/users/stats` y lo que solo usaba eso.

### products

- `GET /api/v1/products/dashboard` (`ADMINISTRADOR`) y `GET /api/v1/platform/products/usage` (`SUPERADMIN`, sin
  tenant: si llega con tenant responde 404, igual que users).
- `decor.tenant.optional-paths=/api/v1/platform/` en `application.properties`.
- `UsersClient.getUserRoles`: no mandar `X-Forwarded-Host` cuando no hay tenant (hoy haría `headers.set(..., null)`),
  así users busca los roles del superadmin en `public`.
- Consultas nuevas en `ProductRepository` y `StockMovementRepository`.

### gateway

- Ruta `/api/v1/platform/products/**` → products, **antes** de la ruta general `/api/v1/platform/**` → users
  (con `order` menor para no depender del orden de definición).
- No hace falta nada más: `TenantGatewayFilter` ya saltea las rutas de plataforma y `JwtFilter` ya exige
  `tenant = "public"` en ellas.

## Para verificar al implementar

- **Nombres de columnas en las consultas nativas.** Los schemas los crea Hibernate con `hbm2ddl` desde un
  `EntityManagerFactory` propio (`TenantDataSource` en users y la autoconfiguración de `decor-common`), **sin** la
  estrategia de nombres de Spring Boot. Los campos sin `@Column` (`lastLogin`, `userId` de `roles`) podrían haber
  quedado como `lastlogin` / `userid` en vez de `last_login` / `user_id`. Revisarlo en la base antes de escribir el
  SQL.
- Que un alta de superadmin con un email de un usuario borrado devuelva 409 y no un 500 por la restricción única
  (`existsByEmail` no ve los borrados). Lo mismo pasa hoy en el alta de usuarios de una tienda.

## Tests previstos

- Utilidad de períodos: día, semana (cruce de mes y año), mes, rango de un solo día, períodos en 0, `from > to`,
  tope de períodos.
- Servicios de dashboard con mocks (conteos, valor del inventario excluyendo sin costo/precio, últimos movimientos).
- Superadmins: alta, email repetido, desactivar a sí mismo, desactivar al último, reactivar.
- Seguridad (`WebMvcTest`): panel de la tienda solo `ADMINISTRADOR` (un `EMPLEADO` con todas las áreas recibe 403),
  plataforma solo `SUPERADMIN`, products sin tenant solo en `/api/v1/platform/**`.
- `TenantFilter` con rutas opcionales.

## Para después

- Ventas: los reportes ya existen (`/api/v1/sales/admin/reports/**`, ver [ventas.md](ventas.md)); falta sumarlos a
  la página del panel en el frontend.
- Observabilidad: tráfico, errores, salud y tiempo real por WS. El backend ya está (ver
  [observabilidad.md](observabilidad.md)); falta sumarlo a las páginas de los paneles.
- "Activos" sigue siendo el último login en 30 días: un usuario que usa la app con una sesión abierta hace más de
  30 días cuenta como inactivo. Las keys `activity:*` del gateway duran 5 minutos, así que no sirven para una
  ventana de 30 días: haría falta guardar la última request en la base.
- "Tareas pendientes" (productos sin precio o sin imagen, combos que no se pueden armar) como lista accionable.
