# Ventas (decor-sales)

Microservicio de ventas: el comprador arma el carrito en la tienda, crea el pedido (que reserva el stock en products),
paga por transferencia y sube el comprobante. El administrador valida el pago, sigue la preparación y la entrega,
carga las ventas del local y configura los datos para cobrar.

> Estado: **etapas 1 y 2 de 3 implementadas** (pedidos, pagos y ventas del local). Los reportes están en
> [Pendiente](#pendiente).

## Resumen

| | |
|---|---|
| Proyecto | `backend/decor-sales` (Gradle, `includeBuild('../decor-common')`) |
| Servicio | `sales-microservice`, puerto **3003**, registrado en Eureka |
| Base | Postgres `sales`, **un schema por tenant** (se crea y migra solo, igual que products) |
| Archivos | Comprobantes en disco: `receipts.path` (`uploads/sales/receipts`), una carpeta por tienda y pedido |
| Tenant | Obligatorio: sin `X-Forwarded-Host` responde 404 |
| Gateway | `/api/v1/sales/**` → `lb://sales-microservice` |
| Depende de | **products** (stock y contador de vendidos) y **users** (roles del usuario, tenants) |

## Qué puede hacer

### Tienda (público, invitado o logueado)

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/checkout` | Datos para pagar (alias, CBU, titular, banco), plazo para pagar, tipos de entrega y topes del pedido. `paymentConfigured: false` si la tienda todavía no cargó los datos de pago |
| POST | `/api/v1/sales/orders` | Crea el pedido y reserva el stock. Devuelve número, estado, total, vencimiento y datos para transferir (201). Si viene con token, lo asocia al usuario |
| POST | `/api/v1/sales/orders/lookup` | `{email, number}` → el pedido. Por POST para que el email no quede en la URL ni en los logs |
| POST | `/api/v1/sales/orders/{number}/receipt` | Multipart: `file` (JPG, PNG o PDF hasta 5 MB) y `email`. Pasa a `PAGO_EN_REVISION` y devuelve el pedido |
| POST | `/api/v1/sales/orders/{number}/cancel` | `{email}` → cancela si todavía no se validó el pago y devuelve el stock |

### Comprador logueado

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/sales/orders/mine?page&size` | Sus pedidos, más nuevos primero, paginados (20 por defecto, máximo 100) |

### Administrador (solo `ADMINISTRADOR`)

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/v1/sales/admin/orders` | **Venta del local** ya cobrada (201). Ver [Venta del local](#venta-del-local) |
| GET | `/api/v1/sales/admin/orders` | Lista paginada. Filtros: `status`, `channel`, `paymentMethod`, `stockReview`, `from` y `to` (`yyyy-MM-dd`, inclusive), `q` (número exacto o parte del email), `page`, `size` |
| GET | `/api/v1/sales/admin/orders/{number}` | Detalle: comprador, entrega, ítems con precio de lista, precio cobrado y costo, historial con el usuario de cada cambio y comprobantes |
| PUT | `/api/v1/sales/admin/orders/{number}/status` | `{status, reason}`: validar, rechazar, avanzar o cancelar. Ver [Cambios de estado](#cambios-de-estado) |
| GET | `/api/v1/sales/admin/orders/{number}/receipts/{id}` | Descarga un comprobante (`attachment; filename="comprobante-<número>-<id>.<ext>"`) |
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

### Ejemplo: venta del local

```json
POST /api/v1/sales/admin/orders
{
  "items": [
    { "type": "PRODUCT", "id": 5, "quantity": 1, "price": 14000.00 },
    { "type": "PRODUCT", "id": 8, "quantity": 2 }
  ],
  "discount": 1000.00,
  "paymentMethod": "EFECTIVO",
  "status": "ENTREGADO",
  "buyer": { "name": "Juan" }
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

### Consulta, comprobante y cancelación del invitado

- Las tres piden el **email y el número**. Si el número no existe o el email no coincide responden el mismo
  **404**, así no se puede saber qué números existen.
- La consulta muestra ítems, total, entrega, historial (estado, fecha y motivo), cuántos comprobantes subió y los
  datos para transferir solo mientras puede subir el comprobante. No muestra costos ni quién hizo cada cambio.
- **Comprobante:**
  - Solo en `PENDIENTE_PAGO` o `RECHAZADO` y antes de que venza el plazo; si no, **409**.
  - JPG, PNG o PDF de hasta **5 MB**. El tipo se reconoce por los primeros bytes del archivo, no por el nombre ni
    por el content-type: cualquier otra cosa es **400**.
  - Hasta **5 por pedido** (cuentan los rechazados). No se borran: quedan como respaldo de la venta.
  - Solo los descarga el administrador; no hay URL pública.
- **Cancelación:** solo mientras no se validó el pago (`PENDIENTE_PAGO`, `PAGO_EN_REVISION`, `RECHAZADO`); después,
  **409**. Devuelve el stock y queda con el motivo "Cancelado por el comprador".

### Cambios de estado

| Cambio | Desde | Efecto |
|---|---|---|
| Validar → `PAGADO` | `PENDIENTE_PAGO`, `PAGO_EN_REVISION`, `RECHAZADO` | Suma el pedido al contador de vendidos. Se puede validar sin comprobante si pagó por otro medio |
| Rechazar → `RECHAZADO` | `PAGO_EN_REVISION` | **Motivo obligatorio.** El plazo para subir otro comprobante corre de nuevo desde el rechazo |
| Avanzar → `EN_PREPARACION`, `LISTO`, `ENTREGADO` | Un estado pago anterior | Se pueden saltear pasos (`PAGADO → ENTREGADO`); nunca hacia atrás |
| Cancelar → `CANCELADO` | Cualquier estado no final | **Motivo obligatorio.** Devuelve el stock; si ya estaba pago, lo resta de vendidos. La devolución del dinero se arregla por fuera |

- Cualquier otro cambio responde **409**. `ENTREGADO`, `CANCELADO` y `VENCIDO` son finales.
- Primero se actualiza products y después se guarda el pedido: si products no responde, **503** y el pedido no
  cambia (se puede reintentar; products no aplica nada dos veces).
- El pedido tiene control optimista (`@Version`): si otro lo cambió mientras tanto (otro administrador, el comprador
  o el vencimiento), **409** "Reload it and try again". Si se acababa de sumar a vendidos, se resta.
- Cada cambio queda en el historial con el estado, la fecha, el usuario y el motivo.

### Vencimiento

- Una tarea (`ExpirationJob`) corre cada **1 hora** (`sales.expiration.interval`, ISO-8601) y recorre todas las
  tiendas.
- Vence los pedidos en `PENDIENTE_PAGO` o `RECHAZADO` con el plazo cumplido: pasan a `VENCIDO` (motivo "Vencio el
  plazo para pagar", sin usuario) y se devuelve el stock.
- Un pedido en `PAGO_EN_REVISION` no vence: lo resuelve el administrador.
- Si products no responde, el pedido queda como estaba y se vence en la pasada siguiente. Mientras tanto ya no
  acepta comprobantes, porque el plazo pasó.

### Venta del local

| Campo | Regla |
|---|---|
| `items` | 1 a **100 líneas** y 1 a **999 unidades** (`sales.local.*`). `price` opcional: precio cobrado si se cambia (≥ 0); sin `price` se usa el actual |
| `discount` | Monto en pesos sobre el subtotal, ≥ 0 y no más que el subtotal (null = 0) |
| `paymentMethod` | Obligatorio: `EFECTIVO`, `TRANSFERENCIA`, `DEBITO`, `CREDITO` u `OTRO` |
| `status` | `PAGADO` (para preparar o enviar después) o `ENTREGADO` |
| `buyer` | Opcional (consumidor final). Si viene algún dato, se valida igual que online |
| `deliveryType` | Opcional, `RETIRO` por defecto. `ENVIO` pide la dirección |

- **Productos "a consultar":** se pueden vender poniendo el precio. Sin `price` responde **400** con los nombres
  y devuelve el stock. El precio de lista queda vacío.
- **Sin stock suficiente:** se vende igual. products registra un `AJUSTE` automático por lo que falta (el stock
  queda en 0), el pedido queda con `stockReview: true` y el motivo del historial dice qué productos faltaron.
- Queda con el precio de lista y el cobrado de cada ítem, canal `LOCAL`, sin vencimiento, y suma a vendidos al
  crearse. El historial registra `PAGADO` (y `ENTREGADO` si corresponde) con el administrador.
- **Anular:** se cancela con motivo como cualquier pedido. Vuelve **todo lo vendido** al stock (también lo que se
  había ajustado: la mercadería existe aunque el sistema dijera que no) y se resta de vendidos.

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
| Invitado | Checkout, crear pedidos, consultar, subir el comprobante y cancelar un pedido con email y número |
| Cualquier usuario logueado | Lo mismo (el pedido queda asociado a él) y ver sus pedidos |
| `ADMINISTRADOR` | Todo el panel de ventas, las ventas del local y la configuración |
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

Venta del local: nace en PAGADO o ENTREGADO
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

## Cómo se comunica

```
Tienda (Origin: http://mi-tienda.localhost:2999)
   │
   ▼
gateway :3000
   - rutas públicas: GET /checkout, POST /orders, POST /orders/lookup,
     POST /orders/{number}/receipt, POST /orders/{number}/cancel
   - POST /orders: si viene un JWT válido agrega X-User-Id; si el JWT es inválido → 401
   - rate limit por IP: 5 pedidos/min, 10 consultas o cancelaciones/min, 5 comprobantes/min
     (el resto, el límite general)
   - agrega X-Forwarded-Host (schema del tenant)
   │
   ▼
decor-sales :3003
   TenantFilter      X-Forwarded-Host → schema
   HeaderAuthFilter  X-User-Id → roles de users (caché 1 min → Redis → users)
   │
   ├── POST /api/internal/products/reservations                ──► products :3002
   ├── POST /api/internal/products/reservations/{n}/release
   ├── POST /api/internal/products/reservations/{n}/sold
   └── POST /api/internal/products/reservations/{n}/unsold
         token interno (X-Internal-Token) + X-Forwarded-Host del tenant
         RestTemplate con Eureka, timeouts 2s/3s, Resilience4j "products"

ExpirationJob (cada 1 h) ── por cada tienda: vence pedidos y devuelve su stock
```

### Endpoints internos de products

Solo con el token interno de los microservicios (rol `INTERNAL`); no pasan por el gateway.

| Llamada | Qué hace |
|---|---|
| `POST /reservations` `{reference, items:[{type, id, quantity}], local}` | Valida que todo exista, tenga precio y alcance el stock, y registra una `SALIDA` por producto con la referencia del pedido. **Todo o nada.** Devuelve nombre, precio y costo de cada ítem en el mismo orden, y `shortages` |
| `POST /reservations/{reference}/release` | Registra una `ENTRADA` por cada `SALIDA` de esa referencia. Los productos borrados no vuelven al stock |
| `POST /reservations/{reference}/sold` `{items}` | Suma las cantidades al contador de vendidos (`sold_count`) de cada producto y combo |
| `POST /reservations/{reference}/unsold` `{items}` | Las resta |

- **Idempotentes por número de pedido** (tabla `stock_reservations` en products):
  - Reintentar una reserva no descuenta dos veces: devuelve los datos de nuevo, con los mismos `shortages`.
  - Devolver dos veces no devuelve dos veces.
  - Vendidos se suma una sola vez y se resta una sola vez.
- Si la devolución llega antes que la reserva (timeout en sales), queda marcada como devuelta y la reserva que
  llegue después se rechaza con 409. Sumar a vendidos una reserva devuelta también es 409.
- **`local: true`** (venta del local):
  - Acepta ítems sin precio (`unitPrice: null`).
  - Si falta stock registra un `AJUSTE` por la diferencia antes de la `SALIDA`, así el stock queda en 0, e informa
    los productos en `shortages`.
  - Devolver la reserva devuelve todo lo que salió.
- Errores: datos inválidos → 400; ítem inexistente, combo deshabilitado, sin precio o sin stock → **409** con el
  nombre del ítem (ej. `Not enough stock: Mesa, Silla`). sales devuelve ese mensaje.
- Los combos descuentan sus productos (cantidad del combo × unidades). El costo de un combo es la suma del costo de
  sus productos, o `null` si alguno no tiene costo. En vendidos, un combo suma al combo, no a sus productos.
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

La venta del local sigue los mismos pasos con `local: true`, después calcula precios y descuento (si falla, devuelve
el stock), **suma a vendidos** (si falla, devuelve el stock) y guarda (si falla, resta de vendidos y devuelve).

Si una de estas vueltas atrás también falla, se loguea un error con el número del pedido para corregirlo a mano (los
movimientos de stock tienen el número como referencia). Los eventos con outbox quedan para después.

### Resilience4j

Instancias `products` y `users` en `application.properties`: circuit breaker (50% de fallas en las últimas 10
llamadas → corta 10 s) y retry (3 intentos cada 200 ms). Los 4xx no cuentan como falla ni se reintentan. Reintentar
es seguro porque todas las operaciones de products son idempotentes.

## Datos

Tablas por schema en la base `sales`:

| Tabla | Qué guarda |
|---|---|
| `orders` | Número, estado, canal (`ONLINE` / `LOCAL`), medio de pago, comprador, `user_id`, entrega y dirección, notas, descuento, total, `stock_review`, `expires_at`, `version` (control optimista), fechas |
| `order_items` | Tipo (`PRODUCT` / `BUNDLE`), `item_id`, nombre, cantidad, precio de lista (null si era "a consultar"), precio cobrado, costo unitario y subtotal al momento de la venta |
| `order_status_history` | Estado, fecha, usuario (null si fue un invitado o el sistema) y motivo |
| `payment_receipts` | Ruta del archivo (relativa a `receipts.path`), tipo, tamaño y fecha |
| `sales_settings` | Alias, CBU, titular, banco y horas de vencimiento (una fila) |

En products:
- `stock_reservations`: referencia, estado, si ya sumó a vendidos (`sold`) y los productos que faltaron
  (`shortages`).
- La columna `reference` en `stock_movements`.
- `sold_count` en productos y combos. Todavía no se muestra en ningún lado: es para los reportes.

> Bases de desarrollo creadas con la etapa 1: Hibernate (`ddl-auto=update`) no saca el `NOT NULL` de
> `order_items.list_price`. Para vender productos "a consultar" en el local, correr en cada schema:
> `ALTER TABLE <schema>.order_items ALTER COLUMN list_price DROP NOT NULL;`

## Cambios en otros servicios

- **products:**
  - Endpoints internos de reserva y vendidos (rol `INTERNAL` para `/api/internal/**`).
  - Combos en el catálogo público (`GET /api/v1/catalog/bundles`, con disponibilidad e imagen del primer
    producto) e `id` en los productos del catálogo.
  - `sold_count` en productos y combos. Los cambios en combos invalidan la caché del catálogo.
- **gateway:**
  - Ruta a sales y rutas públicas del checkout. El placeholder `{code}` en `PermittedUrls` es un solo segmento
    del path.
  - Login opcional en `POST /orders`.
  - Límites `limit.order`, `limit.lookup` y `limit.receipt`.
- **deploy:** base `sales` en `init.sql` y `sales-service` en `docker-compose.dev.yml`.

## Pendiente

### Etapa 3: reportes

- Totales por día/semana/mes, por producto/combo (ranking) y ganancia (precio cobrado − costo guardado),
  desglosados por canal y medio de pago.
- Mostrar el contador de vendidos en el panel y orden "más vendidos" en el catálogo (sin mostrar el número).

Para decidir: zona horaria de los reportes, si se cachean y si se exportan a CSV.

### Para después

- **Mercado Pago** (pago automático: el webhook pondría `PAGADO`).
- **Eventos** (Redis Streams + outbox) para stock y vendidos, en vez de llamadas síncronas.
- **Avisos por email** al comprador y a la tienda.
- Comprobante imprimible (no fiscal) de la venta del local.
- Costo de envío y logística.
- Delegar la gestión de pedidos a `EMPLEADO` (área `VENTAS` en los permisos).
- Actualizar `endpoints.md`, `tenants.md`, `permisos.md` y `MVP.md` con sales.
