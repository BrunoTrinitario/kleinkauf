# decor-common

Librería compartida, **no es un servicio**. Es un build compuesto de Gradle: cada servicio la incluye con
`includeBuild('../decor-common')` en su `settings.gradle` y la usa como dependencia, sin publicarla. Casi todo se
autoconfigura: el servicio la agrega y los beans aparecen solos (cada uno se puede reemplazar definiendo el propio).

Convenciones comunes: [README](README.md).

## Qué trae y quién lo usa

| Paquete | Qué hay | gateway | users | products | sales | observability |
|---|---|---|---|---|---|---|
| `constants` | `Headers`, `TenantConstants` (`public`, el claim `tenant`), `ActivityKeys`, `UsageKeys` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `dto` | `TenantDto`, `RoleDto` y los de reservas de stock (`dto.stock`) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `security` | `InternalTokenService` (token entre servicios), `PermissionArea` (áreas y máscaras) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `servlet.tenant` | `TenantFilter`, `TenantContext`, Hibernate multi-tenant por schema (`TenantIdentifierResolver`, `DatabaseMultiTenantConnectionProvider`), `TenantSchemaManager` | | ✓ | ✓ | ✓ | |
| `servlet.security` | `CustomUserDetails`: authorities `ROLE_<rol>` y `PERM_<área>` | | ✓ | ✓ | ✓ | ✓ |
| `servlet.exceptions` | `ApiException` (mensaje + código) y `GlobalExceptionHandler` | | ✓ | ✓ | ✓ | |
| `events` | `DecorEvent` y `DecorEventPublisher` (Redis Streams `decor:events`) | | ✓ | ✓ | ✓ | ✓ (lee) |
| `audit` | `@Audited`, `@AuditIgnore`, `AuditLog`, outbox y `AuditEvent` | | ✓ | ✓ | ✓ | ✓ (lee) |
| `observability` | Actuator en el puerto de gestión, trazas, logs con `tenant` y `userId`, etiqueta `tenant` (`TenantTag`) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `autoconfigure` | `RedisTemplate<String,Object>` (JSON), caché Caffeine (`caffeineCache`, 10 min) | ✓ | ✓ | ✓ | ✓ | ✓ |

## Cómo usarla en un servicio nuevo

1. **Gradle:** `includeBuild('../decor-common')` en `settings.gradle` y la dependencia en `build.gradle` (copiar de
   `decor-sales`). Sumarlo también al `backend/settings.gradle` para importarlo con el resto.
2. **Propiedades mínimas:**

| Propiedad | Para qué |
|---|---|
| `spring.application.name` | Nombre en Eureka y en métricas y auditoría |
| `server.port` | El puerto de gestión queda en este + 6000 si no se fija `management.server.port` |
| `api.internal.key` | Crea el `InternalTokenService` (la misma clave en todos los servicios) |
| `spring.data.redis.*` | Activa `RedisTemplate`, eventos y el publicador de la auditoría |
| `decor.tenant.required` | `true` por defecto: sin `X-Forwarded-Host` responde 404. `false` = sin tienda trabaja en `public` |
| `decor.tenant.public-paths` | Prefijos que pasan sin tienda (ej. `/products/images/,/api/v1/platform/`) |

3. **Multi-tenant:** implementar un bean `TenantSchemaManager` (ver `TenantDataSource` de products o sales) que cree y
   migre el schema de una tienda la primera vez que se usa. Con eso `DecorServletAutoConfiguration` arma el
   `EntityManagerFactory` multi-tenant: cada request usa el schema de `TenantContext`.
4. **Seguridad:** un `HeaderAuthFilter` propio que lea `X-User-Id`, pida los roles a users y arme
   `CustomUserDetails`; y un `SecurityConfig` con el `TenantFilter` antes. Copiar el de sales o products.
5. **Errores:** tirar `ApiException(mensaje, HttpStatus)`; `GlobalExceptionHandler` responde el mensaje en texto
   con ese código.
6. **Auditoría:** marcar las entidades con `@Audited` (y los campos que no se registran con `@AuditIgnore`). Para lo
   que no es un alta, cambio o baja, inyectar `AuditLog` y llamar a `record(...)` o `read(entidad, id)`. Se apaga con
   `decor.audit.enabled=false`.
7. **Eventos para el panel en vivo:** inyectar `DecorEventPublisher` y `publish(tipo, tenant, datos)`. Dentro de una
   transacción sale después del commit; si Redis falla se loguea y sigue. Sumar el tipo a `DecorEvent` y su ruteo en
   el `EventStreamListener` de observability.
8. **Gateway:** la ruta en `GatewayRoutes` y, si hay rutas públicas, en `PermittedUrls`.

## Contratos entre servicios

### Headers

| Header | Lo pone | Qué lleva |
|---|---|---|
| `X-Forwarded-Host` | gateway (y el servicio que llama a otro, el mismo valor) | Schema de la tienda |
| `X-User-Id` | gateway | Id del usuario del JWT |
| `X-Open-Path` | gateway | `true` en rutas públicas |
| `X-Client-Ip` | gateway | IP de la conexión (para la auditoría) |
| `X-internal-request` | El servicio que llama | `true` en llamadas internas |
| `X-Internal-Token` | El servicio que llama | JWT interno |

El gateway borra `X-User-Id`, `X-Forwarded-Host`, `X-Open-Path`, `X-Tenant-Name` y `X-Client-Ip` si los manda el
cliente.

### Token interno

JWT firmado con `api.internal.key` (distinta a `jwt.secret` de los usuarios), con el nombre del servicio como
subject y **10 segundos** de vida: se genera uno por llamada con `generateMicroServiceToken(nombre)` y se valida
con `validateInternalToken(token)`. products les da el rol `INTERNAL` (lo piden sus `/api/internal/**`); users solo
lo valida cuando viene `X-internal-request: true`.

### JWT de usuario

Lo emite users (`jwt.secret`) con `id`, `email`, `name` y `tenant` (el schema, o `public`). Dura 1 hora. El gateway
lo valida y compara el `tenant` con la tienda de la request.

### Roles y permisos

`RoleDto { type, permissions }`: `permissions` es la máscara de `PermissionArea` (`PRODUCTOS`=1, `PRECIOS`=2,
`STOCK`=4, `COMBOS`=8, `USUARIOS`=16). `CustomUserDetails` da `ROLE_<type>` por cada rol y `PERM_<área>` por la
unión de las máscaras: en `SecurityConfig` se usa `hasRole("ADMINISTRADOR")` o
`hasAuthority(PermissionArea.STOCK.authority())`.

### Redis compartido

| Key / stream | Escribe | Lee | Contenido |
|---|---|---|---|
| `user:<id>:<schema>` (hash `roles`) | users | gateway, products, sales, observability | Roles del usuario (1 h) |
| `tenant:<schema>`, `tenant:url:<url>` (hash `data`) | users | gateway, products, sales | `TenantDto` (1 h) |
| `gateway:cors:<url>` | users | gateway | Origen permitido |
| `activity:<schema>:<userId>` | gateway | users, observability | Hora de la última request en ms (5 min). `ActivityKeys` |
| `usage:<schema>:<yyyy-MM-dd>` (hash) | products | observability | Visitas al catálogo y vistas por producto (8 días). `UsageKeys` |
| `decor:events` (stream) | users, products, sales | observability | `DecorEvent`: `type`, `tenant`, `at` (UTC), `data` (JSON). Recortado a ~10.000 |
| `decor:audit` (stream) | outbox de users, products, sales | observability | `AuditEvent` |

### Eventos (`DecorEvent`)

| Tipo | Publica | `tenant` | `data` |
|---|---|---|---|
| `tenant.created`, `tenant.enabled`, `tenant.disabled`, `tenant.deleted` | users | `public` | `id`, `identifier`, `schema` |
| `order.created`, `order.receipt-uploaded` | sales | La tienda | `number`, `channel`, `status`, `total`, `buyerName` |
| `stock.movement` | products | La tienda | `productId`, `productName`, `movementType`, `quantity`, `stockAfter`, `reason`, `userId` |
| `stock.low` | products | La tienda | `productId`, `productName`, `stock`, `minStock` |

Son para el panel en vivo: si Redis falla se pierden. La auditoría, que no se puede perder, va por la tabla
`public.outbox_events` de cada base y un publicador que la vacía hacia `decor:audit` (cada 2 s,
`decor.audit.publish-interval`). Formato en [observability.md](observability.md#formato-de-la-auditoría).

### Observabilidad

`ObservabilityEnvironmentPostProcessor` pone valores por defecto (lo que defina el servicio gana):

- Actuator en el puerto de gestión (`server.port + 6000`) con `/health`, `/ready`, `/metrics` y `/status`; el
  puerto se publica en Eureka (`management.port` en la metadata).
- Histograma de latencias de las requests HTTP y la etiqueta `application`.
- Trazas con OpenTelemetry (el envío por OTLP lo prende el stack de Docker) y logs con `traceId`, `spanId`,
  `tenant` y `userId`.
