# sales (kleinkauf-sales)

Spring Boot (servlet) on port **3003**, registered in Eureka as `sales-microservice`. Postgres database `sales`, one
schema per store (created with the store's first request). Payment receipts go to disk.

Shared conventions (headers, errors, pagination, dates): [README](README.md). Design and the details of the
communication with products: [sales.md](../features/sales.md).

## What it does

- **Online store:** checkout, orders from guests or logged-in users with stock reserved in products, bank transfer
  payment with a receipt, lookup and cancellation by the buyer, "my orders".
- **Sales panel (`ADMINISTRADOR` only):** order list and detail, validate or reject the payment, advance states,
  cancel, download receipts, store settings (transfer details and deadline).
- **In-store sale:** the admin enters a sale already paid, with a changed price, discount and payment method, even if
  stock is short.
- **Reports:** totals by period, channel and payment method, and a product ranking.
- **Automatic expiration** of unpaid orders.

### Concepts

| Concept | What it is |
|---|---|
| Order | Identified by its **number** (`K7QM-4X2P`): random, no `0/O` or `1/I/L`, unique per store, case-insensitive |
| Channel | `ONLINE` (the store) or `LOCAL` (in-store sale, entered by the admin) |
| Payment method | `EFECTIVO` (cash), `TRANSFERENCIA`, `DEBITO`, `CREDITO`, `OTRO`. Online orders are always `TRANSFERENCIA` |
| Delivery | `RETIRO` (pick-up) or `ENVIO` (shipping, with address). Shipping cost is agreed separately by the store |
| Item | `PRODUCT` or `BUNDLE`, with the catalog `id`. The backend sets the price; the cart's price is only used to warn if it changed |
| Sale | A paid order (`PAGADO`, `EN_PREPARACION`, `LISTO`, `ENTREGADO`). Dated by its payment |
| `stockReview` | In-store sale made without enough stock: products adjusted the stock and it should be checked |

### States

<p align="center"><img src="../diagrams/order-states.svg" alt="Order states and their transitions"></p>

| State | Meaning | Stock | Sale | Receipt | Buyer can cancel |
|---|---|---|---|---|---|
| `PENDIENTE_PAGO` | Created, waiting for the receipt | Reserved | No | Yes | Yes |
| `PAGO_EN_REVISION` | Receipt uploaded | Reserved | No | No | Yes |
| `RECHAZADO` | Invalid receipt (with a reason) | Reserved | No | Yes | Yes |
| `PAGADO` | The admin validated the payment | Deducted | **Yes** | No | No |
| `EN_PREPARACION` | Being prepared | Deducted | Yes | No | No |
| `LISTO` | Ready for pick-up or shipping | Deducted | Yes | No | No |
| `ENTREGADO` | Final (an in-store sale can still be voided) | Deducted | Yes | No | No |
| `CANCELADO` | Final (with a reason) | Returned | No | No | — |
| `VENCIDO` | Final: the deadline passed without a receipt | Returned | No | No | — |

Admin changes (`PUT /admin/orders/{number}/status`):

| Change | From | Effect |
|---|---|---|
| Validate → `PAGADO` | `PENDIENTE_PAGO`, `PAGO_EN_REVISION`, `RECHAZADO` | Adds to sold in products. Can be validated without a receipt (paid some other way) |
| Reject → `RECHAZADO` | `PAGO_EN_REVISION` | **Reason required.** The deadline to upload another receipt restarts |
| Advance → `EN_PREPARACION`, `LISTO`, `ENTREGADO` | An earlier paid state | Steps can be skipped; never backwards |
| Cancel → `CANCELADO` | Any non-final state, and `ENTREGADO` for an in-store sale | **Reason required.** Returns the stock; if it was paid, subtracts from sold. Refunds are handled separately |

Any other change answers 409. Each change goes into the history with date, user and reason.

## Purchase flow (store frontend)

1. **Checkout:** `GET /api/v1/sales/checkout`. If `paymentConfigured` is `false`, the store has not entered its payment
   details and nobody can buy: show it and do not let the buyer continue. It also carries the cart limits and the
   payment deadline.
2. **Create the order:** `POST /api/v1/sales/orders` with the cart items (and the price the buyer saw), the buyer's
   data and the delivery. With a session, send the token: the order shows up in "my orders".
   - **201:** show the number, total, expiration and transfer details. Keep number and email (everything that follows
     needs them). The buyer also gets an email with the same data and the link to the order (sent by users in the
     background; if it fails, the order does not change). See [mail.md](../mail.md).
   - **409 with a JSON `items`:** some price changed. Show `expectedPrice` → `currentPrice`, update the cart and ask to
     confirm again.
   - **409 with text:** no stock (`Not enough stock: Mesa, Silla`), item not available or without a price, or the
     store has no payment details.
   - **503:** products did not answer; try again later.
3. **Pay:** the buyer transfers outside the system and uploads the receipt with
   `POST /api/v1/sales/orders/{number}/receipt` (number + email). The order moves to `PAGO_EN_REVISION`.
4. **Follow the order:** `POST /api/v1/sales/orders/lookup` with email and number. If it was rejected, `history` has
   the reason and `payment` shows up again to upload another receipt.
5. **Cancel:** `POST /api/v1/sales/orders/{number}/cancel` with the email, while the payment has not been validated.
6. **My orders:** `GET /api/v1/sales/orders/mine` with the token.

An order without a receipt expires after `expirationHours` (48 by default) and returns its stock.

## Admin flow

1. **Configure** the transfer details once: `PUT /api/v1/sales/admin/settings`. Without a holder and an alias or CBU,
   the store does not accept orders.
2. **Review payments:** `GET /api/v1/sales/admin/orders?status=PAGO_EN_REVISION`, open the detail, download the receipt
   and validate (`PAGADO`) or reject with a reason.
3. **Prepare and deliver:** advance to `EN_PREPARACION`, `LISTO`, `ENTREGADO`.
4. **Sell in the store:** `POST /api/v1/sales/admin/orders`. If stock was short, the sale comes out with `stockReview`
   and the product's stock should be checked (`GET /api/v1/sales/admin/orders?stockReview=true`).
5. **Void** any non-final order, or an in-store sale already delivered: cancel with a reason.
6. **See reports:** `GET /api/v1/sales/admin/reports/summary` and `/products`.

## Store (checkout)

### GET /api/v1/sales/checkout

**Access:** Public

```bash
curl http://localhost:3000/api/v1/sales/checkout -H 'Origin: http://demo.localhost:2999'
```

Response **200**:

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

`payment` is `null` if `paymentConfigured` is `false`.

### POST /api/v1/sales/orders

**Access:** Public, optional login · **Rate limit:** 5 per minute

Creates the order and reserves stock in products. With a valid token it is linked to the user; with an invalid one, 401.

| Field | Rule |
|---|---|
| `items` | 1 to 30 lines, 1 to 20 units each. `type` (`PRODUCT`/`BUNDLE`), `id`, `quantity` and `price` (the cart's) required. Repeated lines are merged before counting |
| `buyer` | `name` (up to 100), valid `email` (up to 150, stored lower case) and `phone` (6 to 30: digits, spaces, `+`, `-`, `()`), required even when logged in |
| `deliveryType` | `RETIRO` or `ENVIO` |
| `address` | Only with `ENVIO`: `street`, `number`, `city`, `province`, `postalCode` required (up to 100), `apartment` optional |
| `notes` | Optional, up to 500 |

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
    "notes": "Ring the bell"
  }'
```

Response **201**:

```json
{
  "number": "K7QM-4X2P",
  "status": "PENDIENTE_PAGO",
  "total": 72000.00,
  "expiresAt": "2026-11-05T13:30:00",
  "payment": { "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia" }
}
```

Response **409** if some price changed (JSON; the order is not created and the stock is returned):

```json
{
  "message": "Some prices changed. Please review the order and confirm again",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "expectedPrice": 15000.00, "currentPrice": 16500.00 } ]
}
```

| Status | When | Message |
|---|---|---|
| 400 | Invalid items | `The order needs between 1 and 30 items, each with type, id, price and a quantity between 1 and 20` |
| 400 | Invalid buyer | `Name (up to 100 characters), a valid email and a phone are required` |
| 400 | No delivery | `The delivery type is required (RETIRO or ENVIO)` |
| 400 | Incomplete address with `ENVIO` | `Shipping needs street, number, city, province and postal code (up to 100 characters each)` |
| 400 | Notes too long | `Notes must be 500 characters or fewer` |
| 409 | The store has not entered its payment details | `The store has not configured its payment details yet` |
| 409 | Prices changed | The JSON above |
| 409 | No stock, item not available or without a price (answered by products) | `Not enough stock: Mesa, Silla` · `Item not available` · `Item without price: Mesa` |
| 503 | products does not answer or the order could not be saved | `The order could not be created right now. Please try again in a few minutes` |

### POST /api/v1/sales/orders/lookup

**Access:** Public (email) · **Rate limit:** 10 per minute (shared with cancel)

The order as the buyer sees it. A POST so the email does not end up in the URL.

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders/lookup \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com","number":"k7qm-4x2p"}'
```

Response **200** (`OrderDto`):

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
  "notes": "Ring the bell",
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

- `payment` only while a receipt can be uploaded (`PENDIENTE_PAGO` or `RECHAZADO`); otherwise `null`.
- No costs and no information on who made each change (`userId` always `null`). `receipts` is the number of receipts.

Error: 404 `Order not found` (unknown number or non-matching email: they are indistinguishable).

### POST /api/v1/sales/orders/{number}/receipt

**Access:** Public (email) · **Rate limit:** 5 per minute · `multipart/form-data`

Uploads the receipt (`file`) with the order's email (`email`). Moves to `PAGO_EN_REVISION`.

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders/K7QM-4X2P/receipt \
  -H 'Origin: http://demo.localhost:2999' \
  -F 'email=ana@mail.com' -F 'file=@transfer.pdf'
```

Response **200**: `OrderDto`.

| Status | When | Message |
|---|---|---|
| 400 | Not JPG, PNG or PDF (by content, not name) or larger than 5 MB | `The receipt must be a JPG, PNG or PDF file of 5 MB or less` |
| 404 | Number or email | `Order not found` |
| 409 | The order is not in `PENDIENTE_PAGO` or `RECHAZADO`, or it expired | `Receipts can only be uploaded while the order is waiting for payment and before it expires` |
| 409 | It already has 5 (rejected ones count) | `The order already has the maximum of 5 receipts` |
| 409 | Someone else changed it at the same time | `The order was changed by someone else. Reload it and try again` |
| 413 | The request exceeds 6 MB | `The file exceeds the maximum upload size` |

Publishes `order.receipt-uploaded` for the live panel.

### POST /api/v1/sales/orders/{number}/cancel

**Access:** Public (email) · **Rate limit:** 10 per minute (shared with lookup)

```bash
curl -X POST http://localhost:3000/api/v1/sales/orders/K7QM-4X2P/cancel \
  -H 'Origin: http://demo.localhost:2999' -H 'Content-Type: application/json' \
  -d '{"email":"ana@mail.com"}'
```

Response **200**: `OrderDto` in `CANCELADO`, with the reason "Cancelado por el comprador". The stock comes back.

| Status | When | Message |
|---|---|---|
| 404 | Number or email | `Order not found` |
| 409 | The payment was already validated, or it is in a final state (`VENCIDO` or `CANCELADO` too) | `The order can no longer be cancelled: the payment was already validated` |
| 503 | products does not answer | `The stock could not be updated right now. Please try again in a few minutes` |

### GET /api/v1/sales/orders/mine

**Access:** Login

The user's orders, newest first. `page` (0) and `size` (20, at most 100).

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

For the detail, use `lookup` with the order's email. Error: 400 `The page must be 0 or greater and the size between 1 and 100`.

## Sales panel

All of `/api/v1/sales/admin/**` is **`ADMINISTRADOR` only** (not delegated to employees).

### GET /api/v1/sales/admin/orders

Paginated list, newest first (`OrderSummaryDto`, as in "my orders").

| Parameter | What it does |
|---|---|
| `status` | One state |
| `channel` | `ONLINE` or `LOCAL` |
| `paymentMethod` | One payment method |
| `stockReview` | `true`: only in-store sales made without stock |
| `from`, `to` | `yyyy-MM-dd`, store days (Argentina), inclusive, by creation date |
| `q` | Exact number or part of the email |
| `page`, `size` | 0 and 20 (at most 100) |

```bash
curl 'http://localhost:3000/api/v1/sales/admin/orders?status=PAGO_EN_REVISION' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Errors: 400 `Invalid filter: <field>` (invalid value), 400 for the page.

### GET /api/v1/sales/admin/orders/{number}

Full detail (`AdminOrderDto`):

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

`listPrice` is the list price at the time of sale (`null` if it was "ask for price"); `unitPrice` is what was charged;
`unitCost` the cost at the time of sale (`null` if it had none). Error: 404 `Order not found`.

### PUT /api/v1/sales/admin/orders/{number}/status

Changes the state following the [change table](#states).

```bash
curl -X PUT http://localhost:3000/api/v1/sales/admin/orders/K7QM-4X2P/status \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' -d '{"status":"RECHAZADO","reason":"El monto no coincide"}'
```

Response **200**: `AdminOrderDto`.

| Status | When | Message |
|---|---|---|
| 400 | Reject or cancel without a reason (or a reason over 500) | `A reason (up to 500 characters) is required to reject or cancel` |
| 404 | Does not exist | `Order not found` |
| 409 | Change not allowed | `The order cannot change from <current> to <new>` |
| 409 | Someone else changed it at the same time | `The order was changed by someone else. Reload it and try again` |
| 503 | products does not answer (the order does not change; can be retried) | `The stock could not be updated right now. Please try again in a few minutes` |

Recorded in the audit log as `STATUS_CHANGE` with the reason.

### GET /api/v1/sales/admin/orders/{number}/receipts/{id}

Downloads a receipt (`Content-Disposition: attachment; filename="comprobante-<number>-<id>.<ext>"`, with its content
type). Recorded in the audit log as a sensitive read. There is no public URL.

Error: 404 `Receipt not found`.

### POST /api/v1/sales/admin/orders

In-store sale, already paid.

| Field | Rule |
|---|---|
| `items` | 1 to 100 lines, 1 to 999 units. `price` optional: the charged price (≥ 0); without `price`, the current one. Required for "ask for price" products |
| `discount` | Amount in pesos off the subtotal, ≥ 0 and no more than the subtotal (`null` = 0) |
| `paymentMethod` | Required |
| `status` | `PAGADO` (to prepare or ship later) or `ENTREGADO` |
| `buyer` | Optional (walk-in customer). Validated if any field is present |
| `deliveryType`, `address` | Optional, `RETIRO` by default. `ENVIO` requires the address |
| `notes` | Optional, up to 500 |

```bash
curl -X POST http://localhost:3000/api/v1/sales/admin/orders \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"type":"PRODUCT","id":5,"quantity":1,"price":14000.00},{"type":"PRODUCT","id":8,"quantity":2}],
       "discount":1000.00,"paymentMethod":"EFECTIVO","status":"ENTREGADO","buyer":{"name":"Juan"}}'
```

Response **201**: `AdminOrderDto` with channel `LOCAL`, no expiration.

- **Without enough stock it sells anyway:** products adjusts the stock to 0, the order comes out with
  `stockReview: true` and the history says which products were short.
- Adds to sold when created. Publishes `order.created` with `channel: LOCAL`.
- To void it, cancel it with a reason: **everything** sold goes back to stock and is subtracted from sold.

| Status | When | Message |
|---|---|---|
| 400 | Invalid items | `The sale needs between 1 and 100 items, each with type, id, a quantity between 1 and 999 and, if given, a price of zero or greater` |
| 400 | "Ask for price" products without a price (stock is returned) | `These items have no price, enter one: Mesa, Silla` |
| 400 | Invalid discount | `The discount must be zero or greater and not exceed the subtotal` |
| 400 | No payment method | `The payment method is required (EFECTIVO, TRANSFERENCIA, DEBITO, CREDITO or OTRO)` |
| 400 | Invalid initial state | `A store sale starts as PAGADO or ENTREGADO` |
| 400 | Invalid buyer | `Buyer data is optional, but if given: name up to 100 characters, a valid email and a valid phone` |
| 400 | Delivery or address | Same as [creating an order](#post-apiv1salesorders) |
| 409 | Missing product or bundle, or disabled bundle (products) | `Item not available` |
| 503 | products does not answer | `The order could not be created right now. Please try again in a few minutes` |

### GET /api/v1/sales/admin/settings

```json
{ "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia", "expirationHours": 48 }
```

Before it is configured, everything is `null` except `expirationHours` (48).

### PUT /api/v1/sales/admin/settings

Replaces the settings (empty fields are stored as `null`; empty `expirationHours` = 48).

| Field | Rule |
|---|---|
| `alias` | 6 to 20 letters, digits, dots or hyphens |
| `cbu` | 22 digits |
| `holder`, `bank` | Up to 100 |
| `expirationHours` | 1 to 720 |

Accepting online orders requires `holder` and `alias` or `cbu`.

Response **200**: `SalesSettingsDto`. Error: 400 `Alias must have 6 to 20 letters, numbers, dots or hyphens, CBU 22
digits, holder and bank up to 100 characters and the expiration between 1 and 720 hours`.

## Reports

**Access:** ADMINISTRADOR. A sale is a paid order, dated by its payment (when it was validated, or when the in-store sale
was entered); an order cancelled after payment stops counting. Amounts use the prices and costs saved at the time of
sale. Computed on request (no cache).

| Parameter | Rule |
|---|---|
| `from`, `to` | `yyyy-MM-dd`, store days (Argentina), inclusive. Default: the last 30 days. At most 366 days |
| `channel` | `ONLINE` or `LOCAL`; empty = both |

Each total (`ReportTotalsDto`):

| Field | What it is |
|---|---|
| `orders`, `units` | Orders and units sold |
| `revenue` | What was charged (discount already subtracted) |
| `discounts` | Discounts on in-store sales |
| `cost` | Cost of the units sold |
| `profit` | `revenue - cost` |
| `unitsWithoutCost` | Units without a cost. **If above 0, profit is higher than reality: warn about it in the panel** |

### GET /api/v1/sales/admin/reports/summary

Extra parameters: `groupBy` = `day` (default), `week` (from Monday) or `month`, and `paymentMethod`.

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

- `periods` has every period in the range, including zero ones (so charts have no gaps).
- `byChannel` and `byPaymentMethod` only those with sales.

### GET /api/v1/sales/admin/reports/products

Product and bundle ranking. Extra parameters: `sort` = `units` (default), `revenue` or `profit`, and `limit` (1 to 100,
default 20).

```json
{
  "from": "2026-10-01", "to": "2026-10-31", "sort": "UNITS",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "units": 12, "revenue": 180000.00,
               "cost": 108000.00, "profit": 72000.00, "unitsWithoutCost": 0 } ]
}
```

A bundle and a product with the same id are different rows. `revenue` is **before** the order discount. The name is the
one from the most recent sale.

Errors for both: 400 `Invalid filter: <field>` and 400 `The date range must have from before to and be 366 days or
shorter`.

## How it communicates

<p align="center"><img src="../diagrams/sales-flow.svg" alt="How a sales request flows through the gateway, sales, products and users"></p>

- **Creating an order:** reserve in products (all or nothing) → compare prices (if they changed, release and 409) →
  save. If saving fails, the stock is released. If products does not answer, it asks to release just in case and
  answers 503.
- **Expiration:** `ExpirationJob` runs every hour, the first time a minute after the service starts.
- **State changes:** products first (sold, release), then the order. Calls are idempotent by order number: retrying
  never applies anything twice. Details in [sales.md](../features/sales.md#creating-an-order-step-by-step).
- **Optimistic locking:** the order has `@Version`; if two people change it, the second gets 409.
- **Events** (`kleinkauf:events`, live `store.activity` panel): `order.created` (online and in-store) and
  `order.receipt-uploaded`, with `{ number, channel, status, total, buyerName }`, after commit.
- **Audit log:** `SalesSettings` is `@Audited`; the admin's state changes (`STATUS_CHANGE`) and receipt downloads
  (`READ`) are recorded by hand. See [audit.md](../features/audit.md).
- **Expiration:** `PENDIENTE_PAGO` or `RECHAZADO` past the deadline move to `VENCIDO` ("Vencio el plazo para pagar", no
  user) and the stock is returned. `PAGO_EN_REVISION` does not expire. If products does not answer, it expires on the
  next run.

## Data model

`sales` database, one schema per store (`ddl-auto=update`).

| Table | What it stores |
|---|---|
| `orders` | `number` (unique), state, channel, payment method, buyer, `user_id`, delivery and address, notes, discount, total, `stock_review`, `expires_at`, `paid_at` (sale date), `version`, `created_at`, `updated_at` |
| `order_items` | Type, `item_id`, name, quantity, list price, charged price, unit cost and subtotal at the time of sale |
| `order_status_history` | State, date, user and reason |
| `payment_receipts` | File path (relative to `receipts.path`), type, size and date. Never deleted |
| `sales_settings` | Alias, CBU, holder, bank and expiration hours (one row) |

Receipts live on disk at `receipts.path/<schema>/<number>/`.

## Configuration

| Property | Development value | Purpose |
|---|---|---|
| `server.port` / `management.server.port` | `3003` / `9003` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/sales` | Database |
| `spring.data.redis.*` | `localhost:6379` | Cached roles and events |
| `users.url`, `products.url` | `http://users-microservice/`, `http://products-microservice/` | Through Eureka |
| `api.internal.key` | — | Internal token. **Change it outside development** |
| `sales.order.max-lines` / `max-quantity` | 30 / 20 | Online order limits |
| `sales.local.max-lines` / `max-quantity` | 100 / 999 | In-store sale limits |
| `receipts.path` | `uploads/sales/receipts` | Receipt folder |
| `sales.receipts.max-per-order` / `max-size` | 5 / 5 MB | Receipts |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | 5 MB / 6 MB | Uploads |
| `sales.reports.zone` | `America/Argentina/Buenos_Aires` | What "a day" is in reports and filters |
| `sales.expiration.interval` / `initial-delay` | `PT1H` / `PT1M` | Expiration job |
| `http.client.connect-timeout` / `read-timeout` | `2s` / `3s` | Calls to products and users |
| `resilience4j.*.instances.products.*` | | Circuit breaker and retries towards products |

Running it alone: `cd backend/kleinkauf-sales && ./gradlew bootRun` (with products and users up to sell).

## Known limitations

- Employees cannot use the sales panel (there is no `VENTAS` area).
- Online orders are paid only by bank transfer; no pay-on-pick-up and no Mercado Pago.
- A single time zone for every store.
- The buyer gets a confirmation email when the order is created, but no email when its state changes.
