# eureka

Spring Cloud Netflix Eureka Server en el puerto **8761**. Es el registro de servicios: cada uno se registra con su
nombre y los demás lo encuentran por ahí (`lb://<nombre>` en el gateway, `http://<nombre>/` en los `RestTemplate`
con balanceo). No tiene endpoints de la app.

Convenciones comunes: [README](README.md).

## Quién se registra

| Servicio | Nombre en Eureka | Puerto | Gestión |
|---|---|---|---|
| gateway | `gateway-microservice` | 3000 | 9000 |
| users | `users-microservice` | 3001 | 9001 |
| products | `products-microservice` | 3002 | 9002 |
| sales | `sales-microservice` | 3003 | 9003 |
| observability | `observability-microservice` | 3004 | 9004 |

- Se registran con la IP (`eureka.instance.prefer-ip-address=true`).
- Cada instancia publica su puerto de gestión en la metadata (`management.port`): con eso Prometheus y observability
  encuentran `/ready` y `/metrics` de cada una sin configurar nada. Una instancia nueva aparece sola.
- Eureka no se registra en sí mismo: observability lo lee aparte (`observability.static-targets`) y Prometheus
  también.

## Qué se ve

- Panel: http://localhost:8761 (instancias registradas y su estado).
- Salud: `/health`, `/ready`, `/metrics` y `/status` en el puerto de gestión **9761**.

## Configuración

| Propiedad | Valor | Para qué |
|---|---|---|
| `server.port` | `8761` | |
| `eureka.client.register-with-eureka` / `fetch-registry` | `false` | Es el servidor, no un cliente |
| `management.server.port` | `9761` | Puerto de gestión |

En los clientes: `eureka.client.service-url.defaultZone=http://localhost:8761/eureka/` (en Docker, el nombre del
contenedor). En el compose de desarrollo los tiempos de descubrimiento están bajados para que un servicio que
arranca se vea antes; observability pide el registro cada 10 s
(`eureka.client.registry-fetch-interval-seconds`).

Levantarlo solo: `cd backend/eureka && ./gradlew bootRun`. Va primero: los demás servicios reintentan hasta
encontrarlo.

## Límites conocidos

- Una sola instancia, sin réplica: si se cae, los servicios siguen con el último registro que tenían en memoria,
  pero no ven instancias nuevas.
