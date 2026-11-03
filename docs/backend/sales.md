# sales (kleinkauf-sales)

Spring Boot (servlet) en el puerto **3003**, registrado en Eureka como `sales-microservice`. Base Postgres `sales`,
un schema por tienda (se crea con la primera request de la tienda). Los comprobantes de pago van a disco.

Convenciones comunes (headers, errores, paginación, fechas): [README](README.md). Historia del diseño y detalle de
la comunicación con products: [ventas.md](../ventas.md).

## Qué hace

- **Tienda online:** checkout, pedidos de invitados o usuarios logueados con reserva de stock en products, pago por
  transferencia con comprobante, consulta y cancelación del comprador, "mis pedidos".
- **Panel de ventas (solo `ADMINISTRADOR`):** lista y detalle de pedidos, validar o rechazar el pago, avanzar
  estados, cancelar, descargar comprobantes, configuración de la tienda (datos para transferir y plazo).
- **Venta del local:** el administrador carga una venta ya cobrada, con precio cambiado, descuento y medio de pago,
  aunque falte stock.
- **Reportes:** totales por período y por canal y medio de pago, y ranking de productos.
- **Vencimiento automático** de los pedidos sin pagar.

### Conceptos

| Concepto | Qué es |
|---|---|
| Pedido | Se identifica por su **número** (`K7QM-4X2P`): aleatorio, sin `0/O` ni `1/I/L`, único por tienda, sin distinguir mayúsculas |
| Canal | `ONLINE` (la tienda) o `LOCAL` (venta del local, la carga el administrador) |
| Medio de pago | `EFECTIVO`, `TRANSFERENCIA`, `DEBITO`, `CREDITO`, `OTRO`. Los online son siempre `TRANSFERENCIA` |
| Entrega | `RETIRO` o `ENVIO` (con dirección). El costo del envío lo arregla la tienda aparte |
| Ítem | `PRODUCT` o `BUNDLE` (combo), con el `id` del catálogo. El precio lo pone el backend; el del carrito solo sirve para avisar si cambió |
| Venta | Un pedido pago (`PAGADO`, `EN_PREPARACION`, `LISTO`, `ENTREGADO`). Su fecha es la del pago |
| `stockReview` | Venta del local hecha sin stock suficiente: products ajustó el stock y hay que revisarlo |

### Estados

```
                ┌──────────── cancela comprador / admin ────────────┐
                │                                                    ▼
PENDIENTE_PAGO ──sube comprobante──► PAGO_EN_REVISION ──valida──► PAGADO ──► EN_PREPARACION ──► LISTO ──► ENTREGADO
   │    ▲                                 │
   │    └──── vuelve a subir ─── RECHAZADO ◄┘ rechaza
   │
   └── pasa el plazo sin comprobante ──► VENCIDO

Venta del local: nace en PAGADO o ENTREGADO
```

| Estado | Qué significa | Stock | Venta | Comprobante | Cancela el comprador |
|---|---|---|---|---|---|
| `PENDIENTE_PAGO` | Creado, esperando el comprobante | Reservado | No | Sí | Sí |
| `PAGO_EN_REVISION` | Subió el comprobante | Reservado | No | No | Sí |
| `RECHAZADO` | Comprobante inválido (con motivo) | Reservado | No | Sí | Sí |
| `PAGADO` | El administrador validó el pago | Descontado | **Sí** | No | No |
| `EN_PREPARACION` | Se está armando | Descontado | Sí | No | No |
| `LISTO` | Para retirar o despachar | Descontado | Sí | No | No |
| `ENTREGADO` | Final | Descontado | Sí | No | No |
| `CANCELADO` | Final (con motivo) | Devuelto | No | No | — |
| `VENCIDO` | Final: pasó el plazo sin comprobante | Devuelto | No | No | — |

Cambios del administrador (`PUT /admin/orders/{number}/status`):

| Cambio | Desde | Efecto |
|---|---|---|
| Validar → `PAGADO` | `PENDIENTE_PAGO`, `PAGO_EN_REVISION`, `RECHAZADO` | Suma a vendidos en products. Se puede validar sin comprobante (pagó por otro medio) |
| Rechazar → `RECHAZADO` | `PAGO_EN_REVISION` | **Motivo obligatorio.** El plazo para subir otro comprobante vuelve a correr |
| Avanzar → `EN_PREPARACION`, `LISTO`, `ENTREGADO` | Un estado pago anterior | Se pueden saltear pasos; nunca hacia atrás |
| Cancelar → `CANCELADO` | Cualquier estado no final | **Motivo obligatorio.** Devuelve el stock; si estaba pago, lo resta de vendidos. El reintegro del dinero es aparte |

Cualquier otro cambio responde 409. Cada cambio queda en el historial con fecha, usuario y motivo.

## Flujo de compra (frontend de la tienda)

1. **Checkout:** `GET /api/v1/sales/checkout`. Si `paymentConfigured` es `false`, la tienda no cargó sus datos de
   pago y no se puede comprar: mostrarlo y no dejar avanzar. También trae los topes del carrito y el plazo para
   pagar.
2. **Crear el pedido:** `POST /api/v1/sales/orders` con los ítems del carrito (y el precio que vio el comprador),
   los datos del comprador y la entrega. Con sesión iniciada mandar el token: el pedido queda en "mis pedidos".
   - **201:** mostrar el número, el total, el vencimiento y los datos para transferir. Guardar número y email
     (los pide todo lo que sigue).
   - **409 con JSON `items`:** cambió algún precio. Mostrar `expectedPrice` → `currentPrice`, actualizar el carrito
     y pedir que confirme de nuevo.
   - **409 con texto:** sin stock (`Not enough stock: Mesa, Silla`), ítem no disponible o sin precio, o la tienda
     sin datos de pago.
   - **503:** products no respondió; reintentar más tarde.
3. **Pagar:** el comprador transfiere por fuera y sube el comprobante con
   `POST /api/v1/sales/orders/{number}/receipt` (número + email). El pedido pasa a `PAGO_EN_REVISION`.
4. **Seguir el pedido:** `POST /api/v1/sales/orders/lookup` con email y número. Si lo rechazaron, `history` trae el
   motivo y `payment` vuelve a aparecer para subir otro comprobante.
5. **Cancelar:** `POST /api/v1/sales/orders/{number}/cancel` con el email, mientras no se validó el pago.
6. **Mis pedidos:** `GET /api/v1/sales/orders/mine` con el token.

Un pedido sin comprobante vence a las `expirationHours` (48 por defecto) y devuelve el stock.

## Flujo del administrador

1. **Configurar** una vez los datos para transferir: `PUT /api/v1/sales/admin/settings`. Sin titular y alias o CBU,
   la tienda no acepta pedidos.
2. **Revisar pagos:** `GET /api/v1/sales/admin/orders?status=PAGO_EN_REVISION`, abrir el detalle, descargar el
   comprobante y validar (`PAGADO`) o rechazar con motivo.
3. **Preparar y entregar:** avanzar a `EN_PREPARACION`, `LISTO`, `ENTREGADO`.
4. **Vender en el local:** `POST /api/v1/sales/admin/orders`. Si faltaba stock, la venta sale con `stockReview` y
   conviene revisar el stock del producto (`GET /api/v1/sales/admin/orders?stockReview=true`).
5. **Anular** cualquier pedido no final: cancelar con motivo.
6. **Ver reportes:** `GET /api/v1/sales/admin/reports/summary` y `/products`.

## Tienda (checkout)

### GET /api/v1/sales/checkout

**Acceso:** Público

```bash
curl http://localhost:3000/api/v1/sales/checkout -H 'Origin: http://demo.localhost:2999'
```

Respuesta **200**:

```json
{
  "paymentConfigured": true,
  "payment": { "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia" },
  "expirationHours": 48,
  "deliveryTypes": [ "RETIRO", "ENVIO" ],
  "maxLines": 30,
  "maxQuantity": 20
}
```

`payment` es `null` si `paymentConfigured` es `false`.

### POST /api/v1/sales/orders

**Acceso:** Público, login opcional · **Rate limit:** 5 por minuto

Crea el pedido y reserva el stock en products. Con un token válido queda asociado al usuario; con uno inválido, 401.

| Campo | Regla |
|---|---|
| `items` | 1 a 30 líneas, 1 a 20 unidades cada una. `type` (`PRODUCT`/`BUNDLE`), `id`, `quantity` y `price` (el del carrito) obligatorios. Las líneas repetidas se juntan antes de contar |
| `buyer` | `name` (hasta 100), `email` válido (hasta 150, se guarda en minúsculas) y `phone` (6 a 30: números, espacios, `+`, `-`, `()`), obligatorios aunque esté logueado |
| `deliveryType` | `RETIRO` o `ENVIO` |
| `address` | Solo con `ENVIO`: `street`, `number`, `city`, `province`, `postalCode` obligatorios (hasta 100), `apartment` opcional |
| `notes` | Opcional, hasta 500 |

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{
    "items": [ { "type": "PRODUCT", "id": 5, "quantity": 2, "price": 15000.00 },
               { "type": "BUNDLE", "id": 3, "quantity": 1, "price": 42000.00 } ],
    "buyer": { "name": "Ana Pérez", "email": "ana@mail.com", "phone": "11 5555-5555" },
    "deliveryType": "ENVIO",
    "address": { "street": "Av. Siempre Viva", "number": "742", "apartment": "2B",
                 "city": "CABA", "province": "Buenos Aires", "postalCode": "1405" },
    "notes": "Tocar timbre"
  }'
```

Respuesta **201**:

```json
{
  "number": "K7QM-4X2P",
  "status": "PENDIENTE_PAGO",
  "total": 72000.00,
  "expiresAt": "2026-11-05T13:30:00",
  "payment": { "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia" }
}
```

Respuesta **409** si cambió algún precio (JSON, el pedido no se crea y el stock se devuelve):

```json
{
  "message": "Some prices changed. Please review the order and confirm again",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "expectedPrice": 15000.00, "currentPrice": 16500.00 } ]
}
```

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Ítems inválidos | `The order needs between 1 and 30 items, each with type, id, price and a quantity between 1 and 20` |
| 400 | Comprador inválido | `Name (up to 100 characters), a valid email and a phone are required` |
| 400 | Sin entrega | `The delivery type is required (RETIRO or ENVIO)` |
| 400 | Dirección incompleta con `ENVIO` | `Shipping needs street, number, city, province and postal code (up to 100 characters each)` |
| 400 | Notas largas | `Notes must be 500 characters or fewer` |
| 409 | La tienda no cargó sus datos de pago | `The store has not configured its payment details yet` |
| 409 | Precios cambiados | JSON de arriba |
| 409 | Sin stock, ítem no disponible o sin precio (lo responde products) | `Not enough stock: Mesa, Silla` · `Item not available` · `Item without price: Mesa` |
| 503 | products no responde o no se pudo guardar | `The order could not be created right now. Please try again in a few minutes` |

### POST /api/v1/sales/orders/lookup

**Acceso:** Público (email) · **Rate limit:** 10 por minuto (compartido con cancelar)

El pedido visto por el comprador. Por POST para que el email no quede en la URL.

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders/lookup \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com","number":"k7qm-4x2p"}'
```

Respuesta **200** (`OrderDto`):

```json
{
  "number": "K7QM-4X2P",
  "status": "RECHAZADO",
  "createdAt": "2026-11-03T13:30:00",
  "expiresAt": "2026-11-05T15:00:00",
  "buyer": { "name": "Ana Pérez", "email": "ana@mail.com", "phone": "11 5555-5555" },
  "deliveryType": "ENVIO",
  "address": { "street": "Av. Siempre Viva", "number": "742", "apartment": "2B",
               "city": "CABA", "province": "Buenos Aires", "postalCode": "1405" },
  "notes": "Tocar timbre",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "quantity": 2, "unitPrice": 15000.00, "subtotal": 30000.00 } ],
  "discount": 0,
  "total": 72000.00,
  "payment": { "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia" },
  "history": [
    { "status": "PENDIENTE_PAGO", "createdAt": "2026-11-03T13:30:00", "reason": null, "userId": null },
    { "status": "PAGO_EN_REVISION", "createdAt": "2026-11-03T14:00:00", "reason": null, "userId": null },
    { "status": "RECHAZADO", "createdAt": "2026-11-03T15:00:00", "reason": "El monto no coincide", "userId": null }
  ],
  "receipts": 1
}
```

- `payment` solo mientras se puede subir el comprobante (`PENDIENTE_PAGO` o `RECHAZADO`); si no, `null`.
- Sin costos ni quién hizo cada cambio (`userId` siempre `null`). `receipts` es la cantidad de comprobantes.

Error: 404 `Order not found` (número inexistente o email que no coincide: no se distingue).

### POST /api/v1/sales/orders/{number}/receipt

**Acceso:** Público (email) · **Rate limit:** 5 por minuto · `multipart/form-data`

Sube el comprobante (`file`) con el email del pedido (`email`). Pasa a `PAGO_EN_REVISION`.

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders/K7QM-4X2P/receipt \
  -H 'Origin: http://demo.localhost:2999' \
  -F 'email=ana@mail.com' -F 'file=@transferencia.pdf'
```

Respuesta **200**: `OrderDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | No es JPG, PNG o PDF (se mira el contenido, no el nombre) o pesa más de 5 MB | `The receipt must be a JPG, PNG or PDF file of 5 MB or less` |
| 404 | Número o email | `Order not found` |
| 409 | El pedido no está en `PENDIENTE_PAGO` o `RECHAZADO`, o ya venció | `Receipts can only be uploaded while the order is waiting for payment and before it expires` |
| 409 | Ya tiene 5 (cuentan los rechazados) | `The order already has the maximum of 5 receipts` |
| 409 | Otro lo cambió al mismo tiempo | `The order was changed by someone else. Reload it and try again` |
| 413 | El request supera 6 MB | `The file exceeds the maximum upload size` |

Publica `order.receipt-uploaded` para el panel en vivo.

### POST /api/v1/sales/orders/{number}/cancel

**Acceso:** Público (email) · **Rate limit:** 10 por minuto (compartido con la consulta)

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders/K7QM-4X2P/cancel \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com"}'
```

Respuesta **200**: `OrderDto` en `CANCELADO`, con el motivo "Cancelado por el comprador". El stock vuelve.

| Código | Cuándo | Mensaje |
|---|---|---|
| 404 | Número o email | `Order not found` |
| 409 | Ya se validó el pago, o está en un estado final (también `VENCIDO` o `CANCELADO`) | `The order can no longer be cancelled: the payment was already validated` |
| 503 | products no responde | `The stock could not be updated right now. Please try again in a few minutes` |

### GET /api/v1/sales/orders/mine

**Acceso:** Login

Los pedidos del usuario, más nuevos primero. `page` (0) y `size` (20, máximo 100).

```json
{
  "items": [
    { "number": "K7QM-4X2P", "status": "PAGADO", "channel": "ONLINE", "paymentMethod": "TRANSFERENCIA",
      "buyerName": "Ana Pérez", "buyerEmail": "ana@mail.com", "units": 3, "total": 72000.00,
      "stockReview": false, "createdAt": "2026-11-03T13:30:00" }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

Para el detalle, `lookup` con el email del pedido. Error: 400 `The page must be 0 or greater and the size between 1 and 100`.

## Panel de ventas

Todo `/api/v1/sales/admin/**` es **solo `ADMINISTRADOR`** (no se delega a empleados).

### GET /api/v1/sales/admin/orders

Lista paginada, más nuevos primero (`OrderSummaryDto`, como en "mis pedidos").

| Parámetro | Qué hace |
|---|---|
| `status` | Un estado |
| `channel` | `ONLINE` o `LOCAL` |
| `paymentMethod` | Un medio de pago |
| `stockReview` | `true`: solo las ventas del local hechas sin stock |
| `from`, `to` | `yyyy-MM-dd`, días de la tienda (Argentina), inclusive, por fecha de creación |
| `q` | Número exacto o parte del email |
| `page`, `size` | 0 y 20 (máximo 100) |

```bash
curl 'http://localhost:3000/api/v1/sales/admin/orders?status=PAGO_EN_REVISION' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Errores: 400 `Invalid filter: <campo>` (valor inválido), 400 por la página.

### GET /api/v1/sales/admin/orders/{number}

Detalle completo (`AdminOrderDto`):

```json
{
  "number": "K7QM-4X2P", "status": "PAGO_EN_REVISION", "channel": "ONLINE", "paymentMethod": "TRANSFERENCIA",
  "buyer": { "name": "Ana Pérez", "email": "ana@mail.com", "phone": "11 5555-5555" },
  "userId": 12, "deliveryType": "RETIRO", "address": null, "notes": null,
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "quantity": 2, "listPrice": 15000.00,
               "unitPrice": 15000.00, "unitCost": 9000.00, "subtotal": 30000.00 } ],
  "discount": 0, "total": 30000.00, "stockReview": false,
  "expiresAt": "2026-11-05T13:30:00", "createdAt": "2026-11-03T13:30:00", "updatedAt": "2026-11-03T14:00:00",
  "history": [ { "status": "PENDIENTE_PAGO", "createdAt": "2026-11-03T13:30:00", "reason": null, "userId": 12 } ],
  "receipts": [ { "id": 4, "contentType": "application/pdf", "size": 182334, "createdAt": "2026-11-03T14:00:00" } ]
}
```

`listPrice` es el precio de lista al vender (`null` si era "a consultar"); `unitPrice` el cobrado; `unitCost` el
costo al vender (`null` si no tenía). Error: 404 `Order not found`.

### PUT /api/v1/sales/admin/orders/{number}/status

Cambia el estado según la [tabla de cambios](#estados).

```bash
curl -X PUT http://localhost:3000/api/v1/sales/admin/orders/K7QM-4X2P/status \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"status":"RECHAZADO","reason":"El monto no coincide"}'
```

Respuesta **200**: `AdminOrderDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Rechazar o cancelar sin motivo (o motivo de más de 500) | `A reason (up to 500 characters) is required to reject or cancel` |
| 404 | No existe | `Order not found` |
| 409 | Cambio no permitido | `The order cannot change from <actual> to <nuevo>` |
| 409 | Otro lo cambió al mismo tiempo | `The order was changed by someone else. Reload it and try again` |
| 503 | products no responde (el pedido no cambia; se puede reintentar) | `The stock could not be updated right now. Please try again in a few minutes` |

Queda en la auditoría como `STATUS_CHANGE` con el motivo.

### GET /api/v1/sales/admin/orders/{number}/receipts/{id}

Descarga un comprobante (`Content-Disposition: attachment; filename="comprobante-<número>-<id>.<ext>"`, con su
content-type). Se registra en la auditoría como lectura sensible. No hay URL pública.

Error: 404 `Receipt not found`.

### POST /api/v1/sales/admin/orders

Venta del local, ya cobrada.

| Campo | Regla |
|---|---|
| `items` | 1 a 100 líneas, 1 a 999 unidades. `price` opcional: el precio cobrado (≥ 0); sin `price`, el actual. Obligatorio para productos "a consultar" |
| `discount` | Monto en pesos sobre el subtotal, ≥ 0 y no mayor que el subtotal (`null` = 0) |
| `paymentMethod` | Obligatorio |
| `status` | `PAGADO` (para preparar o enviar después) o `ENTREGADO` |
| `buyer` | Opcional (consumidor final). Si viene algún dato, se valida |
| `deliveryType`, `address` | Opcional, `RETIRO` por defecto. `ENVIO` pide la dirección |
| `notes` | Opcional, hasta 500 |

```bash
curl -X POST http://localhost:3000/api/v1/sales/admin/orders \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"type":"PRODUCT","id":5,"quantity":1,"price":14000.00},{"type":"PRODUCT","id":8,"quantity":2}],
       "discount":1000.00,"paymentMethod":"EFECTIVO","status":"ENTREGADO","buyer":{"name":"Juan"}}'
```

Respuesta **201**: `AdminOrderDto` con canal `LOCAL`, sin vencimiento.

- **Sin stock suficiente se vende igual:** products ajusta el stock a 0, el pedido sale con `stockReview: true` y el
  historial dice qué productos faltaron.
- Suma a vendidos al crearse. Publica `order.created` con `channel: LOCAL`.
- Para anularla, cancelarla con motivo: vuelve **todo** lo vendido al stock y se resta de vendidos.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Ítems inválidos | `The sale needs between 1 and 100 items, each with type, id, a quantity between 1 and 999 and, if given, a price of zero or greater` |
| 400 | Productos "a consultar" sin precio (devuelve el stock) | `These items have no price, enter one: Mesa, Silla` |
| 400 | Descuento inválido | `The discount must be zero or greater and not exceed the subtotal` |
| 400 | Sin medio de pago | `The payment method is required (EFECTIVO, TRANSFERENCIA, DEBITO, CREDITO or OTRO)` |
| 400 | Estado inicial inválido | `A store sale starts as PAGADO or ENTREGADO` |
| 400 | Comprador inválido | `Buyer data is optional, but if given: name up to 100 characters, a valid email and a valid phone` |
| 400 | Entrega o dirección | Los de [crear un pedido](#post-apiv1salesorders) |
| 409 | Producto o combo inexistente o combo deshabilitado (products) | `Item not available` |
| 503 | products no responde | `The order could not be created right now. Please try again in a few minutes` |

### GET /api/v1/sales/admin/settings

```json
{ "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia", "expirationHours": 48 }
```

Sin configurar, todo `null` salvo `expirationHours` (48).

### PUT /api/v1/sales/admin/settings

Reemplaza la configuración (los campos vacíos se guardan como `null`; `expirationHours` vacío = 48).

| Campo | Regla |
|---|---|
| `alias` | 6 a 20 letras, números, puntos o guiones |
| `cbu` | 22 dígitos |
| `holder`, `bank` | Hasta 100 |
| `expirationHours` | 1 a 720 |

Para aceptar pedidos online hace falta `holder` y `alias` o `cbu`.

Respuesta **200**: `SalesSettingsDto`. Error: 400 `Alias must have 6 to 20 letters, numbers, dots or hyphens, CBU 22
digits, holder and bank up to 100 characters and the expiration between 1 and 720 hours`.

## Reportes

**Acceso:** ADMINISTRADOR. Una venta es un pedido pago y su fecha es la del pago (cuando se validó, o cuando se
cargó la venta del local); un pedido cancelado después de pagar deja de contar. Los montos usan los precios y
costos guardados al vender. Se calculan al pedirlos (sin caché).

| Parámetro | Regla |
|---|---|
| `from`, `to` | `yyyy-MM-dd`, días de la tienda (Argentina), inclusive. Default: los últimos 30 días. Máximo 366 días |
| `channel` | `ONLINE` o `LOCAL`; vacío = los dos |

Cada total (`ReportTotalsDto`):

| Campo | Qué es |
|---|---|
| `orders`, `units` | Pedidos y unidades vendidas |
| `revenue` | Lo cobrado (con el descuento ya restado) |
| `discounts` | Descuentos de las ventas del local |
| `cost` | Costo de las unidades vendidas |
| `profit` | `revenue - cost` |
| `unitsWithoutCost` | Unidades sin costo cargado. **Si es mayor a 0, la ganancia es más alta de lo real: avisarlo en el panel** |

### GET /api/v1/sales/admin/reports/summary

Parámetros extra: `groupBy` = `day` (default), `week` (desde el lunes) o `month`, y `paymentMethod`.

```bash
curl 'http://localhost:3000/api/v1/sales/admin/reports/summary?from=2026-10-01&to=2026-10-31&groupBy=week' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

```json
{
  "from": "2026-10-01", "to": "2026-10-31", "groupBy": "WEEK", "zone": "America/Argentina/Buenos_Aires",
  "totals": { "orders": 18, "units": 41, "revenue": 1530000.00, "discounts": 5000.00,
              "cost": 910000.00, "profit": 620000.00, "unitsWithoutCost": 2 },
  "periods": [ { "start": "2026-09-29", "totals": { "orders": 0, "units": 0, "revenue": 0, "discounts": 0,
                 "cost": 0, "profit": 0, "unitsWithoutCost": 0 } } ],
  "byChannel": [ { "key": "ONLINE", "totals": { "orders": 12, "...": "..." } } ],
  "byPaymentMethod": [ { "key": "TRANSFERENCIA", "totals": { "orders": 14, "...": "..." } } ]
}
```

- `periods` trae todos los períodos del rango, también en cero (para graficar sin huecos).
- `byChannel` y `byPaymentMethod` solo los que tuvieron ventas.

### GET /api/v1/sales/admin/reports/products

Ranking de productos y combos. Parámetros extra: `sort` = `units` (default), `revenue` o `profit`, y `limit` (1 a
100, default 20).

```json
{
  "from": "2026-10-01", "to": "2026-10-31", "sort": "UNITS",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "units": 12, "revenue": 180000.00,
               "cost": 108000.00, "profit": 72000.00, "unitsWithoutCost": 0 } ]
}
```

Un combo y un producto con el mismo id son renglones distintos. `revenue` es **antes** del descuento del pedido. El
nombre es el de la venta más reciente.

Errores de los dos: 400 `Invalid filter: <campo>` y 400 `The date range must have from before to and be 366 days
or shorter`.

## Cómo se comunica

```
gateway ──► sales :3003
              TenantFilter      X-Forwarded-Host → schema (obligatorio: sin él, 404)
              HeaderAuthFilter  X-User-Id → roles (caché 1 min → Redis → users)
              │
              ├── products :3002   reservas, devoluciones y vendidos (endpoints internos)
              │     token interno + X-Forwarded-Host de la tienda, Eureka, timeouts 2 s / 3 s,
              │     Resilience4j "products" (circuit breaker y reintentos)
              └── users :3001      roles del usuario (GET /api/internal/users/roles/{id})

ExpirationJob (cada 1 h, empieza al minuto) ── por cada tienda: vence pedidos y devuelve su stock
```

- **Crear un pedido:** reserva en products (todo o nada) → compara precios (si cambiaron, devuelve y 409) → guarda.
  Si falla al guardar, devuelve el stock. Si products no responde, pide devolver por las dudas y responde 503.
- **Cambios de estado:** primero products (vendidos, devolución), después el pedido. Las llamadas son idempotentes
  por número de pedido: reintentar no aplica nada dos veces. Detalle en
  [ventas.md](../ventas.md#crear-un-pedido-paso-a-paso).
- **Control optimista:** el pedido tiene `@Version`; si dos cambian lo mismo, el segundo recibe 409.
- **Eventos** (`kleinkauf:events`, panel en vivo `store.activity`): `order.created` (online y local) y
  `order.receipt-uploaded`, con `{ number, channel, status, total, buyerName }`, después del commit.
- **Auditoría:** `SalesSettings` es `@Audited`; los cambios de estado del administrador (`STATUS_CHANGE`) y la
  descarga de comprobantes (`READ`) se registran a mano. Ver [auditoria.md](../auditoria.md).
- **Vencimiento:** `PENDIENTE_PAGO` o `RECHAZADO` con el plazo cumplido pasan a `VENCIDO` ("Vencio el plazo para
  pagar", sin usuario) y se devuelve el stock. `PAGO_EN_REVISION` no vence. Si products no responde, se vence en la
  pasada siguiente.

## Modelo de datos

Base `sales`, un schema por tienda (`ddl-auto=update`).

| Tabla | Qué guarda |
|---|---|
| `orders` | `number` (único), estado, canal, medio de pago, comprador, `user_id`, entrega y dirección, notas, descuento, total, `stock_review`, `expires_at`, `paid_at` (fecha de la venta), `version`, `created_at`, `updated_at` |
| `order_items` | Tipo, `item_id`, nombre, cantidad, precio de lista, precio cobrado, costo unitario y subtotal al vender |
| `order_status_history` | Estado, fecha, usuario y motivo |
| `payment_receipts` | Ruta del archivo (relativa a `receipts.path`), tipo, tamaño y fecha. No se borran |
| `sales_settings` | Alias, CBU, titular, banco y horas de vencimiento (una fila) |

Los comprobantes están en disco en `receipts.path/<schema>/<número>/`.

## Configuración

| Propiedad | Valor en desarrollo | Para qué |
|---|---|---|
| `server.port` / `management.server.port` | `3003` / `9003` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/sales` | Base |
| `spring.data.redis.*` | `localhost:6379` | Roles cacheados y eventos |
| `users.url`, `products.url` | `http://users-microservice/`, `http://products-microservice/` | Por Eureka |
| `api.internal.key` | — | Token interno. **Cambiarlo fuera de desarrollo** |
| `sales.order.max-lines` / `max-quantity` | 30 / 20 | Topes del pedido online |
| `sales.local.max-lines` / `max-quantity` | 100 / 999 | Topes de la venta del local |
| `receipts.path` | `uploads/sales/receipts` | Carpeta de los comprobantes |
| `sales.receipts.max-per-order` / `max-size` | 5 / 5 MB | Comprobantes |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | 5 MB / 6 MB | Subidas |
| `sales.reports.zone` | `America/Argentina/Buenos_Aires` | Qué es "un día" en reportes y filtros |
| `sales.expiration.interval` / `initial-delay` | `PT1H` / `PT1M` | Tarea de vencimiento |
| `http.client.connect-timeout` / `read-timeout` | `2s` / `3s` | Llamadas a products y users |
| `resilience4j.*.instances.products.*` | | Circuit breaker y reintentos hacia products |

Levantarlo solo: `cd backend/kleinkauf-sales && ./gradlew bootRun` (con products y users arriba para vender).

## Límites conocidos

- Los empleados no pueden usar el panel de ventas (no hay área `VENTAS`).
- Un pedido online solo se paga por transferencia; no hay pago al retirar ni Mercado Pago.
- La zona horaria es una para todas las tiendas.
- Sin avisos por mail al comprador ni a la tienda.
- Bases de desarrollo creadas con la primera etapa: hay que sacar el `NOT NULL` de `order_items.list_price` a mano
  (ver [ventas.md](../ventas.md#datos)).
- Más pendientes en [ventas.md](../ventas.md#para-después).
