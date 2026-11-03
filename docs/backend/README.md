# Backend de decor

Referencia del backend para integrar el frontend o empezar a trabajar en un servicio: qué hace cada microservicio,
todos sus endpoints con ejemplos y cómo se comunican. Un archivo por servicio:

| Servicio | Doc | Puerto | Qué hace |
|---|---|---|---|
| gateway | [gateway.md](gateway.md) | 3000 | Única entrada: rutea, reconoce la tienda, valida el login, rate limit y CORS |
| users | [users.md](users.md) | 3001 | Login, usuarios, roles y permisos de cada tienda; alta de tiendas y panel de la plataforma |
| products | [products.md](products.md) | 3002 | Productos, stock, combos, CSV, catálogo público y panel de la tienda |
| sales | [sales.md](sales.md) | 3003 | Pedidos online, pagos por transferencia, ventas del local y reportes |
| observability | [observability.md](observability.md) | 3004 | Salud, métricas, auditoría y WebSocket de los paneles en tiempo real |
| decor-common | [common.md](common.md) | — | Librería compartida (no es un servicio) |
| eureka | [eureka.md](eureka.md) | 8761 | Registro de servicios |

Los docs por tema explican cómo funciona algo que cruza varios servicios; desde acá se enlazan cuando hace falta:
[tenants](../tenants.md), [permisos](../permisos.md), [ventas](../ventas.md), [panel-admin](../panel-admin.md),
[observabilidad](../observabilidad.md) y [auditoría](../auditoria.md). Cómo levantar todo: [README del
proyecto](../../README.md#cómo-correrlo).

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

- **Multi-tenant:** cada tienda (tenant) tiene su propio schema de Postgres en cada base. El gateway reconoce la
  tienda por el header `Origin` y se la pasa a los servicios. Ver [tenants.md](../tenants.md).
- **Plataforma:** lo que no es de ninguna tienda (alta de tiendas, superadmins, panel de la plataforma) vive en el
  schema `public` y en las rutas `/api/v1/platform/**`.
- **Entre servicios:** HTTP síncrono por Eureka con un token interno, protegido con Resilience4j (circuit breaker,
  reintentos y timeout). Los eventos para los paneles en vivo y la auditoría van por Redis Streams.
- **Caché:** Caffeine (local) + Redis (compartida) para tenants, roles y catálogo.

## Mapa de rutas

Todo se llama al gateway (`http://localhost:3000` en desarrollo). Detalle de cada ruta en el doc del servicio.

| Prefijo | Servicio | Tienda | Doc |
|---|---|---|---|
| `/api/v1/auth/**` | users | Sí | [users.md](users.md#autenticación) |
| `/api/v1/users/**` | users | Sí | [users.md](users.md#usuarios-de-la-tienda) |
| `/api/v1/platform/**` (salvo lo de abajo) | users | No | [users.md](users.md#plataforma) |
| `/api/v1/products/**`, `/api/v1/bundles/**`, `/api/v1/catalog/**` | products | Sí | [products.md](products.md) |
| `/products/images/**` | products | No | [products.md](products.md#imágenes) |
| `/api/v1/platform/products/**` | products | No | [products.md](products.md#plataforma) |
| `/api/v1/sales/**` | sales | Sí | [sales.md](sales.md) |
| `/api/v1/observability/**` | observability | Sí | [observability.md](observability.md#panel-de-la-tienda) |
| `/api/v1/platform/observability/**` | observability | No | [observability.md](observability.md#panel-de-la-plataforma) |
| `/ws` | observability | Según el ticket | [observability.md](observability.md#websocket) |
| `/api/internal/**` | users, products | — | Solo entre servicios (sección "Internos" de cada doc) |

"Tienda: Sí" quiere decir que la request tiene que salir del frontend de una tienda (su `Origin`); "No", que es de
la plataforma o pública sin tienda.

## Convenciones

### Cómo llamar

| Header | Cuándo | Ejemplo |
|---|---|---|
| `Origin` | Siempre en las rutas de una tienda: el gateway reconoce la tienda por ahí. El navegador lo manda solo; con curl o Postman hay que ponerlo | `Origin: http://demo.localhost:2999` |
| `Authorization` | Rutas con login | `Authorization: Bearer <token>` |
| `Content-Type` | Bodies JSON / archivos | `application/json` o `multipart/form-data` |

El token sale del login (`POST /api/v1/auth/login` o `/api/v1/platform/auth/login`), dura **1 hora** y está atado a
la tienda donde se logueó: con el token de una tienda no se puede llamar a otra (401). No hay refresh: vencido,
hay que volver a loguearse.

Los headers `X-User-Id`, `X-Forwarded-Host`, `X-Client-Ip` y los internos los pone el gateway; si los manda el
cliente, se borran.

### Acceso

En la ficha de cada endpoint, **Acceso** es uno de:

| Valor | Significa |
|---|---|
| Público | Sin token |
| Público (email) | Sin token; pide el email del pedido (sales). Si no coincide responde 404 |
| Login | Cualquier usuario logueado en la tienda |
| `PRODUCTOS`, `PRECIOS`, `STOCK`, `COMBOS` | Permiso de esa área. El `ADMINISTRADOR` las tiene todas; las de `EMPLEADO` y `CLIENTE` las configura el administrador. Ver [permisos.md](../permisos.md) |
| ADMINISTRADOR | Solo el rol administrador de la tienda (no se delega) |
| SUPERADMIN | Superadmin de la plataforma (token de `public`, sin tienda) |
| Interno | Entre servicios, con el token interno (`X-internal-request: true` y `X-Internal-Token`) |

Roles de una tienda: `ADMINISTRADOR` (el dueño), `EMPLEADO` y `CLIENTE`. Un usuario puede tener varios.

### Errores

**El formato no es uniforme**; casi todo es el código HTTP con el mensaje en **texto plano, en inglés**:

```
HTTP/1.1 404
Product not found
```

| Caso | Cuerpo |
|---|---|
| Errores de negocio (400, 404, 409...) | Texto plano con el mensaje. Cada doc lista los mensajes literales |
| 401 del gateway (sin token, vencido o de otra tienda) | Vacío |
| 404 / 403 del gateway por la tienda | Texto: `The tenant was not found.` / `The tenant is disabled.` |
| 429 (rate limit) | Vacío |
| 403 de Spring Security (falta el rol o el área) | Vacío o el JSON por defecto de Spring |
| Archivo más grande que el límite | 413, `The file exceeds the maximum upload size` |
| Excepciones | Algunos casos devuelven JSON propio: la importación de CSV (errores por fila), crear un pedido con precios viejos (los precios nuevos) y observability (el JSON por defecto de Spring: `timestamp`, `status`, `error`, `path`). Están en la ficha de cada endpoint |
| Error inesperado | 500 con el mensaje de la excepción |

Para el frontend: tomar el código HTTP como fuente de verdad y el texto solo para mostrarlo o mapearlo.

### Paginación

Los listados paginados reciben `page` (desde 0) y `size`, y devuelven siempre:

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }
```

El tamaño por defecto y el máximo cambian por endpoint (en general 20 y 100; el catálogo 24 y 100). Fuera de rango:
400.

### Fechas

- Fechas sin hora: `yyyy-MM-dd` (`2026-11-03`), en parámetros (`from`, `to`) y respuestas.
- Fechas con hora de las tiendas: **sin zona** (`2026-11-03T10:15:00`), en la hora del servidor (UTC en Docker).
- Plataforma (`/api/v1/platform/**`): **con offset** (`2026-11-03T10:15:00Z`), para que el navegador no las corra.
- observability y los eventos del WS: instantes en UTC con `Z`.
- Los reportes de ventas cuentan los días en la zona de la tienda (Argentina, configurable).
- Los períodos de los paneles usan `groupBy` = `DAY`, `WEEK` (empieza el lunes) o `MONTH`; cada período se nombra
  por su primer día y se devuelven todos, también los que tienen 0.

### Importes y cantidades

- Precios, costos y totales son números decimales en JSON (`15000.50`), con 2 decimales. Sin precio = `null`
  ("consultar precio").
- El stock es un entero. Solo cambia con movimientos de stock, nunca con un `PUT` del producto.

### Ids y otros

- Los ids son numéricos y **se repiten entre tiendas** (el producto 5 de una tienda no es el de otra).
- Los pedidos se identifican por su **número** (`K7QM-4X2P`, sin distinguir mayúsculas), no por el id.
- Los productos tienen un **slug** (`mesa-de-roble`) para los links públicos.

### Salud de los servicios

Cada servicio expone `/health`, `/ready`, `/metrics` y `/status` en un **puerto de gestión** interno (el del
servicio + 6000: users 9001, products 9002, sales 9003, observability 9004, gateway 9000, eureka 9761). El gateway
no los expone. Ver [observability.md](observability.md#salud-y-métricas).
