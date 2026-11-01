# Observabilidad (plan)

Plan para saber en todo momento cómo está la plataforma y cada tienda: salud de los servicios, métricas, trazas,
logs y un panel en **tiempo real por WebSocket**, a nivel **plataforma** (superadmin) y a nivel **tienda**
(administrador). La auditoría usa la misma base y está en [auditoria.md](auditoria.md).

> Estado: **plan**. Cuando se implemente cada etapa, este archivo pasa a ser la documentación.

## Decisiones

| Tema | Decisión |
|---|---|
| Salud y métricas | **Spring Boot Actuator** en todos los servicios, configurado en `decor-common`: `/health`, `/ready` y `/metrics` |
| Acceso | **Puerto de gestión interno** por servicio, que el gateway no expone. Lo leen Docker, Prometheus y observability |
| Visualización | **Panel propio** en la app (tiempo real por WS) **+ Prometheus y Grafana** en Docker para lo técnico |
| Servicio | **MS nuevo `decor-observability`**: junta salud, métricas y eventos, guarda la auditoría y empuja todo por WS |
| Trazas y logs | **OpenTelemetry + Tempo + Loki**: cada request lleva un `traceId` de punta a punta y los logs salen en JSON con `traceId` y `tenant` |
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

- Se sirven en el **puerto de gestión** (propuesta: el del servicio + 6000 → users 9001, products 9002,
  sales 9003, observability 9004, gateway 9000, eureka 9761).
- Se arma con `management.endpoints.web.base-path=/`, mapeos de rutas y los grupos `liveness` / `readiness` de
  Actuator. La configuración común va en `decor-common` para que todos queden iguales.
- El gateway es reactivo: usa la misma configuración con los indicadores reactivos.

## Qué se ve

### Plataforma (superadmin)

| Sección | Contenido |
|---|---|
| Estado de servicios | Cada servicio arriba o abajo, instancias registradas en Eureka, resultado de `/ready`, circuit breakers abiertos |
| Tráfico y errores | Requests por segundo, latencia (p50 / p95 / p99), errores 4xx y 5xx por servicio y endpoint, 429 del rate limit |
| Recursos | JVM (memoria, CPU, hilos, GC), pool de conexiones a la base, Redis, cachés (aciertos y fallos) |
| Negocio global | Tenants activos y altas, pedidos y facturación de toda la plataforma, ranking de tiendas por uso |

### Tienda (administrador)

| Sección | Contenido |
|---|---|
| Actividad en vivo | Pedidos nuevos, comprobantes subidos, stock bajo y movimientos de stock, apenas pasan |
| Uso de la tienda | Visitas al catálogo, productos más vistos, requests y errores de su tienda |
| Usuarios conectados | Empleados con actividad en los últimos 5 minutos y su última actividad |
| Auditoría en vivo | Quién hizo qué en su tienda (ver [auditoria.md](auditoria.md)) |
| Ventas | **Dinero recaudado** e **historial de pedidos** |

Pedidos, comprobantes, recaudación e historial dependen del módulo de [ventas](ventas.md): se suman al panel cuando
exista.

## Componentes

### decor-common

- Configuración de Actuator (rutas, puerto de gestión, grupos de salud).
- Micrometer Tracing con OpenTelemetry y propagación del `traceId` en `RestTemplate` / `WebClient`.
- Logs en JSON con `traceId`, `spanId`, `tenant` y `userId` (MDC desde el `TenantContext`).
- Etiqueta `tenant` en las métricas HTTP.
- Cliente para publicar eventos en Redis Streams y la tabla **outbox** (ver [auditoria.md](auditoria.md)).

### decor-observability (nuevo)

- Proyecto Gradle propio con `decor-common`, registrado en Eureka. Base `observability` con un schema por tenant
  (y `public` para la plataforma), igual que los demás.
- **Recolector**: cada 5–10 s pregunta `/ready` y `/metrics` a cada instancia que figura en Eureka y guarda el último
  estado en memoria / Redis.
- **Consumidor de eventos**: lee de Redis Streams (grupo de consumidores), guarda la auditoría y los contadores de
  uso y reenvía cada evento a los WS suscriptos.
- **WebSocket** en `/ws`, ruteado por el gateway.
- REST para lo histórico (lo que pasó antes de abrir el panel); el WS manda solo lo nuevo.

### gateway

- Ruta `/ws/**` hacia observability (`lb:ws://`) y rutas REST `/api/v1/observability/**`.
- Marca en Redis la **última actividad** de cada usuario (`activity:<tenant>:<userId>`, TTL 5 min).
- Publica el evento de tráfico por tenant que usa "uso de la tienda".

### Docker (desarrollo)

Prometheus, Grafana (con dashboards ya cargados), Tempo, Loki y un colector de OpenTelemetry en un
`docker-compose.observability.yml`.

## WebSocket

1. El frontend pide un ticket con su JWT:
   `POST /api/v1/observability/ws-ticket` → `{ "ticket": "…", "expiresIn": 30 }`.
   El ticket se guarda en Redis con el usuario, el tenant y el rol, dura **30 s** y sirve **una sola vez**.
2. Abre `wss://<tienda>/ws?ticket=…`. El gateway reconoce el tenant por el `Origin`; observability valida el
   ticket y que su tenant sea el de la conexión.
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

- Los canales `store.*` siempre son **de la tienda del ticket**: no se puede pedir el canal de otra.
- Ping cada 30 s; si se corta, el frontend pide un ticket nuevo y vuelve a conectarse.

## Etapas

| # | Rama | Qué incluye |
|---|---|---|
| 1 | `feature/obs-base` | Actuator (`/health`, `/ready`, `/metrics`) en todos los servicios con puerto de gestión, trazas OpenTelemetry, logs JSON con `traceId` y `tenant`, etiqueta `tenant`, Docker con Prometheus, Grafana, Tempo y Loki |
| 2 | `feature/obs-servicio` | MS `decor-observability`, recolector, Redis Streams, ticket y WS, panel de plataforma, última actividad |
| 3 | `feature/auditoria` | Auditoría con outbox (ver [auditoria.md](auditoria.md)), feed en vivo y panel de tienda |
| 4 | `docs/observabilidad` | Documentación final y `MVP.md` |

## Para decidir al arrancar

- **Orden**: si va antes o después de [ventas](ventas.md) (quedó para mañana).
- Puertos de gestión exactos.
- Healthchecks de Docker y si `/ready` tiene en cuenta a los otros microservicios (por ejemplo, products sin users).
- Muestreo de trazas (100 % en desarrollo; en producción menos).
- Qué dashboards de Grafana se cargan y si hay alertas.
- Cuánto tiempo se guardan métricas, trazas y logs.
- Qué datos históricos guarda observability (los contadores de uso, por ejemplo) y con qué detalle.

## Límites conocidos

- La etiqueta `tenant` en las métricas está bien con cientos de tiendas; con miles conviene contar el uso por tienda
  solo en observability.
- "Conectado" es una aproximación: alguien que cerró el navegador figura hasta 5 minutos.
