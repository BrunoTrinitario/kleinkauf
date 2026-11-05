# Observabilidad

Cómo saber en todo momento cómo está la plataforma y cada tienda: salud de los servicios, métricas, trazas, logs y
un panel en **tiempo real por WebSocket**, a nivel **plataforma** (superadmin) y a nivel **tienda**
(administrador). La auditoría usa la misma base y está en [auditoria.md](auditoria.md).

> Estado: **hecho**. Backend en las tres etapas de código (base técnica, servicio `kleinkauf-observability` con su WS
> y auditoría) y frontend: `/estado` y `/auditoria` en la plataforma, `/admin/actividad` y `/admin/auditoria` en
> cada tienda (ver [frontend.md](frontend.md), sección 9).

## Decisiones

| Tema | Decisión |
|---|---|
| Salud y métricas | **Spring Boot Actuator** en todos los servicios, configurado en `kleinkauf-common`: `/health`, `/ready` y `/metrics` |
| Acceso | **Puerto de gestión interno** por servicio, que el gateway no expone. Lo leen Docker, Prometheus y observability |
| Visualización | **Panel propio** en la app (tiempo real por WS) **+ Prometheus y Grafana** en Docker para lo técnico |
| Servicio | **MS `kleinkauf-observability`**: junta salud, métricas y eventos, guarda la auditoría y empuja todo por WS |
| Trazas y logs | **OpenTelemetry + Tempo + Loki**: cada request lleva un `traceId` de punta a punta y los logs salen en JSON con `traceId` y `tenant`. **Grafana Alloy** hace de colector (trazas por OTLP y logs de los contenedores) |
| Recolección | **Eventos + consulta periódica**: los servicios publican eventos en Redis Streams; la salud y las métricas técnicas se consultan cada 10 s |
| Protocolo | **WebSocket puro** con mensajes JSON propios (sin STOMP) |
| Autenticación del WS | **Ticket corto por REST**: un solo uso, 30 s. El JWT nunca va en la URL |
| Acceso revocado | Cada minuto se revisan los roles de los conectados y se **cierra el WS** de quien ya no tiene el rol |
| Métricas por tienda | Las métricas HTTP llevan la etiqueta **`tenant`** |
| Usuarios conectados | Por **última actividad**: conectado = alguna request en los últimos 5 minutos |

## /health, /ready y /metrics

| Ruta | Qué responde | Para qué |
|---|---|---|
| `/health` | **Liveness**: el proceso está vivo y no trabado | Docker / orquestador reinicia el servicio si falla |
| `/ready` | **Readiness**: puede atender (base, Redis, Eureka y lo que necesite cada servicio) | No mandarle tráfico hasta que esté listo |
| `/metrics` | Métricas en formato **Prometheus** | Prometheus y observability |
| `/status` | El detalle completo de la salud, componente por componente | Revisar a mano qué está fallando |

- Se sirven en el **puerto de gestión**: el del servicio + 6000 → users 9001, products 9002, sales 9003,
  observability 9004, gateway 9000, eureka 9761. Cada servicio lo fija en su `application.properties`; si no,
  `kleinkauf-common` lo calcula. El puerto se publica en la metadata de Eureka (`management.port`) y así lo encuentran
  Prometheus y observability.
- La configuración común está en `kleinkauf-common` (`ObservabilityEnvironmentPostProcessor`): son valores por defecto y
  lo que defina un servicio gana. `/health` incluye solo `livenessState` y `ping`; `/ready` incluye todos los
  indicadores del servicio (base, Redis, Eureka) menos `livenessState`.
- `/ready` **no** mira a los otros microservicios: si users se cae, products sigue listo (sus llamadas tienen circuit
  breaker). En users quedan afuera el mail y RabbitMQ.
- El gateway es reactivo: sus filtros (JWT, tenant, rate limit) también corren en el puerto de gestión, así que lo
  dejan pasar (`ManagementRequests`) y tiene una cadena de seguridad propia sin login.

## Qué se ve

### Plataforma (superadmin)

| Sección | Contenido | Estado |
|---|---|---|
| Estado de servicios | Cada servicio arriba, degradado o abajo; sus instancias de Eureka con el resultado de `/ready` por componente; circuit breakers abiertos | Hecho (`overview` y `platform.health`) |
| Tráfico y errores | Requests por segundo, latencia p50 / p95 / p99, errores 5xx y 4xx, 429 del rate limit, por servicio | Hecho (`platform.traffic`). Por endpoint: en Grafana |
| Recursos | Heap, CPU, hilos y conexiones a la base en uso, por servicio | Hecho. Redis y aciertos de caché: pendiente |
| Usuarios conectados | Total y por tienda | Hecho (`platform.traffic`) |
| Negocio global | Tenants por estado, altas y uso por tienda | Por REST en users y products ([panel-admin.md](panel-admin.md)); `platform.business` avisa cuando cambian |
| Auditoría | Quién hizo qué en la plataforma | Hecho (`platform.audit` y su historial) |
| Pedidos y facturación de la plataforma | Totales de todas las tiendas | Descartado: el superadmin no ve los datos de las tiendas |

observability **no vuelve a calcular** lo que ya da el panel de la plataforma
(`GET /api/v1/platform/admin/dashboard`, `/admin/usage` y `/platform/products/usage`): suma la salud, el tráfico y
el tiempo real.

### Tienda (administrador)

| Sección | Contenido | Estado |
|---|---|---|
| Auditoría en vivo | Quién hizo qué en su tienda (ver [auditoria.md](auditoria.md)) | Hecho (`store.audit` y su historial) |
| Ventas | **Dinero recaudado** e **historial de pedidos** | Por REST en [ventas](ventas.md) (`/api/v1/sales/admin/**`) |
| Actividad en vivo | Pedidos nuevos y ventas del local, comprobantes subidos, movimientos de stock cargados a mano y productos que pasan a stock bajo, apenas pasan | Hecho (`store.activity`) |
| Uso de la tienda | Visitas al catálogo y productos más vistos del día; requests y errores por segundo de su tienda | Hecho (`store.usage` y `GET /store/usage`) |
| Usuarios conectados | Usuarios con actividad en los últimos 5 minutos (todos los logueados, también clientes) y su última actividad | Hecho (`store.users` y `GET /store/users`) |

#### Actividad de la tienda (`store.activity`)

Eventos de `kleinkauf:events` de esa tienda, reenviados tal cual: `{ "type", "at", ...datos }`.

| `type` | Lo publica | Cuándo | Datos |
|---|---|---|---|
| `order.created` | sales | Pedido online creado o venta del local cargada | `number`, `channel` (`ONLINE`/`LOCAL`), `status`, `total`, `buyerName` |
| `order.receipt-uploaded` | sales | El comprador subió un comprobante (el pedido pasa a revisión) | Igual que `order.created` |
| `stock.movement` | products | Movimiento cargado a mano desde el panel (los de los pedidos no: ya llega el pedido) | `productId`, `productName`, `movementType`, `quantity`, `stockAfter`, `reason`, `userId` |
| `stock.low` | products | Un producto con mínimo **cruza** a stock bajo con cualquier movimiento, también los de los pedidos (no se repite mientras siga bajo) | `productId`, `productName`, `stock`, `minStock` |

Se publican después del commit (`KleinKaufEventPublisher`); si Redis falla no hay evento y la operación sigue. Son en
vivo: lo que pasó con el panel cerrado está en la auditoría y en los pedidos.

#### Uso y conectados (`store.usage` y `store.users`)

- En cada lectura del recolector (10 s), solo para las tiendas que tienen algún WS suscripto.
- **Tráfico**: requests, 4xx y 5xx por segundo de la tienda, de las métricas del **gateway** con la etiqueta
  `tenant` (cada request de una tienda pasa una vez por ahí; en los servicios se contaría de nuevo). El servicio
  sale de `observability.gateway-service`. Hasta la segunda lectura del gateway `traffic` es `null`.
- **Catálogo**: products cuenta en Redis, por tienda y por día (`usage:<tenant>:<yyyy-MM-dd>`, `UsageKeys` en
  `kleinkauf-common`), las **visitas** (la primera página del listado sin búsqueda) y las **vistas** del detalle de cada
  producto (por slug). Se mandan los 5 más vistos. Vencen a los 8 días. El día es el de Buenos Aires para todas las
  tiendas.
- **Conectados**: `SCAN` de `activity:<tenant>:*` con la hora de la última request y el nombre (de users, con caché).

```json
{ "at": "…", "date": "2026-11-03", "catalogVisits": 40,
  "topProducts": [ { "slug": "mesa-de-roble", "views": 12 } ],
  "traffic": { "requestsPerSecond": 2.5, "errors4xxPerSecond": 0.1, "errors5xxPerSecond": 0.0 } }
```

```json
{ "total": 2, "users": [ { "userId": 8, "name": "Beto", "lastActivity": "2026-11-03T13:00:05Z" } ] }
```

## Componentes

### kleinkauf-common

- Configuración de Actuator (rutas, puerto de gestión, grupos de salud).
- Micrometer Tracing con OpenTelemetry y propagación del `traceId` en `RestTemplate` / `WebClient`. El envío de
  trazas por OTLP está **apagado por defecto** (`management.otlp.tracing.export.enabled`) y lo prende el stack de
  Docker.
- Logs con `traceId`, `spanId`, `tenant` y `userId` (MDC desde el `TenantContext`): en texto por defecto y en JSON
  (logstash) con `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`.
- Etiqueta `tenant` en las métricas HTTP (`TenantTag`: solo valores con forma de schema; lo demás va como `invalid`).
- `KleinKaufEventPublisher`: publica eventos en el stream `kleinkauf:events` de Redis (`type`, `tenant`, `at` en UTC y
  `data` en JSON), recortado a unos 10.000. Dentro de una transacción publica después del commit. Si Redis falla
  se loguea y la operación sigue: estos eventos son solo para el panel en vivo. La auditoría, que no se puede
  perder, va aparte con una tabla **outbox** (`com.kleinkauf.common.audit`, ver [auditoria.md](auditoria.md)).
- Eventos que se publican hoy: users, al crear, habilitar, deshabilitar o borrar una tienda (`tenant.created`,
  `tenant.enabled`, `tenant.disabled`, `tenant.deleted`); sales, los pedidos (`order.created`,
  `order.receipt-uploaded`); products, el stock (`stock.movement`, `stock.low`).
- `UsageKeys`: el formato de los contadores de uso del catálogo, que escribe products y lee observability.
- `ActivityKeys`: el formato de las keys de última actividad, que escribe el gateway y lee observability.

### kleinkauf-observability

- Proyecto Gradle propio con `kleinkauf-common`, registrado en Eureka, puerto 3004 (gestión 9004). Base `observability`
  para la auditoría, con JDBC y sin Hibernate: así no se activan el multi-tenant ni la captura de auditoría de
  `kleinkauf-common`, que son para los servicios que la mandan.
- **Recolector** (`collector/`): cada 10 s (`observability.poll.interval`) lee `/ready` y `/metrics` de cada
  instancia de Eureka que publica `management.port`, todas a la vez, con 2 s de timeout cada una. Eureka se lee
  aparte (`observability.static-targets`). De `/metrics` toma requests, 5xx, 4xx, 429 y el histograma de latencias
  (las tasas y los percentiles salen de restar dos lecturas), heap, CPU, hilos, conexiones a la base y circuit
  breakers abiertos. No cuenta las requests del puerto de gestión ni las del WS. El estado queda en memoria.
  - Un servicio está `UP` si todas sus instancias están listas, `DEGRADED` si algunas y `DOWN` si ninguna. Los de
    `observability.services` que no tienen instancias en Eureka figuran `DOWN`.
  - Usuarios conectados: recorre las keys `activity:*` con `SCAN`.
- **Eventos** (`events/`): lee el stream `kleinkauf:events` **sin grupo de consumidores**, porque cada instancia tiene
  sus propios WS y tiene que ver todos los eventos. Empieza por los que llegan desde que arrancó. `tenant.*` va a
  `platform.business`; `order.*` y `stock.*` a `store.activity` de esa tienda.
- **Tienda** (`store/`): arma `store.usage` y `store.users` en cada lectura del recolector (`StoreFeeds`) y responde
  sus GET.
- **Auditoría** (`audit/`): consume el stream `kleinkauf:audit` con un grupo de consumidores, la guarda y la manda por
  el WS. Detalle en [auditoria.md](auditoria.md).
- **WebSocket** (`ws/`) en `/ws`, y REST para lo que había antes de abrir el panel. El WS manda solo lo nuevo.

### gateway

- Ruta `/ws` hacia observability (`lb:ws://`), `/api/v1/platform/observability/**` y `/api/v1/observability/**`.
  `/ws` no pide JWT (entra con el ticket) y desde el frontend de la plataforma no busca tienda.
- `ActivityFilter` marca en Redis la **última actividad** de cada usuario logueado (`activity:<tenant>:<userId>`,
  TTL 5 min; `public` para los superadmins), como mucho una vez cada 30 s por usuario y sin frenar la request.
- `X-Client-Ip`: la IP de la conexión, para la auditoría (la que manda el cliente se borra).
- En las métricas, el WS va con `uri="/ws"` para que sus conexiones largas no se mezclen con las latencias.

## Endpoints

Todos por el gateway. Las rutas de plataforma no tienen tienda (desde una tienda responden 404) y las de la tienda
tampoco se pueden pedir desde la plataforma (404).

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/v1/platform/observability/ws-ticket` | Superadmin | Ticket para el WS de la plataforma |
| GET | `/api/v1/platform/observability/overview` | Superadmin | Última lectura del recolector: salud, tráfico, recursos y usuarios conectados |
| GET | `/api/v1/platform/observability/audit` | Superadmin | Historial de auditoría de la plataforma ([auditoria.md](auditoria.md)) |
| POST | `/api/v1/observability/ws-ticket` | Admin de la tienda | Ticket para el WS de su tienda |
| GET | `/api/v1/observability/store/audit` | Admin de la tienda | Historial de auditoría de su tienda |
| GET | `/api/v1/observability/store/audit/{entity}/{entityId}` | Admin de la tienda | Historial de una cosa puntual |
| GET | `/api/v1/observability/store/users` | Admin de la tienda | Usuarios conectados (lo mismo que `store.users`) |
| GET | `/api/v1/observability/store/usage` | Admin de la tienda | Uso del catálogo del día y tráfico (lo mismo que `store.usage`) |
| GET | `/ws?ticket=…` | Con ticket | WebSocket |

## WebSocket

1. El frontend pide un ticket con su JWT (`POST …/ws-ticket` de arriba) → `{ "ticket": "…", "expiresIn": 30 }`.
   Se emite con los roles **actuales** (observability se los pide a users en el momento). Se guarda en Redis
   (`ws-ticket:<id>`) con el usuario y el tenant, dura **30 s** y sirve **una sola vez** (se lee y se borra con
   `GETDEL`).
2. Abre `ws://<gateway>/ws?ticket=…`. El gateway reconoce el tenant por el `Origin`; observability valida el
   ticket (sin ticket, vencido o usado: 401) y que su tenant sea el de la conexión (si no: 403).
   - **Superadmin**: el frontend de la plataforma (`platform.url` del gateway) no tiene tienda, así que el gateway
     acepta `/ws` desde ese `Origin` sin buscar tenant y la conexión es de `public`.
3. Se suscribe a canales con mensajes JSON:

```json
{ "type": "subscribe", "channel": "platform.health" }
```

```json
{ "type": "event", "channel": "platform.health", "data": [ … ], "at": "2026-11-02T10:00:00Z" }
```

| Canal | Quién | Qué manda | Estado |
|---|---|---|---|
| `platform.health` | Superadmin | La salud de todos los servicios, cuando cambia algún estado | Hecho |
| `platform.traffic` | Superadmin | Tráfico, recursos y usuarios conectados, en cada lectura (10 s) | Hecho |
| `platform.business` | Superadmin | Altas, bajas y cambios de tiendas (`tenant.*`) | Hecho |
| `platform.audit` | Superadmin | Cada evento de auditoría de la plataforma, apenas se guarda | Hecho |
| `store.audit` | Admin de la tienda | Cada evento de auditoría de su tienda, apenas se guarda | Hecho |
| `store.activity` | Admin de la tienda | Pedidos, ventas del local, comprobantes y stock, apenas pasan | Hecho |
| `store.usage` | Admin de la tienda | Visitas al catálogo, productos más vistos, requests y errores, en cada lectura (10 s) | Hecho |
| `store.users` | Admin de la tienda | Usuarios conectados, en cada lectura (10 s) | Hecho |

Mensajes del cliente: `subscribe` y `unsubscribe` (con `channel`) y `ping`. El servidor responde `subscribed`,
`unsubscribed`, `pong` o `{ "type": "error", "message": … }` sin cortar la conexión.

- Un ticket de plataforma solo se suscribe a `platform.*` y uno de tienda solo a `store.*`. Los `store.*` son siempre
  **de la tienda del ticket**: no se puede pedir el canal de otra.
- Ping cada 30 s; si se corta, el frontend pide un ticket nuevo y vuelve a conectarse.
- Un cliente que no lee (más de 5 s o 512 KB pendientes) se desconecta.
- **Acceso revocado**: quien perdió el rol (un superadmin desactivado, por ejemplo) no consigue tickets nuevos. Y
  cada minuto observability vuelve a pedir los roles de los conectados y **cierra el WS** de quien ya no lo tiene
  (código 1008), el mismo minuto en que se le corta el REST. Si users no responde no se desconecta a nadie.

## Cómo se usa

### Con Docker

```bash
docker compose -f backend/deploy/docker-compose.dev.yml -f backend/deploy/docker-compose.observability.yml up -d
```

El primer archivo levanta los servicios (observability incluido); el segundo suma Prometheus, Grafana, Tempo, Loki y
Alloy. Con el primero solo, todo anda igual pero sin ese stack.

| Qué | Dónde |
|---|---|
| Grafana (sin login en desarrollo) | http://localhost:3300 → carpeta **KleinKauf**, dashboard **KleinKauf - Servicios** |
| Prometheus | http://localhost:9090 (Status → Targets muestra cada instancia) |
| Trazas | Grafana → Explore → Tempo |
| Logs | Grafana → Explore → Loki, por ejemplo `{service="product-service", level="ERROR"}` o `{service=~".+"} \| tenant="demo"` |

- **Qué cambia en los servicios con el stack levantado**: el overlay les pone `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`
  (logs en JSON) y prende el envío de trazas a Alloy (`MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED`). Sin el stack, los
  logs salen en texto con `[servicio,traceId,spanId,tenant]` y las trazas no se mandan, para que no haya errores de
  conexión.
- **Prometheus** encuentra las instancias en Eureka (con su IP y el `management.port` de la metadata), así que una
  instancia nueva aparece sola. Eureka se lee aparte, porque no se registra en sí mismo.
- **Alloy** recibe las trazas por OTLP y las pasa a Tempo, y lee por el socket de Docker los logs de los contenedores de
  los servicios. En Loki el nivel es etiqueta; `traceId`, `tenant` y `userId` van como *structured metadata* (no
  crean una serie por valor).
- Logs y trazas están enlazados en Grafana: desde un log se abre su traza ("Ver traza") y desde una traza, sus logs.
- El dashboard **KleinKauf - Servicios** muestra servicios arriba o abajo, requests por segundo, latencia p95, errores 5xx
  y 4xx (el 429 es el rate limit), requests por tienda en el gateway, los endpoints más lentos, heap, CPU y conexiones
  a la base. No cuenta las requests del puerto de gestión.
- Los healthchecks del compose de desarrollo usan `/health` del puerto de gestión.
- Muestreo de trazas: 100 % (`management.tracing.sampling.probability`, se puede bajar por servicio).
- Los datos del stack no tienen volumen: se pierden al borrar los contenedores. Prometheus guarda 3 días y Tempo 72 h.
- La VM de Docker necesita memoria: con 4 GB, los servicios compilando con `bootRun` a la vez no entran. Conviene
  darle 6 GB o más (`memory=` en `.wslconfig` en Windows) o levantarlos de a uno la primera vez.

### Sin Docker (bootRun)

observability necesita Postgres (base `observability`), Redis y Eureka, igual que los demás. `init.sql` crea la base;
con un volumen de Postgres de antes hay que crearla a mano:

```bash
docker exec postgres-dev psql -U postgres -c "CREATE DATABASE observability"
```

## Etapas

| # | Rama | Qué incluye | Estado |
|---|---|---|---|
| 1 | `feature/obs-base` | Actuator (`/health`, `/ready`, `/metrics`) en todos los servicios con puerto de gestión, trazas OpenTelemetry, logs JSON con `traceId` y `tenant`, etiqueta `tenant`, Docker con Prometheus, Grafana, Tempo, Loki y Alloy | **Hecha** |
| 2 | `feature/obs-servicio` | MS `kleinkauf-observability`, recolector, Redis Streams, ticket y WS, panel de plataforma, última actividad | **Backend hecho** |
| 3 | `feature/auditoria` | Auditoría con outbox ([auditoria.md](auditoria.md)), feed en vivo y panel de tienda | **Backend de la auditoría hecho** |
| 4 | `docs/observabilidad` | Documentación final y `MVP.md` | **Hecha** |

Cada rama sale de la anterior.

## Pendiente

- **Historial de una cosa puntual** dentro de su pantalla (pedido, producto, usuario): el endpoint ya está.
- **Prueba de punta a punta con Docker**: todo junto con Postgres y Redis reales (los tests usan H2 y mocks).

## Para decidir

- Muestreo de trazas en producción (en desarrollo es 100 %).
- Alertas en Grafana y más dashboards (por ahora hay uno, sin alertas).
- Cuánto tiempo se guardan métricas, trazas y logs fuera de desarrollo.
- Si los healthchecks de Docker pasan a usar `/ready` (hoy usan `/health`) para que `depends_on` espere a que el
  servicio pueda atender y no solo a que arrancó.
- Qué datos históricos guarda observability (los contadores de uso, por ejemplo) y con qué detalle.

## Límites conocidos

- La etiqueta `tenant` en las métricas está bien con cientos de tiendas; con miles conviene contar el uso por tienda
  solo en observability.
- "Conectado" es una aproximación: alguien que cerró el navegador figura hasta 5 minutos.
- Las visitas al catálogo son requests a la primera página del listado: un bot o recargar la página también cuentan.
  No hay historial de uso más allá de 8 días (ver "Para decidir").
- El estado del recolector está en memoria: con varias instancias de observability cada una tiene el suyo (leen lo
  mismo, así que solo difieren por unos segundos).
- Los eventos de `kleinkauf:events` (no la auditoría) se pierden si Redis está caído al publicarlos. Solo afecta al
  panel en vivo.
- Los logs del gateway llevan `traceId` pero no `tenant` ni `userId` (es reactivo y el MDC no se arma por request);
  el tenant del gateway sí está en las métricas.
