# gateway

Spring Cloud Gateway (WebFlux, reactivo) en el puerto **3000**, registrado en Eureka como `gateway-microservice`.
Es la **única entrada pública**: el frontend nunca le habla directo a un servicio. No tiene endpoints propios:
rutea, reconoce la tienda, valida el login y protege.

Convenciones comunes (headers, errores, fechas): [README](README.md). Cómo se reconoce la tienda en detalle:
[tenants.md](../tenants.md).

## Qué hace

- **Rutea** cada prefijo al servicio que corresponde, por Eureka (`lb://...`).
- **Reconoce la tienda** por el header `Origin` y le pasa el schema a los servicios en `X-Forwarded-Host`.
- **Valida el JWT** en las rutas con login y le pasa el usuario a los servicios en `X-User-Id`. El token tiene que
  ser de la tienda de la request.
- **Protege**: borra los headers internos que mande el cliente, rate limit por IP y origen, CORS solo para las URLs
  de las tiendas y de la plataforma, token interno en `/api/internal/**`.
- **Observabilidad**: marca la última actividad de cada usuario logueado en Redis, pasa la IP del cliente en
  `X-Client-Ip` (para la auditoría) y etiqueta las métricas HTTP con la tienda.

Los servicios **confían** en `X-Forwarded-Host` y `X-User-Id` porque solo los pone el gateway.

## Recorrido de una request

Los filtros corren en este orden. Cada uno puede cortar la request; lo que responde está a la derecha.

| # | Filtro | Qué hace | Si corta |
|---|---|---|---|
| 1 | `ClientHeadersFilter` | Borra `X-User-Id`, `X-Forwarded-Host`, `X-Open-Path`, `X-Tenant-Name` y `X-Client-Ip` que mande el cliente. Pone `X-Client-Ip` con la IP de la conexión | — |
| 2 | `CorsConfig` | CORS (ver abajo). Va antes de los filtros que cortan, así sus 401, 404/403 y 429 salen con las cabeceras CORS y el navegador deja leer el código. Responde los preflight sin seguir | **403** sin cabeceras CORS si el origen no se acepta |
| 3 | `RequestFilter` | Rate limit por tipo de ruta, IP y origen (ver abajo) | **429**, cuerpo vacío |
| 4 | `MsFilter` | `/api/internal/**`: valida el token interno (`X-Internal-Token`) | **401**, cuerpo vacío |
| 5 | `TenantGatewayFilter` | Busca la tienda por el `Origin` y agrega `X-Forwarded-Host: <schema>`. Se saltea en `/api/v1/platform/**`, `/products/images/**` y el `/ws` del frontend de la plataforma | **404** `The tenant was not found.` (no existe o está borrada) · **403** `The tenant is disabled.` · **503** `Error de respuesta` si users no responde |
| 6 | `JwtFilter` | En las rutas con login valida el `Authorization: Bearer`, que el claim `tenant` del token sea el schema de la request (`public` en `/api/v1/platform/**`) y agrega `X-User-Id`. En las públicas agrega `X-Open-Path: true`; en las de login opcional, si viene token lo valida igual | **401**, cuerpo vacío (sin token, vencido, inválido o de otra tienda) |
| 7 | `ActivityFilter` | Marca `activity:<tenant>:<userId>` en Redis (TTL 5 min, como mucho cada 30 s por usuario). No frena la request | — |
| — | Ruteo | Manda la request al servicio | **503** si el servicio no está en Eureka |

`OPTIONS` siempre pasa el filtro de JWT. Las requests del puerto de gestión (9000) se saltean todos los filtros.

## Rutas

| Prefijo | Servicio | Tienda |
|---|---|---|
| `/api/v1/products/**`, `/api/v1/bundles/**`, `/api/v1/catalog/**` | products | Sí |
| `/products/images/**` | products | No |
| `/api/v1/platform/products/**` | products | No |
| `/api/v1/sales/**` | sales | Sí |
| `/api/v1/users/**`, `/api/v1/auth/**` | users | Sí |
| `/api/internal/tenants/**` | users | — (token interno) |
| `/api/v1/observability/**` | observability | Sí |
| `/api/v1/platform/observability/**` | observability | No |
| `/ws` | observability (WebSocket) | Según el ticket |
| `/api/v1/platform/**` (el resto) | users | No |

Las rutas de plataforma de products y observability tienen `order(-1)` para ganarle a `/api/v1/platform/**`.

### Públicas (sin token)

Están en `PermittedUrls.getExcludedPaths()`. Sin método valen para cualquiera; `{id}` es un número, `{code}` un
segmento y `{any}` cualquier cosa.

| Ruta |
|---|
| `/api/v1/auth/login`, `/api/v1/auth/register` |
| `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/forgot-password/{any}` |
| `POST /api/v1/platform/tenants`, `POST /api/v1/platform/auth/login` |
| `GET /api/v1/products/featured`, `GET /api/v1/products/{id}`, `GET /api/v1/products/{id}/metadata`, `GET /api/v1/products/search` |
| `GET /api/v1/catalog/products`, `GET /api/v1/catalog/products/{any}`, `GET /api/v1/catalog/categories`, `GET /api/v1/catalog/bundles` |
| `GET /api/v1/bundles/all`, `GET /api/v1/bundles/{id}` |
| `GET /api/v1/sales/checkout`, `POST /api/v1/sales/orders`, `POST /api/v1/sales/orders/lookup`, `POST /api/v1/sales/orders/{code}/receipt`, `POST /api/v1/sales/orders/{code}/cancel` |
| `/products/images/{any}` |
| `GET /ws` (entra con un ticket, ver [observability.md](observability.md#websocket)) |

Que una ruta sea pública en el gateway no quiere decir que el servicio la deje pasar sin login: el servicio
aplica sus propias reglas. Todo lo que no está en la lista pide token.

**Login opcional:** `POST /api/v1/sales/orders`. Sin token se crea como invitado; con token el pedido queda asociado
al usuario, y un token inválido responde 401.

## Rate limit

Ventanas de **1 minuto** por tipo de ruta, IP (o `X-Forwarded-For`) y `Origin`. Se cuenta en memoria
(Caffeine), así que con varias instancias del gateway cada una cuenta lo suyo.

| Tipo | Rutas | Límite por minuto | Propiedad |
|---|---|---|---|
| login | `/api/v1/auth/login`, `/api/v1/platform/auth/login` | 5 | `limit.login` |
| signup | `/api/v1/platform/tenants` | 3 | `limit.signup` |
| order | `/api/v1/sales/orders` (crear) | 5 | `limit.order` |
| lookup | `/api/v1/sales/orders/lookup`, `/api/v1/sales/orders/{code}/cancel` | 10 | `limit.lookup` |
| receipt | `/api/v1/sales/orders/{code}/receipt` | 5 | `limit.receipt` |
| global | Todo lo demás | 300 | `limit.global` |
| image | `/products/images/**` | Sin límite | — |

Con el límite en 5 pasan 5 requests y la sexta recibe **429** con el cuerpo vacío.

## CORS

- Se acepta un `Origin` si es:
  - la URL de una tienda que existe (aunque esté deshabilitada);
  - `platform.url` (el frontend de la plataforma);
  - un **subdominio de la plataforma** con forma de identificador (`<identificador>.<host de platform.url>`, mismo
    esquema y puerto, ej. `http://mitienda.localhost:2999`), **aunque la tienda no exista o esté borrada**. Así el
    404 de `TenantGatewayFilter` llega legible y el frontend muestra "esta tienda no existe". No consulta a users.

  Cualquier otro recibe **403** sin cabeceras CORS y el navegador corta la request.
- `CorsConfig` corre segundo (`@Order(-150)`), antes del rate limit, del tenant y del JWT: las respuestas que cortan
  esos filtros ya llevan las cabeceras CORS. Los preflight se responden ahí y no cuentan para el rate limit.
- Con origen aceptado: credenciales permitidas, todos los headers y métodos.
- La consulta va a la caché local, después Redis y por último a users; solo se cachean los orígenes permitidos.
- El CORS propio de Spring está apagado (`spring.webflux.cors.enabled=false`): lo hace `CorsConfig`.

## Configuración y resiliencia

| Propiedad | Valor en desarrollo | Para qué |
|---|---|---|
| `server.port` | `3000` | |
| `management.server.port` | `9000` | `/health`, `/ready`, `/metrics` (interno) |
| `jwt.secret` | — | Valida los JWT que emite users. **Cambiarlo fuera de desarrollo** |
| `api.internal.key` | — | Token interno entre servicios. **Cambiarlo fuera de desarrollo** |
| `platform.url` | `http://localhost:2999` | Frontend de la plataforma: CORS y `/ws` del superadmin |
| `tenant.url` | `lb://users-microservice/api/internal/tenants` | Dónde busca las tiendas |
| `users.url`, `products.url`, `sales.url`, `observability.url`, `observability.ws-url` | `lb://...` | Destinos de las rutas |
| `spring.data.redis.host` / `.port` | `localhost:6379` | Tiendas, CORS y última actividad |
| `limit.*` | ver arriba | Rate limit |
| `http.client.timeout` | `3s` | Timeout de las llamadas a users |
| `resilience4j.*.instances.users.*` | ver abajo | Circuit breaker y reintentos hacia users |

- **Búsqueda de la tienda:** caché local (Caffeine, 1 minuto) → Redis (`tenant:url:<url>`, lo escribe users) →
  `GET /api/internal/tenants/id?url=` en users. Deshabilitar o borrar una tienda tarda hasta 1 minuto en aplicarse.
- **Hacia users:** circuit breaker (si falla el 50 % de las últimas 10 llamadas corta 10 s y después prueba con 3),
  hasta 3 intentos con 200 ms de espera y timeout de 3 s. Los 4xx no cuentan como falla ni se reintentan.
- **Métricas:** las HTTP llevan la etiqueta `tenant` (el schema, `none` sin tienda) y el WS va con `uri="/ws"`.
  observability usa esa etiqueta para el tráfico por tienda.
- Logs de cada request por consola (`LoggingFilter`, `ForwardTimingFilter`).

## Límites conocidos

- Las respuestas que corta el gateway (401, 404/403 de la tienda, 429) tienen el cuerpo vacío o en texto: el
  frontend tiene que decidir por el código.
- El rate limit es por instancia (memoria) y usa `X-Forwarded-For` si viene, que el cliente puede inventar: detrás de
  un proxy habría que tomar la IP del proxy.
- `X-Client-Ip` es la IP de la conexión: detrás de un proxy sería la del proxy.
