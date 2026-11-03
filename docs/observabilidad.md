# Observabilidad (plan)

Plan para saber en todo momento cómo está la plataforma y cada tienda: salud de los servicios, métricas, trazas,
logs y un panel en **tiempo real por WebSocket**, a nivel **plataforma** (superadmin) y a nivel **tienda**
(administrador). La auditoría usa la misma base y está en [auditoria.md](auditoria.md).

> Estado: **etapa 1 hecha** (Actuator, trazas, logs, etiqueta `tenant` y stack de Docker: ver
> [Cómo se usa](#cómo-se-usa-etapa-1)). **Etapas 2 y 3: backend hecho** (`decor-observability`, ticket y WS,
> eventos, última actividad, auditoría con outbox y su feed en vivo); faltan las páginas de los paneles en el
> frontend y los canales `store.activity`, `store.usage` y `store.users`. La etapa 4 sigue en plan.

## Decisiones

| Tema | Decisión |
|---|---|
| Salud y métricas | **Spring Boot Actuator** en todos los servicios, configurado en `decor-common`: `/health`, `/ready` y `/metrics` |
| Acceso | **Puerto de gestión interno** por servicio, que el gateway no expone. Lo leen Docker, Prometheus y observability |
| Visualización | **Panel propio** en la app (tiempo real por WS) **+ Prometheus y Grafana** en Docker para lo técnico |
| Servicio | **MS nuevo `decor-observability`**: junta salud, métricas y eventos, guarda la auditoría y empuja todo por WS |
| Trazas y logs | **OpenTelemetry + Tempo + Loki**: cada request lleva un `traceId` de punta a punta y los logs salen en JSON con `traceId` y `tenant`. **Grafana Alloy** hace de colector (trazas por OTLP y logs de los contenedores) |
| Recolección | **Eventos + consulta periódica**: los servicios publican eventos en Redis Streams; la salud y las métricas técnicas se consultan cada 5–10 s |
| Protocolo | **WebSocket puro** con mensajes JSON propios (sin STOMP) |
| Autenticación del WS | **Ticket corto por REST**: un solo uso, 30 s. El JWT nunca va en la URL |
| Métricas por tienda | Las métricas HTTP llevan la etiqueta **`tenant`** |
| Usuarios conectados | Por **última actividad**: conectado = alguna request en los últimos 5 minutos |
| Ramas | **4 etapas**, una rama por etapa |

## /health, /ready y /metrics

| Ruta | Qué responde | Para qué |
|---|---|---|
| `/health` | **Liveness**: el proceso está vivo y no trabado | Docker / orquestador reinicia el servicio si falla |
| `/ready` | **Readiness**: puede atender (base, Redis, Eureka y lo que necesite cada servicio) | No mandarle tráfico hasta que esté listo |
| `/metrics` | Métricas en formato **Prometheus** | Prometheus y observability |
| `/status` | El detalle completo de la salud, componente por componente | Revisar a mano qué está fallando |

- Se sirven en el **puerto de gestión**: el del servicio + 6000 → users 9001, products 9002, sales 9003,
  observability 9004, gateway 9000, eureka 9761. Cada servicio lo fija en su `application.properties`; si no,
  `decor-common` lo calcula. El puerto se publica en la metadata de Eureka (`management.port`) y así lo encuentran
  Prometheus y observability.
- La configuración común está en `decor-common` (`ObservabilityEnvironmentPostProcessor`): son valores por defecto y
  lo que defina un servicio gana. `/health` incluye solo `livenessState` y `ping`; `/ready` incluye todos los
  indicadores del servicio (base, Redis, Eureka) menos `livenessState`.
- `/ready` **no** mira a los otros microservicios: si users se cae, products sigue listo (sus llamadas tienen circuit
  breaker). En users quedan afuera el mail y RabbitMQ.
- El gateway es reactivo: sus filtros (JWT, tenant, rate limit) también corren en el puerto de gestión, así que lo
  dejan pasar (`ManagementRequests`) y tiene una cadena de seguridad propia sin login.

## Qué se ve

### Plataforma (superadmin)

| Sección | Contenido |
|---|---|
| Estado de servicios | Cada servicio arriba o abajo, instancias registradas en Eureka, resultado de `/ready`, circuit breakers abiertos |
| Tráfico y errores | Requests por segundo, latencia (p50 / p95 / p99), errores 4xx y 5xx por servicio y endpoint, 429 del rate limit |
| Recursos | JVM (memoria, CPU, hilos, GC), pool de conexiones a la base, Redis, cachés (aciertos y fallos) |
| Negocio global | Tenants activos y altas, pedidos y facturación de toda la plataforma, ranking de tiendas por uso |

**Lo que ya existe sin observability.** El panel de la plataforma (`/dashboard` en el frontend) ya muestra tenants
por estado, altas por período y uso por tienda (usuarios, activos, última actividad, productos, combos) con REST:
`GET /api/v1/platform/admin/dashboard`, `/admin/usage` y `/platform/products/usage` (ver [panel-admin.md](panel-admin.md)).
observability **no los vuelve a calcular**: suma lo que falta (pedidos y facturación de la plataforma, tráfico,
salud) y el tiempo real (`platform.business` avisa de un alta nueva para refrescar ese panel).

**Última actividad.** Hoy `/admin/usage` toma el **último login** de cada tienda. Cuando el gateway marque la actividad
por request (`activity:<tenant>:<userId>`, ver abajo), ese endpoint debería pasar a usarla, y "usuarios activos"
podría usar la misma fuente.

### Tienda (administrador)

| Sección | Contenido |
|---|---|
| Actividad en vivo | Pedidos nuevos, comprobantes subidos, stock bajo y movimientos de stock, apenas pasan |
| Uso de la tienda | Visitas al catálogo, productos más vistos, requests y errores de su tienda |
| Usuarios conectados | Empleados con actividad en los últimos 5 minutos y su última actividad |
| Auditoría en vivo | Quién hizo qué en su tienda (ver [auditoria.md](auditoria.md)) |
| Ventas | **Dinero recaudado** e **historial de pedidos** |

Pedidos, comprobantes, recaudación e historial salen del módulo de [ventas](ventas.md) (`decor-sales`), que ya
existe: el panel de ventas y sus reportes (`/api/v1/sales/admin/reports/**`) son REST; observability suma los
eventos en vivo (pedido nuevo, comprobante subido) y no rehace los reportes.

## Componentes

### decor-common

- Configuración de Actuator (rutas, puerto de gestión, grupos de salud).
- Micrometer Tracing con OpenTelemetry y propagación del `traceId` en `RestTemplate` / `WebClient`.
- Logs en JSON con `traceId`, `spanId`, `tenant` y `userId` (MDC desde el `TenantContext`).
- Etiqueta `tenant` en las métricas HTTP.
- `DecorEventPublisher`: publica eventos en el stream `decor:events` de Redis (`type`, `tenant`, `at` en UTC y
  `data` en JSON), recortado a unos 10.000. Dentro de una transacción publica después del commit. Si Redis falla
  se loguea y la operación sigue: estos eventos son solo para el panel en vivo. La auditoría, que no se puede
  perder, va aparte con una tabla **outbox** (`com.decor.common.audit`, ver [auditoria.md](auditoria.md)).
- `ActivityKeys`: el formato de las keys de última actividad, que escribe el gateway y lee observability.
- Eventos que se publican hoy: users, al crear, habilitar, deshabilitar o borrar una tienda (`tenant.created`,
  `tenant.enabled`, `tenant.disabled`, `tenant.deleted`).

### decor-observability

- Proyecto Gradle propio con `decor-common`, registrado en Eureka, puerto 3004 (gestión 9004).
  El estado del recolector está en memoria. La base `observability` (un schema por tienda y `public` para la
  plataforma) guarda la auditoría, con JDBC y sin Hibernate (así no se activan el multi-tenant ni la captura de
  auditoría de `decor-common`, que son para los servicios que la mandan).
- **Auditoría** (`audit/`): consume el stream `decor:audit`, la guarda y la manda por el WS. Detalle en
  [auditoria.md](auditoria.md).
- **Recolector** (`collector/`): cada 10 s (`observability.poll.interval`) lee `/ready` y `/metrics` de cada
  instancia de Eureka que publica `management.port`, todas a la vez, con 2 s de timeout cada una. Eureka se lee
  aparte (`observability.static-targets`). De `/metrics` toma requests, 5xx, 4xx, 429 y el histograma de latencias
  (las tasas y los p50 / p95 / p99 salen de restar dos lecturas), heap, CPU, hilos, conexiones a la base y circuit
  breakers abiertos. No cuenta las requests del puerto de gestión ni las del WS.
  - Un servicio está `UP` si todas sus instancias están listas, `DEGRADED` si algunas y `DOWN` si ninguna. Los de
    `observability.services` que no tienen instancias en Eureka figuran `DOWN`.
  - Usuarios conectados: recorre las keys `activity:*` con `SCAN`.
- **Eventos** (`events/`): lee el stream `decor:events` **sin grupo de consumidores**, porque cada instancia tiene
  sus propios WS y tiene que ver todos los eventos. Empieza por los que llegan desde que arrancó. La auditoría (etapa
  3) va a usar un grupo aparte que guarde y confirme cada evento.
- **WebSocket** en `/ws`, ruteado por el gateway, y REST para lo que había antes de abrir el panel
  (`GET /api/v1/platform/observability/overview`, la última lectura del recolector). El WS manda solo lo nuevo.

### gateway

- Ruta `/ws` hacia observability (`lb:ws://`) y `/api/v1/platform/observability/**`. `/ws` no pide JWT (entra con
  el ticket) y desde el frontend de la plataforma no busca tienda. `/api/v1/observability/**` (panel y auditoría
  de la tienda) va a observability.
- `X-Client-Ip`: la IP de la conexión, para la auditoría (la que manda el cliente se borra).
- `ActivityFilter` marca en Redis la **última actividad** de cada usuario logueado (`activity:<tenant>:<userId>`,
  TTL 5 min; `public` para los superadmins), como mucho una vez cada 30 s por usuario y sin frenar la request.
- En las métricas, el WS va con `uri="/ws"` para que sus conexiones largas no se mezclen con las latencias.
- Pendiente: publicar el evento de tráfico por tenant que usa "uso de la tienda".

### Docker (desarrollo)

Prometheus, Grafana (con un dashboard ya cargado), Tempo, Loki y Alloy en
`backend/deploy/docker-compose.observability.yml`, que se suma al compose de desarrollo. Ver
[Cómo se usa](#cómo-se-usa-etapa-1).

## WebSocket

1. El frontend pide un ticket con su JWT:
   `POST /api/v1/platform/observability/ws-ticket` → `{ "ticket": "…", "expiresIn": 30 }` (superadmin) o
   `POST /api/v1/observability/ws-ticket` (administrador de la tienda; desde la plataforma responde 404).
   Se emite con los roles **actuales** (observability se los pide a users en el momento). Se guarda en Redis
   (`ws-ticket:<id>`) con el usuario y el tenant, dura **30 s** y sirve **una sola vez** (se lee y se borra con
   `GETDEL`).
2. Abre `ws://<gateway>/ws?ticket=…`. El gateway reconoce el tenant por el `Origin`; observability valida el
   ticket (sin ticket, vencido o usado: 401) y que su tenant sea el de la conexión (si no: 403).
   - **Superadmin**: el frontend de la plataforma (`platform.url` del gateway) no tiene tienda, así que el gateway
     acepta `/ws` desde ese `Origin` sin buscar tenant y la conexión es de `public`. Con ese ticket solo se puede
     suscribir a `platform.*`.
3. Se suscribe a canales con mensajes JSON:

```json
{ "type": "subscribe", "channel": "orders" }
```

```json
{ "type": "event", "channel": "orders", "data": { … }, "at": "2026-11-02T10:00:00Z" }
```

| Canal | Quién | Qué manda |
|---|---|---|
| `platform.health` | Superadmin | Cambios de estado de los servicios |
| `platform.traffic` | Superadmin | Resumen de tráfico, errores y recursos cada pocos segundos |
| `platform.business` | Superadmin | Altas de tenants, pedidos de la plataforma |
| `platform.audit` | Superadmin | Auditoría de plataforma |
| `store.activity` | Admin de la tienda | Pedidos, comprobantes, stock |
| `store.usage` | Admin de la tienda | Visitas, productos vistos, errores |
| `store.users` | Admin de la tienda | Usuarios conectados |
| `store.audit` | Admin de la tienda | Auditoría de su tienda |

Hoy están `platform.health` (se manda cuando cambia algún estado), `platform.traffic` (en cada lectura, con los
usuarios conectados), `platform.business` (los eventos `tenant.*`), `platform.audit` y `store.audit` (cada evento de
auditoría apenas se guarda). Faltan `store.activity`, `store.usage` y `store.users`.

Mensajes del cliente: `subscribe` y `unsubscribe` (con `channel`) y `ping`. El servidor responde `subscribed`,
`unsubscribed`, `pong` o `{ "type": "error", "message": … }` sin cortar la conexión.

- Los canales `store.*` son siempre **de la tienda del ticket**: no se puede pedir el canal de otra, y un ticket de
  tienda no puede suscribirse a los `platform.*` (ni al revés).
- Ping cada 30 s; si se corta, el frontend pide un ticket nuevo y vuelve a conectarse.
- Un cliente que no lee (más de 5 s o 512 KB pendientes) se desconecta.
- **Acceso revocado**: quien perdió el rol (un superadmin desactivado, por ejemplo) no consigue tickets nuevos. Y
  cada minuto observability vuelve a pedir los roles de los conectados y **cierra el WS** de quien ya no lo tiene
  (código 1008), el mismo minuto en que se le corta el REST. Si users no responde no se desconecta a nadie.

## Cómo se usa (etapa 1)

```bash
docker compose -f backend/deploy/docker-compose.dev.yml -f backend/deploy/docker-compose.observability.yml up -d
```

| Qué | Dónde |
|---|---|
| Grafana (sin login en desarrollo) | http://localhost:3300 → carpeta **Decor**, dashboard **Decor - Servicios** |
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
- El dashboard **Decor - Servicios** muestra servicios arriba o abajo, requests por segundo, latencia p95, errores 5xx
  y 4xx (el 429 es el rate limit), requests por tienda en el gateway, los endpoints más lentos, heap, CPU y conexiones
  a la base. No cuenta las requests del puerto de gestión.
- Los healthchecks del compose de desarrollo usan `/health` del puerto de gestión.
- Muestreo de trazas: 100 % (`management.tracing.sampling.probability`, se puede bajar por servicio).
- Los datos del stack no tienen volumen: se pierden al borrar los contenedores. Prometheus guarda 3 días y Tempo 72 h.

## Etapas

| # | Rama | Qué incluye | Estado |
|---|---|---|---|
| 1 | `feature/obs-base` | Actuator (`/health`, `/ready`, `/metrics`) en todos los servicios con puerto de gestión, trazas OpenTelemetry, logs JSON con `traceId` y `tenant`, etiqueta `tenant`, Docker con Prometheus, Grafana, Tempo, Loki y Alloy | **Hecha** |
| 2 | `feature/obs-servicio` | MS `decor-observability`, recolector, Redis Streams, ticket y WS, panel de plataforma, última actividad | **Backend hecho**; falta la página del panel |
| 3 | `feature/auditoria` | Auditoría con outbox (ver [auditoria.md](auditoria.md)), feed en vivo y panel de tienda | **Backend de la auditoría hecho**; faltan el panel de la tienda en el frontend y sus canales de actividad, uso y usuarios |
| 4 | `docs/observabilidad` | Documentación final y `MVP.md` | Plan |

## Para decidir al arrancar

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
- El estado del recolector está en memoria: con varias instancias de observability cada una tiene el suyo (leen lo
  mismo, así que solo difieren por unos segundos).
- Los eventos de `decor:events` (no la auditoría) se pierden si Redis está caído al publicarlos. Solo afecta al
  panel en vivo.
- Las respuestas de error de observability (400, 404) salen por `/error`, que no pide login.
- `/admin/usage` de users sigue tomando el **último login** como última actividad; falta que use las keys
  `activity:*`.
- Los logs del gateway llevan `traceId` pero no `tenant` ni `userId` (es reactivo y el MDC no se arma por request);
  el tenant del gateway sí está en las métricas.
