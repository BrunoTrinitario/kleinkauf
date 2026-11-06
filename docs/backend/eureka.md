# eureka

Spring Cloud Netflix Eureka Server on port **8761**. It is the service registry: each service registers under its name
and the others find it there (`lb://<name>` in the gateway, `http://<name>/` in load-balanced `RestTemplate`s). It has
no application endpoints.

Shared conventions: [README](README.md).

## Who registers

| Service | Name in Eureka | Port | Management |
|---|---|---|---|
| gateway | `gateway-microservice` | 3000 | 9000 |
| users | `users-microservice` | 3001 | 9001 |
| products | `products-microservice` | 3002 | 9002 |
| sales | `sales-microservice` | 3003 | 9003 |
| observability | `observability-microservice` | 3004 | 9004 |

- They register by IP (`eureka.instance.prefer-ip-address=true`).
- Each instance publishes its management port in its metadata (`management.port`): that is how Prometheus and
  observability find each one's `/ready` and `/metrics` without any configuration. A new instance shows up on its own.
- Eureka does not register itself: observability reads it separately (`observability.static-targets`), and so does
  Prometheus.

## What you can see

- Dashboard: http://localhost:8761 (registered instances and their status).
- Health: `/health`, `/ready`, `/metrics` and `/status` on management port **9761**.

## Configuration

| Property | Value | Purpose |
|---|---|---|
| `server.port` | `8761` | |
| `eureka.client.register-with-eureka` / `fetch-registry` | `false` | It is the server, not a client |
| `management.server.port` | `9761` | Management port |

In the clients: `eureka.client.service-url.defaultZone=http://localhost:8761/eureka/` (in Docker, the container name).
The development compose lowers the discovery timings so a starting service shows up sooner; observability fetches the
registry every 10 s (`eureka.client.registry-fetch-interval-seconds`).

Running it alone: `cd backend/eureka && ./gradlew bootRun`. It goes first: the other services keep retrying until they
find it.

## Known limitations

- A single instance, no replica: if it goes down, the services keep the last registry they had in memory but do not
  see new instances.
