# observability (kleinkauf-observability)

Spring Boot (servlet) en el puerto **3004**, registrado en Eureka como `observability-microservice`. Base Postgres
`observability` (un schema por tienda y `public`) con JDBC, sin Hibernate.

Convenciones comunes: [README](README.md). Diseño y decisiones: [observabilidad.md](../observabilidad.md) y
[auditoria.md](../auditoria.md).

## Qué hace

- **Salud y tráfico de la plataforma:** cada 10 s lee `/ready` y `/metrics` de cada instancia registrada en Eureka
  y arma el estado de cada servicio, el tráfico (requests, errores, latencias), los recursos y los usuarios
  conectados.
- **Auditoría:** guarda los eventos que mandan users, products y sales (quién hizo qué) y deja consultarlos.
- **Tiempo real por WebSocket** para el panel de la plataforma (superadmin) y el de cada tienda (administrador):
  salud, tráfico, altas de tiendas, auditoría en vivo, actividad de la tienda (pedidos y stock), uso del catálogo y
  usuarios conectados.

## Salud y métricas

Todos los servicios exponen en su **puerto de gestión** (el del servicio + 6000), que el gateway no publica:

| Ruta | Qué responde | Lo usa |
|---|---|---|
| `/health` | Liveness: el proceso está vivo | Docker (healthchecks) |
| `/ready` | Readiness: base, Redis, Eureka y lo que necesite el servicio (no mira a los otros servicios) | observability |
| `/metrics` | Formato Prometheus, con la etiqueta `tenant` en las métricas HTTP | Prometheus y observability |
| `/status` | El detalle de la salud por componente | A mano |

Puertos: gateway 9000, users 9001, products 9002, sales 9003, observability 9004, eureka 9761.

## Panel de la plataforma

Solo `SUPERADMIN`, desde el frontend de la plataforma (sin tienda). Con tienda responden **404**.

### POST /api/v1/platform/observability/ws-ticket

**Acceso:** SUPERADMIN

Ticket para abrir el WebSocket. Se emite con los roles **actuales** (los pide a users en el momento).

```bash
curl -X POST http://localhost:3000/api/v1/platform/observability/ws-ticket \
  -H 'Origin: http://localhost:2999' -H 'Authorization: Bearer <token>'
```

Respuesta **200**: `{ "ticket": "f3a9...", "expiresIn": 30 }`

| Código | Cuándo |
|---|---|
| 403 | Ya no es superadmin (por ejemplo, lo desactivaron) |
| 503 | users no responde (`No se pudo verificar el acceso`) |

### GET /api/v1/platform/observability/overview

**Acceso:** SUPERADMIN

La última lectura del recolector, para mostrar algo antes del primer mensaje del WS.

```json
{
  "at": "2026-11-03T13:00:10Z",
  "services": [
    { "name": "products-microservice", "status": "UP",
      "instances": [ { "id": "products-microservice:172.18.0.5:3002", "status": "UP",
                       "components": { "db": "UP", "redis": "UP", "discoveryComposite": "UP" }, "error": null } ],
      "openCircuitBreakers": [] }
  ],
  "traffic": [
    { "name": "products-microservice", "instances": 1, "requestsPerSecond": 2.4,
      "errors5xxPerSecond": 0.0, "errors4xxPerSecond": 0.1, "rateLimitedPerSecond": 0.0,
      "p50Ms": 12.3, "p95Ms": 48.0, "p99Ms": 95.5,
      "heapUsedBytes": 1.5E8, "heapMaxBytes": 4.0E9, "cpu": 0.03, "threads": 42.0,
      "dbConnectionsActive": 1.0, "dbConnectionsMax": 10.0 }
  ],
  "connectedUsers": { "total": 5, "byTenant": [ { "tenant": "demo", "users": 4 }, { "tenant": "public", "users": 1 } ] }
}
```

- `status` de un servicio: `UP` (todas sus instancias listas), `DEGRADED` (algunas) o `DOWN` (ninguna, o sin
  instancias en Eureka).
- Las tasas y los percentiles son `null` hasta la segunda lectura (salen de restar dos). Los recursos son la suma
  de las instancias (la CPU, el promedio).
- Antes de la primera lectura: `at: null` y listas vacías.

### GET /api/v1/platform/observability/audit

**Acceso:** SUPERADMIN

Auditoría de la plataforma (tiendas y superadmins), paginada, la más nueva primero. No incluye la de las tiendas.

| Parámetro | Qué hace |
|---|---|
| `from`, `to` | Fecha **con zona** ISO-8601 (`2026-11-02T03:00:00Z`); `to` no se incluye. El frontend decide qué es "un día" para quien mira |
| `userId` | Quién lo hizo |
| `entity`, `entityId` | Qué cosa (`Tenant`, `7`) |
| `action` | `CREATE`, `UPDATE`, `DELETE`, `READ`, `STATUS_CHANGE`, `IMPORT`, `ACTIVATE`, `DEACTIVATE` |
| `page`, `size` | 0 y 20 |

Respuesta **200**: `{ "items": [AuditEvent], "page", "size", "totalItems", "totalPages" }` (ver
[formato](#formato-de-la-auditoría)). Error: 400 si `from` o `to` no son fechas con zona.

### GET /api/v1/platform/observability/audit/export

**Acceso:** SUPERADMIN

La misma auditoría en **CSV** (`auditoria-plataforma-<fecha>.csv`), con los mismos filtros sin `page` ni `size`.
Formato en [auditoria.md](../auditoria.md#consultas). 400 si hay más de 50.000 eventos (hay que acotar las fechas).

## Panel de la tienda

Solo `ADMINISTRADOR` de la tienda. Sin tienda (desde la plataforma) responden **404**; el superadmin no ve la
auditoría de las tiendas.

### POST /api/v1/observability/ws-ticket

**Acceso:** ADMINISTRADOR

Igual que el de la plataforma, para el WS de la tienda. Respuesta **200**: `{ "ticket": "...", "expiresIn": 30 }`.
403 si ya no es administrador, 503 si users no responde.

### GET /api/v1/observability/store/audit

**Acceso:** ADMINISTRADOR

Auditoría de la tienda, paginada. Mismos parámetros que la de la plataforma salvo `entityId` (para una cosa
puntual, el endpoint de abajo).

```bash
curl 'http://localhost:3000/api/v1/observability/store/audit?entity=Product&action=update&from=2026-11-01T03:00:00Z' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

### GET /api/v1/observability/store/audit/{entity}/{entityId}

**Acceso:** ADMINISTRADOR

Historial de una cosa (`/store/audit/Product/12`, `/store/audit/Order/K7QM-4X2P`). Paginado.

### GET /api/v1/observability/store/audit/export

**Acceso:** ADMINISTRADOR

La auditoría de la tienda en **CSV** (`auditoria-<tienda>-<fecha>.csv`), con los mismos filtros que
`/store/audit` sin `page` ni `size`. 400 si hay más de 50.000 eventos.

```bash
curl -o auditoria.csv 'http://localhost:3000/api/v1/observability/store/audit/export?from=2026-11-01T03:00:00Z' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

### GET /api/v1/observability/store/users

**Acceso:** ADMINISTRADOR

Usuarios conectados de la tienda (alguna request en los últimos 5 minutos), del más reciente al más viejo. Son
todos los logueados, también clientes. `name` es `null` si users no respondió.

```json
{ "total": 2,
  "users": [ { "userId": 8, "name": "Beto", "lastActivity": "2026-11-03T13:00:05Z" },
             { "userId": 1, "name": "Dueño Demo", "lastActivity": "2026-11-03T12:58:40Z" } ] }
```

Error: 503 si no se pudo leer Redis.

### GET /api/v1/observability/store/usage

**Acceso:** ADMINISTRADOR

Uso de la tienda en el día.

```json
{
  "at": "2026-11-03T13:00:10Z",
  "date": "2026-11-03",
  "catalogVisits": 40,
  "topProducts": [ { "slug": "mesa-de-roble", "views": 12 }, { "slug": "silla-nordica", "views": 7 } ],
  "traffic": { "requestsPerSecond": 2.5, "errors4xxPerSecond": 0.1, "errors5xxPerSecond": 0.0 }
}
```

- `date`: el día en Buenos Aires. `catalogVisits`: veces que se abrió la primera página del catálogo sin búsqueda.
  `topProducts`: los 5 detalles más vistos, por slug.
- `traffic`: requests y errores por segundo de la tienda en el gateway, de la última lectura. `null` hasta la
  segunda lectura del gateway.

Error: 503 si no se pudo leer Redis.

## WebSocket

### Guía del cliente

1. **Pedir un ticket** con el JWT: `POST /api/v1/platform/observability/ws-ticket` (superadmin) o
   `POST /api/v1/observability/ws-ticket` (administrador). Dura **30 s** y sirve **una sola vez**.
2. **Conectar** a `ws://localhost:3000/ws?ticket=<ticket>` desde el frontend (el navegador manda el `Origin`). El
   JWT nunca va en la URL.
   - Ticket ausente, vencido o usado: el handshake falla con **401**.
   - Ticket de otra tienda (o de tienda abierto desde la plataforma, y al revés): **403**.
3. **Suscribirse** a los canales que muestre la página. Un ticket de plataforma solo puede `platform.*`; uno de
   tienda solo `store.*`, siempre de su tienda.
4. **Ping cada 30 s** para mantener la conexión.
5. **Cargar lo anterior por REST** (overview, auditoría, usuarios, uso): el WS solo manda lo nuevo desde que se
   suscribió.
6. **Si se corta**, pedir un ticket nuevo y reconectar (con espera creciente). Si el ticket responde **403**, perdió
   el acceso: no reintentar.

| Cierre | Motivo | Qué hacer |
|---|---|---|
| 1008 `Acceso revocado` | Cada minuto se revisan los roles de los conectados; ya no tiene el rol | No reconectar; cerrar la sesión o volver al inicio |
| 4500 (sesión no confiable) | El cliente no leyó a tiempo (más de 5 s o 512 KB pendientes) | Reconectar con ticket nuevo |
| Otros | Red, reinicio del servicio | Reconectar con ticket nuevo |

```js
async function connectWs(ticketUrl, token, channels, onEvent) {
  const res = await fetch(ticketUrl, { method: "POST", headers: { Authorization: `Bearer ${token}` } })
  if (res.status === 403) throw new Error("Sin acceso")
  const { ticket } = await res.json()

  const ws = new WebSocket(`ws://localhost:3000/ws?ticket=${encodeURIComponent(ticket)}`)
  let ping
  ws.onopen = () => {
    channels.forEach((channel) => ws.send(JSON.stringify({ type: "subscribe", channel })))
    ping = setInterval(() => ws.send(JSON.stringify({ type: "ping" })), 30_000)
  }
  ws.onmessage = (e) => {
    const msg = JSON.parse(e.data)
    if (msg.type === "event") onEvent(msg.channel, msg.data, msg.at)
    if (msg.type === "error") console.warn(msg.message)
  }
  ws.onclose = (e) => {
    clearInterval(ping)
    if (e.code !== 1008) setTimeout(() => connectWs(ticketUrl, token, channels, onEvent), 2_000)
  }
  return ws
}
```

### Mensajes

Del cliente (JSON de hasta 1.024 caracteres):

| Mensaje | Respuesta |
|---|---|
| `{ "type": "subscribe", "channel": "store.activity" }` | `{ "type": "subscribed", "channel": "store.activity" }` |
| `{ "type": "unsubscribe", "channel": "store.activity" }` | `{ "type": "unsubscribed", "channel": "store.activity" }` |
| `{ "type": "ping" }` | `{ "type": "pong" }` |

Un error no corta la conexión: `{ "type": "error", "message": "Canal no permitido: platform.health" }` (también
`JSON invalido`, `Mensaje demasiado largo`, `Tipo de mensaje desconocido: …`).

Del servidor, en cada canal suscripto:

```json
{ "type": "event", "channel": "store.activity", "data": { ... }, "at": "2026-11-03T13:00:00Z" }
```

### Canales

| Canal | Quién | Cuándo manda | `data` |
|---|---|---|---|
| `platform.health` | Superadmin | Cuando cambia el estado de algún servicio, instancia o circuit breaker | Lista de servicios (como `services` del overview) |
| `platform.traffic` | Superadmin | En cada lectura (10 s) | `{ at, traffic, connectedUsers }` (como el overview) |
| `platform.business` | Superadmin | Alta, habilitación, deshabilitación o baja de una tienda | `{ type: "tenant.created", at, id, identifier, schema }` |
| `platform.audit` | Superadmin | Cada evento de auditoría de la plataforma, apenas se guarda | `AuditEvent` |
| `store.audit` | Administrador | Cada evento de auditoría de su tienda | `AuditEvent` |
| `store.activity` | Administrador | Pedidos, comprobantes y stock, apenas pasan | Ver abajo |
| `store.usage` | Administrador | En cada lectura (10 s) | Como `GET /store/usage` |
| `store.users` | Administrador | En cada lectura (10 s) | Como `GET /store/users` |

`store.activity` manda `{ type, at, ...datos }`:

| `type` | Cuándo | Datos |
|---|---|---|
| `order.created` | Pedido online nuevo o venta del local | `number`, `channel` (`ONLINE`/`LOCAL`), `status`, `total`, `buyerName` |
| `order.receipt-uploaded` | El comprador subió un comprobante | Los mismos |
| `stock.movement` | Movimiento de stock cargado a mano (los de los pedidos no) | `productId`, `productName`, `movementType`, `quantity`, `stockAfter`, `reason`, `userId` |
| `stock.low` | Un producto pasa a stock bajo (una vez, al cruzar el mínimo) | `productId`, `productName`, `stock`, `minStock` |

```json
{ "type": "event", "channel": "store.activity",
  "data": { "type": "order.created", "at": "2026-11-03T13:00:00Z", "number": "K7QM-4X2P",
            "channel": "ONLINE", "status": "PENDIENTE_PAGO", "total": 72000.0, "buyerName": "Ana Pérez" },
  "at": "2026-11-03T13:00:00.120Z" }
```

`store.activity` es solo en vivo: lo que pasó con el panel cerrado está en la auditoría y en la lista de pedidos.

## Formato de la auditoría

```json
{
  "id": "0b6f…",
  "tenant": "demo",
  "at": "2026-11-02T10:15:00Z",
  "service": "products",
  "actor": { "userId": 3, "name": "Ana", "role": "ADMINISTRADOR" },
  "ip": "190.1.2.3",
  "traceId": "4bf92f3577b34da6",
  "action": "UPDATE",
  "entity": "Product",
  "entityId": "12",
  "changes": { "price": { "from": "15000.00", "to": "18000.00" } }
}
```

- `action`: `CREATE`, `UPDATE`, `DELETE` (también el borrado lógico), `READ` (lecturas sensibles),
  `STATUS_CHANGE` (pedidos), `IMPORT` (CSV), `ACTIVATE` / `DEACTIVATE` (superadmins).
- `actor`: `null` en el alta de tienda por autoservicio. `role` es el rol más alto; `name` lo completa
  observability al guardar (queda aunque después borren al usuario).
- `changes`: en un alta, los campos con valor; en un cambio, solo los que cambiaron. Valores como texto (hasta 500).
  Nunca contraseñas, tokens ni campos ignorados (como el último login).
- `traceId` une el evento con la traza y los logs en Grafana.

Qué se registra en cada servicio: [auditoria.md](../auditoria.md#qué-se-registra). Llega por un **outbox**: no se
pierde aunque observability o Redis estén caídos.

## Cómo se comunica

- **Recolector:** cada `observability.poll.interval` (10 s) lee `/ready` y `/metrics` de cada instancia de Eureka
  que publica `management.port`, todas a la vez, con 2 s de timeout. Eureka se lee aparte
  (`observability.static-targets`). El estado queda en memoria.
- **Tráfico por tienda:** de las métricas del gateway (`observability.gateway-service`), por la etiqueta `tenant`.
- **Redis:**

| Key / stream | Para qué |
|---|---|
| `ws-ticket:<id>` | Tickets del WS (30 s, se leen y borran con `GETDEL`) |
| `activity:<tenant>:<userId>` | Última request de cada usuario (la escribe el gateway). Usuarios conectados |
| `usage:<tenant>:<día>` | Uso del catálogo (lo escribe products) |
| `kleinkauf:events` | Eventos de los servicios (tiendas, pedidos, stock). Se leen sin grupo de consumidores: cada instancia los ve todos |
| `kleinkauf:audit` | Auditoría. Se consume con un grupo: cada evento se guarda una vez y se confirma |

- **users:** roles actuales para los tickets y la revisión de cada minuto, y nombres para la auditoría (caché de
  10 minutos). Con circuit breaker y reintentos. Si users no responde, no se desconecta a nadie.
- **Base:** la auditoría, un schema por tienda y `public`, creados con el primer evento.

## Stack de Docker y Grafana

```bash
docker compose -f backend/deploy/docker-compose.dev.yml -f backend/deploy/docker-compose.observability.yml up -d
```

El segundo archivo suma Prometheus, Grafana, Tempo, Loki y Alloy; sin él todo anda igual pero sin ese stack.

| Qué | Dónde |
|---|---|
| Grafana (sin login en desarrollo) | http://localhost:3300 → carpeta **KleinKauf**, dashboard **KleinKauf - Servicios** |
| Prometheus | http://localhost:9090 (Status → Targets) |
| Trazas | Grafana → Explore → Tempo |
| Logs | Grafana → Explore → Loki, por ejemplo `{service="product-service", level="ERROR"}` |

- Con el stack, los servicios loguean en JSON y mandan trazas a Alloy; sin él, logs en texto con
  `[servicio,traceId,spanId,tenant]`.
- Desde un log se abre su traza y desde una traza sus logs.
- Los datos del stack no tienen volumen. Detalle en [observabilidad.md](../observabilidad.md#cómo-se-usa).

## Configuración

| Propiedad | Valor en desarrollo | Para qué |
|---|---|---|
| `server.port` / `management.server.port` | `3004` / `9004` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/observability` | Auditoría |
| `spring.data.redis.*` | `localhost:6379` | Tickets, actividad, eventos |
| `users.url` | `http://users-microservice/` | Roles y nombres |
| `observability.poll.interval` / `poll.timeout` | `10s` / `2s` | Recolector |
| `observability.services` | Los 5 servicios | Los que tienen que estar (sin instancias figuran `DOWN`) |
| `observability.static-targets` | `eureka=http://localhost:9761` | Lo que no se registra en Eureka |
| `observability.gateway-service` | `gateway-microservice` | De dónde sale el tráfico por tienda |
| `observability.ticket.ttl` | `30s` | Duración de los tickets |
| `observability.ws.revalidate-interval` | `60s` | Revisión de los roles de los conectados |
| `observability.scheduling.enabled`, `events.enabled`, `audit.enabled` | `true` | Los tests los apagan |
| `audit.export.zone` | `America/Argentina/Buenos_Aires` | Hora de las fechas del CSV de auditoría |

Sin Docker necesita Postgres (base `observability`; con un volumen viejo:
`docker exec postgres-dev psql -U postgres -c "CREATE DATABASE observability"`), Redis y Eureka.

## Límites conocidos

- El estado del recolector está en memoria: con varias instancias cada una tiene el suyo.
- "Conectado" es aproximado: alguien que cerró el navegador figura hasta 5 minutos.
- Las visitas al catálogo cuentan cualquier request a la primera página (recargas, bots) y se guardan 8 días.
- La etiqueta `tenant` en las métricas aguanta cientos de tiendas; con miles conviene contar el uso solo acá.
- Pendientes y decisiones abiertas: [observabilidad.md](../observabilidad.md#pendiente).
