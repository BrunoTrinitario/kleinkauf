# Auditoría

Registro de quién hizo qué, cuándo y qué cambió, en cada tienda y en la plataforma, y verlo en tiempo real.
Se apoya en el servicio de [observabilidad](observabilidad.md).

> Estado: **backend hecho** (captura, outbox, guardado, consultas y WS). Falta la vista en el frontend.

## Decisiones

| Tema | Decisión |
|---|---|
| Qué se audita | **Cambios de datos**, **acciones de plataforma** y **lecturas sensibles**. Los logins no |
| Quién cuenta | Solo lo que hace **una persona**: una request con usuario o una pública (el alta por autoservicio). Las llamadas internas entre servicios (sales reservando stock en products) y lo que corre sin request (tareas programadas, el arranque) no se registran |
| Detalle | Quién, cuándo, IP, acción, entidad e id, y **solo los campos que cambiaron, con valor anterior y nuevo**. Nunca contraseñas ni tokens |
| Cómo se captura | **Listener de Hibernate** para las entidades `@Audited`; a mano con `AuditLog` para lo demás |
| Cómo llega | **Outbox**: la fila se guarda en la misma transacción que el cambio y después se publica en Redis Streams. **No se pierde** aunque observability o Redis estén caídos |
| Outbox | **Una sola tabla por servicio**, `public.outbox_events`, con columna `tenant` (no una por schema): se escribe con la misma conexión que el cambio y el publicador lee una sola tabla |
| Dónde se guarda | En **`decor-observability`**, base `observability`: `<schema>.audit_events`, un schema por tienda y `public` para la plataforma |
| Retención | **Para siempre**. Nadie la edita ni la borra |
| Quién la ve | **Cada uno lo suyo**: el administrador ve la de su tienda y el superadmin la de la plataforma. El superadmin **no** ve la de las tiendas |
| Nombres | Observability busca el nombre del usuario en users **al guardar** y lo deja guardado: si después lo borran, su auditoría sigue diciendo quién era |
| Tienda borrada | Su auditoría se conserva, pero no se consulta desde la app (su administrador ya no entra y el superadmin no ve las tiendas). Queda en la base |
| En vivo | Canales `store.audit` y `platform.audit` del WebSocket |

## Qué se registra

### Cambios de datos (tienda), automáticos

Entidades `@Audited`: cada alta (`CREATE`), cambio (`UPDATE`) y baja (`DELETE`).

| Servicio | Entidades |
|---|---|
| products | `Product` (precio y costo incluidos), `Bundle`, `BundleProduct`, `ProductImage`, `ProductMetadata`, `StockMovement` |
| users | `User`, `Role` (asignar y quitar roles), `RolePermission` (permisos de un rol), `Tenant` |
| sales | `SalesSettings` |

- Un borrado lógico (`deletedAt` de vacío a una fecha) se registra como `DELETE`.
- Las colecciones no se registran: se audita la entidad hija (ej. cada `ProductImage`). Una relación a otra
  entidad se guarda con su id.
- Si en un cambio solo cambiaron campos ignorados (`@AuditIgnore`, ej. el último login), no se registra nada.

### A mano (`AuditLog`)

| Servicio | Qué | Acción y entidad |
|---|---|---|
| products | Importación CSV: **una** entrada con el resumen (creados, actualizados), no una por producto | `IMPORT` `Product` |
| sales | Cambios de estado de pedidos del administrador (validar, rechazar, cancelar…), con el motivo | `STATUS_CHANGE` `Order` (número) |
| users | Desactivar y reactivar superadmins (van con SQL directo, no pasan por Hibernate) | `DEACTIVATE` / `ACTIVATE` `Superadmin` |

El alta de superadmins sí pasa por Hibernate (`User` y `Role` en `public`).

### Acciones de plataforma

Las de users sobre tiendas (`Tenant`) y superadmins quedan con `tenant = "public"`. Si las hace un superadmin,
`actor.role` es `SUPERADMIN`. El alta por **autoservicio** no tiene usuario: `actor` va en `null` y queda la IP.
El dueño que se crea con la tienda queda en la auditoría de esa tienda (`User` `CREATE`).

Leer el panel de la plataforma (resumen, uso por tienda, lista de superadmins) **no** se audita.

### Lecturas sensibles (`READ`)

| Qué | Entidad |
|---|---|
| Ver costo y margen (`GET /products/{id}/pricing`) | `ProductPricing` (id del producto) |
| Exportar el CSV de productos | `ProductExport` |
| Descargar un comprobante de pago | `PaymentReceipt` (id del comprobante) |

## Formato del evento

```json
{
  "id": "0b6f…",
  "tenant": "mi_tienda",
  "at": "2026-11-02T10:15:00Z",
  "service": "products",
  "actor": { "userId": 3, "name": "Ana", "role": "ADMINISTRADOR" },
  "ip": "190.1.2.3",
  "traceId": "4bf92f3577b34da6",
  "action": "UPDATE",
  "entity": "Product",
  "entityId": "12",
  "changes": {
    "price": { "from": "15000.00", "to": "18000.00" },
    "name": { "from": "Mesa", "to": "Mesa ratona" }
  }
}
```

- `action`: `CREATE`, `UPDATE`, `DELETE`, `READ`, `STATUS_CHANGE`, `IMPORT`, `ACTIVATE` o `DEACTIVATE`.
- `actor.role`: el rol más alto del usuario. `actor.name` lo completa observability.
- `ip`: la de la conexión, que el gateway pasa en `X-Client-Ip` (si el cliente manda ese header, se borra). No se
  toma de `X-Forwarded-For`, que lo puede inventar el cliente; detrás de un proxy habría que tomarla del proxy.
- El `traceId` une la auditoría con la traza y los logs de esa request en Grafana.
- `at` siempre **con zona** (UTC, `Z`), guardado como `timestamptz`.
- En un alta, `changes` trae los campos con valor (`from` vacío). En un cambio, solo los que cambiaron. Los valores
  van como texto, cortados a 500 caracteres.
- Nunca van los campos marcados con `@AuditIgnore` ni los que tienen `password`, `token` o `secret` en el nombre.

## Cómo funciona

```
products / users / sales                                 decor-observability
────────────────────────                                 ───────────────────
cambio + fila en public.outbox_events  (misma transacción)
        │
        ▼
OutboxPublisher (cada 2 s) ──► Redis Streams ──► AuditConsumer ──► <schema>.audit_events
   marca la fila como enviada    decor:audit     (grupo observability-audit)      │
                                                                                   └──► WS store.audit / platform.audit
```

1. **Captura** (`decor-common`, `com.decor.common.audit`): `AuditHibernateListener` escucha `PostInsert`,
   `PostUpdate` y `PostDelete`. El "antes" sale del estado que Hibernate tenía cargado, y los campos que cambiaron,
   de los que Hibernate marcó como sucios. Inserta la fila con la conexión de la misma sesión: si el cambio se
   deshace, la auditoría también. `AuditLog` hace lo mismo a mano con `JdbcTemplate` (dentro de una transacción se
   suma a ella).
2. **Outbox**: `public.outbox_events` se crea al arrancar el servicio. Los enviados se borran a la semana: la
   auditoría vive en observability, acá es solo de paso.
3. **Publicador** (`OutboxPublisher`): toma los pendientes con `FOR UPDATE SKIP LOCKED` (con varias instancias cada
   una toma filas distintas), los manda al stream `decor:audit` y los marca. Si Redis falla, no marca nada y reintenta.
4. **Consumidor** (`AuditConsumer` en observability): grupo de consumidores `observability-audit`, así con varias
   instancias cada evento lo guarda una sola. Lo guarda **una sola vez** aunque llegue repetido (por `id`) y recién
   después lo confirma (`XACK`): si la base falla, queda pendiente y se reintenta. Los pendientes de una instancia que
   se cayó los toma otra al minuto. Después lo manda por el WS.

La importación del CSV apaga la captura automática mientras corre (`AuditSuppression`) y deja la entrada del resumen.

`decor.audit.enabled=false` apaga la auditoría de un servicio; `decor.audit.publisher.enabled=false`, solo el envío.

## Consultas

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/v1/observability/store/audit` | Admin de la tienda | Historial de su tienda, paginado (`page`, `size` hasta 100), con filtros `from`, `to`, `userId`, `entity` y `action` |
| GET | `/api/v1/observability/store/audit/{entity}/{entityId}` | Admin de la tienda | Historial de una cosa puntual (ej. `Product/12`) |
| GET | `/api/v1/platform/observability/audit` | Superadmin | Historial de la plataforma, paginado y con los mismos filtros (y `entityId`) |

- `from` y `to` son fechas con zona (ISO-8601, ej. `2026-11-02T03:00:00Z`); `to` no se incluye. Una fecha mal
  escrita responde 400.
- Respuesta: `{ "items": [ … ], "page": 0, "size": 20, "totalItems": 3, "totalPages": 1 }`, lo más nuevo primero.
- Desde la plataforma, las rutas de la tienda responden 404; un administrador que pide la de la plataforma, 403.

## Límites conocidos

- Las acciones registradas a mano fuera de una transacción (el cambio de estado de un pedido, la desactivación de
  un superadmin) se escriben justo después del cambio, no en la misma transacción: si el servicio se cae entre
  las dos, se pierde esa entrada.
- El stream `decor:audit` se recorta a unos 100.000 eventos: si observability estuviera caída mucho tiempo con
  mucho movimiento, se perderían los más viejos (la outbox ya los marcó como enviados).
- Los cambios hechos con SQL directo que no pasan por `AuditLog` no quedan registrados.

## Para decidir

- Si se puede exportar el historial (CSV).
- Particionado de `audit_events` si crece mucho (hoy tiene índices por fecha, por usuario y por entidad).
