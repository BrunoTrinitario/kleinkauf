# Auditoría (plan)

Plan para registrar quién hizo qué, cuándo y qué cambió, en cada tienda y en la plataforma, y verlo en tiempo real.
Se apoya en el servicio de [observabilidad](observabilidad.md).

> Estado: **plan**. Cuando se implemente, este archivo pasa a ser la documentación.

## Decisiones

| Tema | Decisión |
|---|---|
| Qué se audita | **Cambios de datos**, **acciones de plataforma** y **lecturas sensibles**. Los logins no |
| Detalle | Quién, cuándo, IP, acción, entidad e id, y **solo los campos que cambiaron, con valor anterior y nuevo**. Nunca contraseñas ni tokens |
| Dónde se guarda | En **`decor-observability`**, base `observability`: un schema por tienda y `public` para la plataforma |
| Cómo llega | **Outbox**: cada servicio guarda el evento en la misma transacción que el cambio y después lo publica en Redis Streams. **No se pierde** aunque observability o Redis estén caídos |
| Retención | **Para siempre**. Nadie la edita ni la borra |
| Quién la ve | **Cada uno lo suyo**: el administrador ve la de su tienda y el superadmin la de la plataforma. El superadmin **no** ve la de las tiendas |
| En vivo | Canales `store.audit` y `platform.audit` del WebSocket |

## Qué se registra

### Cambios de datos (tienda)

| Servicio | Acciones |
|---|---|
| products | Crear, editar y borrar productos; precio y costo; movimientos de stock; combos y su precio; imágenes; metadatos; destacados; importación CSV (una entrada con el resumen: creados, actualizados) |
| users | Crear, editar y borrar usuarios; asignar y quitar roles; cambiar los permisos de un rol |
| sales | Configuración de ventas; cambios de estado de pedidos (validar, rechazar, cancelar…) |

### Acciones de plataforma

Alta, deshabilitar, habilitar y borrar tenants (superadmin o autoservicio).

### Lecturas sensibles

Ver costo y margen (`/products/{id}/pricing`), exportar el CSV y descargar comprobantes de pago.

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

- `action`: `CREATE`, `UPDATE`, `DELETE`, `READ` (lecturas sensibles) o una acción propia (`STATUS_CHANGE`,
  `IMPORT`, `PERMISSIONS_CHANGE`…).
- El `traceId` une la auditoría con la traza y los logs de esa request en Grafana.
- Los campos sensibles se excluyen con una anotación (`@AuditIgnore`) o una lista en la configuración.

## Cómo funciona

```
products / users / sales                         decor-observability
────────────────────────                         ───────────────────
cambio + fila en outbox  (misma transacción)
        │
        ▼
publicador (cada pocos segundos) ──► Redis Streams ──► consumidor ──► audit_events (schema de la tienda / public)
        marca la fila como enviada        decor:audit                         │
                                                                              └──► WS store.audit / platform.audit
```

1. **Captura**: un componente de `decor-common` calcula las diferencias (antes / después) al guardar. Para cambios
   que no son una entidad (importación, cambio de estado, lectura sensible) se registra a mano con un servicio
   `AuditLog`.
2. **Outbox**: la tabla `outbox_events` se escribe en la misma transacción que el cambio.
3. **Publicador**: envía los pendientes a Redis Streams y los marca como enviados; si falla, reintenta.
4. **Consumidor**: observability lo guarda **una sola vez** (por `id`, aunque llegue repetido) y lo reenvía por WS.

## Consultas

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/v1/observability/store/audit` | Admin de la tienda | Historial de su tienda, paginado, con filtros por fecha, usuario, entidad y acción |
| GET | `/api/v1/observability/store/audit/{entity}/{id}` | Admin de la tienda | Historial de una cosa puntual (ej. todos los cambios del producto 12) |
| GET | `/api/v1/observability/platform/audit` | Superadmin | Historial de la plataforma, paginado y con filtros |

## Para decidir al arrancar

- Dónde vive la tabla outbox en products (hoy hay un schema por tienda): una por schema o una sola en `public` con
  columna `tenant`.
- Cómo se calcula el "antes" (listener de Hibernate o copia al cargar la entidad).
- Si la auditoría de una tienda borrada se conserva y cómo se consulta.
- Si se puede exportar el historial (CSV).
- Si la vista del administrador incluye a los usuarios borrados con su nombre.
- Índices y particionado de `audit_events`, ya que se guarda para siempre.
