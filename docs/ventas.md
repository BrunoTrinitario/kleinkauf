# Ventas (decor-sales)

Microservicio de ventas: el comprador arma el carrito en la tienda, crea el pedido (que reserva el stock en products)
y paga por transferencia. El administrador ve los pedidos en el panel y configura los datos para cobrar.

> Estado: **etapa 1 de 3 implementada** (pedidos). Los comprobantes, los cambios de estado, el vencimiento, las ventas
> del local y los reportes están en [Pendiente](#pendiente).

## Resumen

| | |
|---|---|
| Proyecto | `backend/decor-sales` (Gradle, `includeBuild('../decor-common')`) |
| Servicio | `sales-microservice`, puerto **3003**, registrado en Eureka |
| Base | Postgres `sales`, **un schema por tenant** (se crea y migra solo, igual que products) |
| Tenant | Obligatorio: sin `X-Forwarded-Host` responde 404 |
| Gateway | `/api/v1/sales/**` → `lb://sales-microservice` |
| Depende de | **products** (reserva y devolución de stock) y **users** (roles del usuario, tenants) |

## Qué puede hacer

### Tienda (público, invitado o logueado)

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/checkout` | Datos para pagar (alias, CBU, titular, banco), plazo para pagar, tipos de entrega y topes del pedido. `paymentConfigured: false` si la tienda todavía no cargó los datos de pago |
| POST | `/api/v1/sales/orders` | Crea el pedido y reserva el stock. Devuelve número, estado, total, vencimiento y datos para transferir (201). Si viene con token, lo asocia al usuario |
| POST | `/api/v1/sales/orders/lookup` | `{email, number}` → el pedido. Por POST para que el email no quede en la URL ni en los logs |

### Comprador logueado

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/orders/mine?page&size` | Sus pedidos, más nuevos primero, paginados (20 por defecto, máximo 100) |

### Administrador (solo `ADMINISTRADOR`)

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/admin/orders` | Lista paginada. Filtros: `status`, `channel`, `paymentMethod`, `stockReview`, `from` y `to` (`yyyy-MM-dd`, inclusive), `q` (número exacto o parte del email), `page`, `size` |
| GET | `/api/v1/sales/admin/orders/{number}` | Detalle: comprador, entrega, ítems con precio de lista, precio cobrado y costo, historial con el usuario de cada cambio |
| GET | `/api/v1/sales/admin/settings` | Configuración de ventas de la tienda |
| PUT | `/api/v1/sales/admin/settings` | `{alias, cbu, holder, bank, expirationHours}`. Los campos vacíos se guardan como `null`; `expirationHours` vacío = 48 |

### Ejemplo: crear un pedido

```json
POST /api/v1/sales/orders
{
  "items": [
    { "type": "PRODUCT", "id": 5, "quantity": 2, "price": 15000.00 },
    { "type": "BUNDLE",  "id": 3, "quantity": 1, "price": 42000.00 }
  ],
  "buyer": { "name": "Ana Pérez", "email": "ana@mail.com", "phone": "11 5555-5555" },
  "deliveryType": "ENVIO",
  "address": { "street": "Av. Siempre Viva", "number": "742", "apartment": "2B",
               "city": "CABA", "province": "Buenos Aires", "postalCode": "1405" },
  "notes": "Tocar timbre"
}
```

```json
201
{
  "number": "K7QM-4X2P",
  "status": "PENDIENTE_PAGO",
  "total": 72000.00,
  "expiresAt": "2026-11-04T15:30:00",
  "payment": { "alias": "tienda.decor", "cbu": null, "holder": "Decor SRL", "bank": "Galicia" }
}
```

`price` es el precio que vio el comprador en el carrito. Si cambió, responde **409** y no crea el pedido:

```json
409
{
  "message": "Some prices changed. Please review the order and confirm again",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "expectedPrice": 15000.00, "currentPrice": 16500.00 } ]
}
```

## Reglas

### Pedido online

- **Ítems:** productos (`PRODUCT`) o combos (`BUNDLE`), por id (el catálogo devuelve el `id` de cada producto y
  combo). Entre 1 y **30 líneas** y entre 1 y **20 unidades** por línea (`sales.order.max-lines`,
  `sales.order.max-quantity`). Las líneas repetidas se juntan antes de controlar los topes.
- **Precio:** lo pone el backend con el precio actual. El del carrito solo se usa para avisar si cambió (409).
  Los productos sin precio ("a consultar") y los combos sin precio no se pueden comprar online.
- **Combos:** se venden a su precio y reservan el stock de cada producto que los componen. Tienen que estar
  habilitados y con todos sus productos vigentes.
- **Comprador:** nombre (hasta 100), email válido (hasta 150, se guarda en minúsculas) y teléfono (6 a 30
  caracteres: números, espacios, `+`, `-`, `()`), obligatorios aunque esté logueado.
- **Entrega:** `RETIRO` o `ENVIO`. Con envío son obligatorios calle, número, ciudad, provincia y código postal
  (hasta 100 cada uno); piso/depto es opcional. El costo del envío lo arregla la tienda aparte.
- **Notas:** opcionales, hasta 500 caracteres.
- **Datos de pago:** sin titular y alias o CBU cargados, crear un pedido responde **409**.
- El pedido nace en `PENDIENTE_PAGO`, canal `ONLINE`, medio de pago `TRANSFERENCIA`, sin descuento, y vence a las
  `expirationHours` (48 por defecto) de creado.

### Número de pedido

- Formato `XXXX-XXXX` (ej. `K7QM-4X2P`), aleatorio para que no se puedan recorrer los pedidos de otros. Sin `0/O`
  ni `1/I/L` para que se pueda dictar.
- Único por tienda (unique en el schema). La búsqueda no distingue mayúsculas.

### Consulta del invitado

- Pide el **email y el número**. Si el número no existe o el email no coincide responde el mismo **404**, así no
  se puede saber qué números existen.
- Muestra ítems, total, entrega, historial (estado, fecha y motivo) y los datos para transferir solo mientras falta
  pagar (`PENDIENTE_PAGO` o `RECHAZADO`). No muestra costos ni quién hizo cada cambio.

### Configuración de ventas

| Campo | Regla |
|---|---|
| `alias` | 6 a 20 letras, números, puntos o guiones |
| `cbu` | 22 dígitos |
| `holder`, `bank` | Hasta 100 caracteres |
| `expirationHours` | 1 a 720 (por defecto 48) |

Hay una sola fila por tienda (`sales_settings`). Mientras no se guarde se usan los valores por defecto.

### Permisos

| Quién | Qué puede |
|---|---|
| Invitado | Checkout, crear pedidos, consultar un pedido con email y número |
| Cualquier usuario logueado | Lo mismo (el pedido queda asociado a él) y ver sus pedidos |
| `ADMINISTRADOR` | Todo el panel de ventas y la configuración |
| `EMPLEADO` | Nada del panel, aunque tenga todas las áreas de productos (la gestión de ventas no se delega todavía) |

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
| `CANCELADO` | Lo canceló el comprador o el administrador (con motivo). Estado final | Devuelto | No |
| `VENCIDO` | Pasó el plazo sin comprobante. Estado final | Devuelto | No |

Hoy los pedidos solo se crean en `PENDIENTE_PAGO`: las transiciones llegan en la etapa 2. Cada cambio de estado
queda en el historial (estado, fecha, usuario, motivo); la creación ya se registra.

## Cómo se comunica

```
Tienda (Origin: http://mi-tienda.localhost:3000)
   │
   ▼
gateway :3000
   - rutas públicas: GET /checkout, POST /orders, POST /orders/lookup
   - POST /orders: si viene un JWT válido agrega X-User-Id; si el JWT es inválido → 401
   - rate limit por IP: 5 pedidos/min y 10 consultas/min (el resto, el límite general)
   - agrega X-Forwarded-Host (schema del tenant)
   │
   ▼
decor-sales :3003
   TenantFilter      X-Forwarded-Host → schema
   HeaderAuthFilter  X-User-Id → roles de users (caché 1 min → Redis → users)
   │
   ├── POST /api/internal/products/reservations              ──► products :3002
   └── POST /api/internal/products/reservations/{n}/release  ──► products :3002
         token interno (X-Internal-Token) + X-Forwarded-Host del tenant
         RestTemplate con Eureka, timeouts 2s/3s, Resilience4j "products"
```

### Reserva de stock en products

Endpoints internos de products (`/api/internal/products/reservations`), solo con el token interno de los
microservicios (rol `INTERNAL`); no pasan por el gateway.

| Llamada | Qué hace |
|---|---|
| `POST /reservations` `{reference, items:[{type, id, quantity}]}` | Valida que todo exista, tenga precio y alcance el stock, y registra una `SALIDA` por producto con la referencia del pedido. **Todo o nada.** Devuelve nombre, precio y costo de cada ítem, en el mismo orden |
| `POST /reservations/{reference}/release` | Registra una `ENTRADA` por cada `SALIDA` de esa referencia. Los productos borrados no vuelven al stock |

- **Idempotentes por número de pedido** (tabla `stock_reservations` en products): reintentar una reserva no
  descuenta dos veces (devuelve los datos de nuevo) y devolver dos veces no devuelve dos veces.
- Si la devolución llega antes que la reserva (timeout en sales), queda marcada como devuelta y la reserva que
  llegue después se rechaza con 409.
- Errores: datos inválidos → 400; ítem inexistente, combo deshabilitado, sin precio o sin stock → **409** con el
  nombre del ítem (ej. `Not enough stock: Mesa, Silla`). sales devuelve ese mensaje al comprador.
- Los combos descuentan sus productos (cantidad del combo × unidades). El costo de un combo es la suma del costo de
  sus productos, o `null` si alguno no tiene costo.
- Cada reserva y devolución invalida la caché del catálogo (cambia la disponibilidad).

### Crear un pedido, paso a paso

1. Valida el pedido y que la tienda tenga datos de pago.
2. Genera el número (reintenta si ya existe).
3. **Reserva** el stock en products con ese número.
   - 4xx (sin stock, sin precio, no disponible): responde ese error. products no descontó nada.
   - Timeout, 5xx o circuito abierto: la reserva pudo haberse hecho, así que pide **devolver** y responde 503.
4. Compara los precios con los del carrito. Si cambió alguno: **devuelve** el stock y responde 409 con los precios
   nuevos.
5. Guarda el pedido con nombre, precio de lista, precio cobrado y costo de cada ítem copiados al momento de la venta.
   Si falla: **devuelve** el stock y responde 503.

Si la devolución también falla, el stock queda reservado: se loguea un error con el número del pedido para
corregirlo a mano (los movimientos tienen la referencia). Los eventos con outbox quedan para después.

### Resilience4j

Instancias `products` y `users` en `application.properties`: circuit breaker (50% de fallas en las últimas 10
llamadas → corta 10 s) y retry (3 intentos cada 200 ms). Los 4xx no cuentan como falla ni se reintentan. Reintentar
la reserva es seguro porque es idempotente.

## Datos

Tablas por schema en la base `sales`:

| Tabla | Qué guarda |
|---|---|
| `orders` | Número, estado, canal (`ONLINE` / `LOCAL`), medio de pago, comprador, `user_id`, entrega y dirección, notas, descuento, total, `stock_review`, `expires_at`, fechas |
| `order_items` | Tipo (`PRODUCT` / `BUNDLE`), `item_id`, nombre, cantidad, precio de lista, precio cobrado, costo unitario y subtotal al momento de la venta |
| `order_status_history` | Estado, fecha, usuario (null si fue un invitado o el sistema) y motivo |
| `sales_settings` | Alias, CBU, titular, banco y horas de vencimiento (una fila) |

En products: `stock_reservations` (referencia y estado de cada reserva) y la columna `reference` en
`stock_movements`.

## Cambios en otros servicios

- **products:** endpoints internos de reserva (con rol `INTERNAL` para `/api/internal/**`), combos en el catálogo
  público (`GET /api/v1/catalog/bundles`, con disponibilidad e imagen del primer producto) e `id` en los productos
  del catálogo. Los cambios en combos invalidan la caché del catálogo.
- **gateway:** ruta a sales, rutas públicas del checkout, login opcional en `POST /orders` y límites
  `limit.order` / `limit.lookup`.
- **deploy:** base `sales` en `init.sql` y `sales-service` en `docker-compose.dev.yml`.

## Pendiente

### Etapa 2: pagos y ventas del local

- Subir el comprobante (JPG, PNG o PDF hasta 5 MB, en disco por tienda, solo lo ve el administrador) → 
  `PAGO_EN_REVISION`, con rate limit propio.
- Cambios de estado del administrador (`PUT /admin/orders/{number}/status`): validar, rechazar con motivo, avanzar
  y cancelar.
- Cancelación del comprador hasta que se valida el pago (`POST /orders/{number}/cancel`).
- Vencimiento programado: pasa a `VENCIDO` y devuelve el stock (también desde `RECHAZADO`).
- Contador `vendidos` en products al validar y al cancelar un pedido pagado.
- **Ventas del local** (`POST /admin/orders`): pedidos ya pagos (`PAGADO` o `ENTREGADO`), canal `LOCAL`, medio de
  pago, comprador opcional, precio cambiado por ítem o descuento, productos "a consultar" y `AJUSTE` automático si
  falta stock (marca `stock_review`). Anulación con motivo.

Para decidir: qué pasa si se borra el producto de un pedido, cuántos comprobantes se pueden subir y si se borran
los viejos, cada cuánto corre el vencimiento, comprobante imprimible de la venta del local, descuento en monto o
porcentaje y tope, y si al anular una venta con `AJUSTE` vuelve todo el stock o solo lo que había.

### Etapa 3: reportes

- Totales por día/semana/mes, por producto/combo (ranking) y ganancia, desglosados por canal y medio de pago.
- Orden "más vendidos" en el catálogo (sin mostrar el número).

Para decidir: zona horaria de los reportes, si se cachean y si se exportan a CSV.

### Para después

- **Mercado Pago** (pago automático: el webhook pondría `PAGADO`).
- **Eventos** (Redis Streams + outbox) para stock y vendidos, en vez de llamadas síncronas.
- **Avisos por email** al comprador y a la tienda.
- Costo de envío y logística.
- Delegar la gestión de pedidos a `EMPLEADO` (área `VENTAS` en los permisos).
- Actualizar `endpoints.md`, `tenants.md`, `permisos.md` y `MVP.md` con sales.
