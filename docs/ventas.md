# Ventas (plan)

Plan del módulo de ventas: carrito → pedido → pago por transferencia con comprobante → validación del
administrador → preparación y entrega. Más adelante se van a sumar integraciones de pago (Mercado Pago) y más
automatización; este plan deja el lugar para eso.

> Estado: **plan**. Cuando se implemente cada etapa, este archivo pasa a ser la documentación del módulo.

## Decisiones

| Tema | Decisión |
|---|---|
| Arquitectura | **MS nuevo `decor-sales`** (puerto 3003), base `sales` con un schema por tenant, como los demás |
| Comprador | **Invitado o registrado**. El invitado deja nombre, email y teléfono; si está logueado (`CLIENTE`) el pedido queda asociado a su usuario y aparece en "mis pedidos" |
| Carrito | **En el frontend**. Al pagar se mandan los ítems y cantidades; el backend arma el pedido con los precios actuales |
| Ítems | **Productos y combos**. El combo se vende a su precio y reserva el stock de cada producto que lo compone |
| Stock | **Se reserva al crear el pedido** (`SALIDA`). Si se rechaza definitivamente, se cancela o vence, se devuelve (`ENTRADA`) |
| Comunicación con products | **Síncrona**: llamadas a endpoints internos de products con Resilience4j. Eventos (Redis Streams + outbox) quedan como mejora |
| Comprobante | **Disco**, una carpeta por tenant. JPG, PNG o PDF hasta **5 MB**. No es público: solo lo ve el administrador |
| Gestión de pedidos | **Solo `ADMINISTRADOR`** (validar pagos, cambiar estados, configuración, reportes) |
| Entrega | **Retiro o envío** (con dirección). El costo de envío lo arregla la tienda aparte; logística queda afuera |
| Datos para transferir | **Configuración de ventas de la tienda** en sales: alias, CBU, titular, banco y plazo de vencimiento |
| Vencimiento | Si no se sube el comprobante en **48 h** (configurable por tienda), el pedido pasa a `VENCIDO` y se devuelve el stock |
| Cancelación del comprador | **Sí, hasta que se valida el pago** (`PENDIENTE_PAGO`, `PAGO_EN_REVISION`, `RECHAZADO`). Después solo el administrador |
| Seguimiento del invitado | **Email + número de pedido**. El número es un **código aleatorio** (`K7QM-4X2P`) para que no se pueda recorrer |
| Venta real | Un pedido **cuenta como venta cuando se valida el pago** (`PAGADO` en adelante) |
| Reportes | Totales por período, por producto/combo (ranking), ganancia (precio − costo guardados al vender) y **contador `vendidos`** en el producto |
| Contador `vendidos` | **Solo en el panel**. El catálogo puede ordenar por "más vendidos" sin mostrar el número |
| Avisos por email | **Después** (junto con los eventos). Por ahora el comprador consulta su pedido y la tienda lo ve en el panel |
| Abuso | Rate limit en el gateway (crear pedidos, consultar y subir comprobante) y topes por pedido |
| Ramas | **4 etapas**, una rama por etapa, cada una a `develop` antes de seguir |

## Estados del pedido

```
                ┌──────────── cancela comprador / admin ────────────┐
                │                                                    ▼
PENDIENTE_PAGO ──sube comprobante──► PAGO_EN_REVISION ──valida──► PAGADO ──► EN_PREPARACION ──► LISTO ──► ENTREGADO
   │    ▲                                 │
   │    └──── vuelve a subir ─── RECHAZADO ◄┘ rechaza
   │
   └── pasa el plazo sin comprobante ──► VENCIDO
```

| Estado | Qué significa | Stock | Cuenta como venta |
|---|---|---|---|
| `PENDIENTE_PAGO` | Creado, esperando el comprobante | Reservado | No |
| `PAGO_EN_REVISION` | Subió el comprobante, lo revisa el administrador | Reservado | No |
| `RECHAZADO` | El comprobante no es válido (con motivo). Puede subir otro | Reservado | No |
| `PAGADO` | El administrador validó el pago | Descontado | **Sí** |
| `EN_PREPARACION` | Se está armando | Descontado | Sí |
| `LISTO` | Listo para retirar o despachar | Descontado | Sí |
| `ENTREGADO` | Entregado. Estado final | Descontado | Sí |
| `CANCELADO` | Lo canceló el comprador o el administrador (con motivo). Estado final | Devuelto | No (si estaba pagado, deja de contar) |
| `VENCIDO` | Pasó el plazo sin comprobante. Estado final | Devuelto | No |

- El administrador puede cancelar en cualquier estado no final. Si el pedido ya estaba pagado, la devolución del
  dinero se arregla por fuera.
- `RECHAZADO` también vence: el plazo corre desde el rechazo.
- Cada cambio de estado se guarda en un historial (estado, fecha, usuario, motivo).

## Componentes

### decor-sales (nuevo)

- Proyecto Gradle propio con `includeBuild('../decor-common')`, registrado en Eureka, `TenantFilter` y Hibernate
  multi-tenant como products (el tenant es obligatorio).
- Base `sales` (se agrega a `deploy/init.sql`) y servicio en `docker-compose.dev.yml`.
- Tablas por schema:
  - `orders`: número, estado, datos del comprador, `user_id` (si estaba logueado), tipo de entrega y dirección,
    notas, total, `expires_at`, fechas.
  - `order_items`: tipo (`PRODUCT` / `BUNDLE`), id, nombre, cantidad, **precio unitario y costo unitario al momento
    de la compra**, subtotal.
  - `order_status_history`: estado, fecha, usuario, motivo.
  - `payment_receipts`: archivo, tipo, tamaño, fecha (puede haber varios si hubo rechazos).
  - `sales_settings`: una fila por tienda (alias, CBU, titular, banco, horas de vencimiento).
- Autenticación igual que products: `HeaderAuthFilter` con los roles de users (Redis + caché local).
- Tarea programada para los vencimientos (recorre los tenants).

### products (cambios)

- Endpoints internos (`/api/internal/products/...`, token interno):
  - **Reservar**: recibe la referencia del pedido y los ítems; valida que existan, que tengan precio y que haya
    stock; registra las `SALIDA` y devuelve nombre, precio y costo de cada ítem. Todo o nada.
  - **Devolver**: registra las `ENTRADA` de la reserva de ese pedido.
  - **Marcar vendido / desmarcar**: suma o resta el contador `vendidos`.
  - Las tres son **idempotentes por referencia de pedido**: si sales reintenta, no se descuenta dos veces.
- Campo `soldCount` en productos y combos; orden "más vendidos" en el catálogo.
- Combos en el catálogo público (hoy no están) para poder agregarlos al carrito.

### gateway

- Ruta `/api/v1/sales/**` → `decor-sales`; rutas públicas de checkout y consulta en `PermittedUrls`.
- Rate limit propio para crear pedidos, consultar y subir comprobante.

## Endpoints (propuesta)

### Públicos (desde la URL de la tienda)

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/checkout` | Datos para pagar (alias, CBU, titular, banco), plazo y opciones de entrega |
| POST | `/api/v1/sales/orders` | Crea el pedido y reserva stock. Devuelve número, total, datos para transferir y vencimiento. Si viene con token, lo asocia al usuario |
| POST | `/api/v1/sales/orders/lookup` | `{email, number}` → el pedido (por POST para que el email no quede en la URL ni en los logs) |
| POST | `/api/v1/sales/orders/{number}/receipt` | Sube el comprobante (multipart, con el email) → `PAGO_EN_REVISION` |
| POST | `/api/v1/sales/orders/{number}/cancel` | `{email}` → cancela si todavía no se validó el pago |

### Comprador registrado

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/orders/mine` | Sus pedidos, paginados |

### Administrador

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/admin/orders` | Lista con filtros (estado, fechas, búsqueda por número/email), paginada |
| GET | `/api/v1/sales/admin/orders/{number}` | Detalle con ítems, historial y comprobantes |
| GET | `/api/v1/sales/admin/orders/{number}/receipts/{id}` | Descarga un comprobante |
| PUT | `/api/v1/sales/admin/orders/{number}/status` | `{status, reason}`: validar, rechazar, avanzar o cancelar (según las transiciones) |
| GET / PUT | `/api/v1/sales/admin/settings` | Configuración de ventas de la tienda |
| GET | `/api/v1/sales/admin/reports/summary` | Pedidos, unidades, facturado y ganancia por día/semana/mes en un rango |
| GET | `/api/v1/sales/admin/reports/products` | Unidades, facturado y ganancia por producto/combo (ranking) |

## Etapas

| # | Rama | Qué incluye |
|---|---|---|
| 1 | `feature/ventas-pedidos` | MS `decor-sales` (proyecto, Eureka, gateway, base, docker), configuración de ventas, endpoints internos de reserva/devolución en products, combos en el catálogo, crear pedido, consulta del invitado, "mis pedidos", lista y detalle del administrador, rate limit y topes |
| 2 | `feature/ventas-pagos` | Comprobantes, cambios de estado con historial, cancelación del comprador, vencimiento programado, contador `vendidos` al validar/cancelar |
| 3 | `feature/ventas-reportes` | Reportes por período y por producto, ganancia, orden "más vendidos" en el catálogo |
| 4 | `docs/ventas` | Este archivo como documentación final, `endpoints.md`, `tenants.md`, `permisos.md` y `MVP.md` |

## Para decidir al arrancar cada etapa

**Etapa 1**
- Consistencia si falla la llamada a products (pedido guardado sin reserva, o reserva sin pedido): orden de los
  pasos y compensación.
- Formato del número de pedido y si es único por tienda o global.
- Topes exactos (líneas por pedido, unidades por línea) y límites del rate limit.
- Datos obligatorios del comprador y de la dirección.
- Qué devuelve el catálogo de los combos (disponibilidad, imágenes).
- Si un cambio de precio entre el carrito y el pedido se avisa o se acepta el precio nuevo.

**Etapa 2**
- Qué pasa con el pedido si se borra o deshabilita el producto.
- Cuántos comprobantes se pueden subir y si se borran los de pedidos viejos.
- Cada cuánto corre el vencimiento.

**Etapa 3**
- Zona horaria de los reportes y si se cachean.
- Si los reportes se exportan a CSV.

## Para después

- Integración con **Mercado Pago** (pago automático, sin comprobante): el estado `PAGADO` lo pondría el webhook.
- **Eventos** (Redis Streams + outbox) para stock y vendidos, en vez de llamadas síncronas.
- **Avisos por email** al comprador y a la tienda.
- Costo de envío y logística.
- Delegar la gestión de pedidos a `EMPLEADO` (nueva área `VENTAS` en los permisos).
