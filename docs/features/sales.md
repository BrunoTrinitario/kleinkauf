# Sales (kleinkauf-sales)

The sales microservice: the buyer fills a cart in the store, creates the order (which reserves stock in products),
pays by bank transfer and uploads the receipt. The admin validates the payment, follows preparation and delivery,
records in-store sales, checks reports and configures the payment details.

Full endpoint reference with examples and errors: [backend/sales.md](../backend/sales.md).

## Overview

| | |
|---|---|
| Project | `backend/kleinkauf-sales` (Gradle, `includeBuild('../kleinkauf-common')`) |
| Service | `sales-microservice`, port **3003**, registered in Eureka |
| Database | Postgres `sales`, **one schema per tenant** (created and migrated automatically, like products) |
| Files | Receipts on disk: `receipts.path` (`uploads/sales/receipts`), one folder per store and order |
| Time zone | `sales.reports.zone` (`America/Argentina/Buenos_Aires`): what "a day" means in reports and date filters |
| Tenant | Required: without `X-Forwarded-Host` it answers 404 |
| Gateway | `/api/v1/sales/**` → `lb://sales-microservice` |
| Depends on | **products** (stock and sold counter) and **users** (user roles, tenants) |

In the frontend: cart, `/checkout`, `/pedido/{number}` and `/mis-pedidos` in the store; `/admin/orders`,
`/admin/orders/new` (in-store sale) and `/admin/reports` in the admin panel.

## What it can do

### Store (public, guest or logged in)

| Method | Path | What it does |
|---|---|---|
| GET | `/api/v1/sales/checkout` | Payment details (alias, CBU, holder, bank), payment deadline, delivery types and order limits. `paymentConfigured: false` if the store has not entered its payment details yet |
| POST | `/api/v1/sales/orders` | Creates the order and reserves stock. Returns number, status, total, expiration and transfer details (201). With a token, links it to the user |
| POST | `/api/v1/sales/orders/lookup` | `{email, number}` → the order. A POST so the email does not end up in the URL or the logs |
| POST | `/api/v1/sales/orders/{number}/receipt` | Multipart: `file` (JPG, PNG or PDF up to 5 MB) and `email`. Moves to `PAGO_EN_REVISION` and returns the order |
| POST | `/api/v1/sales/orders/{number}/cancel` | `{email}` → cancels if the payment has not been validated yet and returns the stock |

### Logged-in buyer

| Method | Path | What it does |
|---|---|---|
| GET | `/api/v1/sales/orders/mine?page&size` | Their orders, newest first, paginated (20 by default, at most 100) |

### Admin (`ADMINISTRADOR` only)

| Method | Path | What it does |
|---|---|---|
| POST | `/api/v1/sales/admin/orders` | **In-store sale**, already paid (201). See [In-store sale](#in-store-sale) |
| GET | `/api/v1/sales/admin/orders` | Paginated list, newest first. Filters: `status`, `channel`, `paymentMethod`, `stockReview`, `from` and `to` (`yyyy-MM-dd`, store days, inclusive), `q` (exact number or part of the email), `page`, `size` |
| GET | `/api/v1/sales/admin/orders/{number}` | Detail: buyer, delivery, items with list price, charged price and cost, history with the user behind each change, and receipts |
| PUT | `/api/v1/sales/admin/orders/{number}/status` | `{status, reason}`: validate, reject, advance or cancel. See [Status changes](#status-changes) |
| GET | `/api/v1/sales/admin/orders/{number}/receipts/{id}` | Downloads a receipt (`attachment; filename="comprobante-<number>-<id>.<ext>"`) |
| GET | `/api/v1/sales/admin/reports/summary` | Totals by day, week or month, broken down by channel and payment method. See [Reports](#reports) |
| GET | `/api/v1/sales/admin/reports/products` | Product and bundle ranking by units, revenue or profit |
| GET | `/api/v1/sales/admin/settings` | The store's sales settings |
| PUT | `/api/v1/sales/admin/settings` | `{alias, cbu, holder, bank, expirationHours}`. Empty fields are stored as `null`; empty `expirationHours` = 48 |

### Example: creating an order

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
  "notes": "Ring the bell"
}
```

```json
201
{
  "number": "K7QM-4X2P",
  "status": "PENDIENTE_PAGO",
  "total": 72000.00,
  "expiresAt": "2026-11-04T15:30:00",
  "payment": { "alias": "tienda.kleinkauf", "cbu": null, "holder": "KleinKauf SRL", "bank": "Galicia" }
}
```

`price` is the price the buyer saw in the cart. If it changed, the answer is **409** and no order is created:

```json
409
{
  "message": "Some prices changed. Please review the order and confirm again",
  "items": [ { "type": "PRODUCT", "id": 5, "name": "Mesa", "expectedPrice": 15000.00, "currentPrice": 16500.00 } ]
}
```

The frontend updates the cart and shows the old and new prices.

### Example: in-store sale

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

## Rules

### Online order

- **Items:** products (`PRODUCT`) or bundles (`BUNDLE`), by id (the catalog returns the `id` of each product and
  bundle). Between 1 and **30 lines** and between 1 and **20 units** per line (`sales.order.max-lines`,
  `sales.order.max-quantity`). Repeated lines are merged before checking the limits.
- **Price:** set by the backend with the current price. The cart's price is only used to warn if it changed (409).
  Products without a price ("ask for price") and bundles without a price cannot be bought online.
- **Bundles:** sold at their price and reserve the stock of each product they contain. They must be enabled and all
  their products must be active.
- **Buyer:** name (up to 100), valid email (up to 150, stored in lower case) and phone (6 to 30 characters: digits,
  spaces, `+`, `-`, `()`), required even when logged in.
- **Delivery:** `RETIRO` (pick-up) or `ENVIO` (shipping). Shipping requires street, number, city, province and postal
  code (up to 100 each); floor/apartment is optional. The shipping cost is agreed separately by the store.
- **Notes:** optional, up to 500 characters.
- **Payment details:** without a holder and an alias or CBU, creating an order answers **409**.
- The order starts as `PENDIENTE_PAGO`, channel `ONLINE`, payment method `TRANSFERENCIA`, no discount, and expires
  `expirationHours` (48 by default) after creation.
- Once created, the buyer gets a confirmation email (see [mail.md](../mail.md)).

### Order number

- Format `XXXX-XXXX` (e.g. `K7QM-4X2P`), random so other people's orders cannot be enumerated. No `0/O` or `1/I/L`, so
  it can be read out loud.
- Unique per store (unique in the schema). Lookup is case-insensitive.

### Guest lookup, receipt and cancellation

- All three require the **email and the number**. If the number does not exist or the email does not match, they
  answer the same **404**, so nobody can learn which numbers exist.
- The lookup shows items, total, delivery, history (status, date and reason), how many receipts were uploaded and the
  transfer details only while a receipt can still be uploaded. It shows neither costs nor who made each change.
- **Receipt:**
  - Only in `PENDIENTE_PAGO` or `RECHAZADO` and before the deadline; otherwise **409**.
  - JPG, PNG or PDF up to **5 MB**. The type is detected from the file's first bytes, not from its name or content
    type: anything else is **400**.
  - Up to **5 per order** (rejected ones count). They are never deleted: they stay as proof of the sale.
  - Only the admin can download them; there is no public URL.
- **Cancellation:** only while the payment has not been validated (`PENDIENTE_PAGO`, `PAGO_EN_REVISION`,
  `RECHAZADO`); afterwards, **409**. Returns the stock and records the reason "Cancelado por el comprador".

### Status changes

| Change | From | Effect |
|---|---|---|
| Validate → `PAGADO` | `PENDIENTE_PAGO`, `PAGO_EN_REVISION`, `RECHAZADO` | Adds the order to the sold counter. Can be validated without a receipt if the buyer paid some other way |
| Reject → `RECHAZADO` | `PAGO_EN_REVISION` | **Reason required.** The deadline to upload another receipt restarts from the rejection |
| Advance → `EN_PREPARACION`, `LISTO`, `ENTREGADO` | An earlier paid status | Steps can be skipped (`PAGADO → ENTREGADO`); never backwards |
| Cancel → `CANCELADO` | Any non-final status, and `ENTREGADO` for an in-store sale | **Reason required.** Returns the stock; if it was paid, subtracts it from sold. Refunds are handled outside the system |

- Any other change answers **409**. `ENTREGADO`, `CANCELADO` and `VENCIDO` are final, with one exception: a delivered
  in-store sale can be voided (see [In-store sale](#in-store-sale)). A delivered online order cannot.
- products is updated first and then the order is saved: if products does not answer, **503** and the order does not
  change (it can be retried; products never applies anything twice).
- The order uses optimistic locking (`@Version`): if someone else changed it in the meantime (another admin, the buyer
  or expiration), **409** "Reload it and try again". If it had just been added to sold, it is subtracted.
- Every change goes to the history with status, date, user and reason.

### Expiration

- A job (`ExpirationJob`) runs every **hour** (`sales.expiration.interval`, ISO-8601) and goes through every store.
- It expires orders in `PENDIENTE_PAGO` or `RECHAZADO` whose deadline passed: they move to `VENCIDO` (reason "Vencio el
  plazo para pagar", no user) and the stock is returned.
- An order in `PAGO_EN_REVISION` does not expire: the admin decides.
- If products does not answer, the order stays as it was and expires on the next run. Meanwhile it no longer accepts
  receipts, since the deadline passed.

### In-store sale

| Field | Rule |
|---|---|
| `items` | 1 to **100 lines** and 1 to **999 units** (`sales.local.*`). `price` optional: the charged price if it differs (≥ 0); without `price` the current one is used |
| `discount` | Amount in pesos off the subtotal, ≥ 0 and no more than the subtotal (null = 0) |
| `paymentMethod` | Required: `EFECTIVO` (cash), `TRANSFERENCIA`, `DEBITO`, `CREDITO` or `OTRO` |
| `status` | `PAGADO` (to prepare or ship later) or `ENTREGADO` |
| `buyer` | Optional (walk-in customer). If any field is present, it is validated like online |
| `deliveryType` | Optional, `RETIRO` by default. `ENVIO` requires the address |

- **"Ask for price" products:** can be sold by entering the price. Without `price` it answers **400** with the names
  and returns the stock. The list price stays empty.
- **Not enough stock:** the sale goes through anyway. products records an automatic `AJUSTE` for what is missing
  (stock ends at 0), the order gets `stockReview: true` and the history reason says which products were short.
- It keeps the list and charged price of each item, channel `LOCAL`, no expiration, and adds to sold when created. The
  history records `PAGADO` (and `ENTREGADO` if applicable) with the admin.
- **Voiding:** cancelled with a reason like any order, **even if already `ENTREGADO`** (a sale entered by mistake or
  returned at the counter; `OrderStatus.canChangeTo(target, channel)`). **Everything sold** goes back to stock (including
  what had been adjusted: the goods exist even if the system said they did not) and it is subtracted from sold.

### Reports

A **sale** is an order in `PAGADO`, `EN_PREPARACION`, `LISTO` or `ENTREGADO`, dated by its payment (`paid_at`: when it
was validated, or when the in-store sale was entered). An order cancelled after payment stops counting. Amounts use
the prices and costs saved at the time of sale, not the current ones.

| Parameter | Rule |
|---|---|
| `from`, `to` | `yyyy-MM-dd`, store days, inclusive. Defaults to the last 30 days up to today. At most **366 days** |
| `groupBy` (summary) | `day` (default), `week` (from Monday) or `month` |
| `channel` | `ONLINE` or `LOCAL`; empty = both |
| `paymentMethod` (summary) | `EFECTIVO`, `TRANSFERENCIA`, `DEBITO`, `CREDITO` or `OTRO`; empty = all |
| `sort` (ranking) | `units` (default), `revenue` or `profit` |
| `limit` (ranking) | 1 to 100, 20 by default |

Each total has:

| Field | What it is |
|---|---|
| `orders`, `units` | Orders and units sold |
| `revenue` | What was charged: order totals with the discount already subtracted |
| `discounts` | Discounts on in-store sales |
| `cost` | Cost of the units sold (cost saved at the time of sale) |
| `profit` | `revenue - cost` |
| `unitsWithoutCost` | Units without a cost. If above 0, profit is higher than reality: the panel warns about it |

- **Summary:** totals for the range, one period per day/week/month (including empty ones, at zero, so charts have no
  gaps) and the breakdown by channel and by payment method (only those with sales).
- **Ranking:** one row per product or bundle (a bundle and a product with the same id are different rows), with the
  name from the most recent sale. There `revenue` adds up line subtotals: it is **before** the order discount, which is
  not spread across items. Ties by name.
- **Time zone:** orders are stored in server time (UTC), but days are cut in the store's zone: a sale on Monday at
  10 pm in Argentina belongs to Monday. The `from`/`to` filters of the order list use the same rule.
- Computed on request, **without a cache**: they read the sales of one store in the range. If volume grows, the first
  step is a cache or summary tables.

### Sales settings

| Field | Rule |
|---|---|
| `alias` | 6 to 20 letters, digits, dots or hyphens |
| `cbu` | 22 digits |
| `holder`, `bank` | Up to 100 characters |
| `expirationHours` | 1 to 720 (48 by default) |

There is a single row per store (`sales_settings`). Until it is saved, the defaults are used. In the frontend it is
the "Datos de cobro" dialog on the orders page; checkout offers the admin to fill it in instead of only saying the
store does not sell online.

### Permissions

| Who | What they can do |
|---|---|
| Guest | Checkout, create orders, look up, upload the receipt and cancel an order with email and number |
| Any logged-in user | The same (the order is linked to them) and see their orders |
| `ADMINISTRADOR` | The whole sales panel, in-store sales and settings |
| `EMPLEADO` | Nothing of the panel, even with every product area (sales management cannot be delegated yet) |

## Order states

```
                ┌──────────── cancelled by buyer / admin ───────────┐
                │                                                    ▼
PENDIENTE_PAGO ──uploads receipt──► PAGO_EN_REVISION ──validates──► PAGADO ──► EN_PREPARACION ──► LISTO ──► ENTREGADO
   │    ▲                                 │
   │    └──── uploads again ─── RECHAZADO ◄┘ rejects
   │
   └── deadline passes without receipt ──► VENCIDO

In-store sale: starts as PAGADO or ENTREGADO
```

| State | Meaning | Stock | Counts as a sale |
|---|---|---|---|
| `PENDIENTE_PAGO` | Created, waiting for the receipt | Reserved | No |
| `PAGO_EN_REVISION` | Receipt uploaded, the admin is reviewing it | Reserved | No |
| `RECHAZADO` | The receipt is not valid (with a reason). Another one can be uploaded | Reserved | No |
| `PAGADO` | The admin validated the payment | Deducted | **Yes** |
| `EN_PREPARACION` | Being prepared | Deducted | Yes |
| `LISTO` | Ready for pick-up or shipping | Deducted | Yes |
| `ENTREGADO` | Delivered. Final (an in-store sale can still be voided) | Deducted | Yes |
| `CANCELADO` | Cancelled by the buyer or the admin (with a reason). Final | Returned | No (if it was paid, it stops counting) |
| `VENCIDO` | The deadline passed without a receipt. Final | Returned | No |

## How it communicates

```
Store (Origin: http://my-store.localhost:2999)
   │
   ▼
gateway :3000
   - public routes: GET /checkout, POST /orders, POST /orders/lookup,
     POST /orders/{number}/receipt, POST /orders/{number}/cancel
   - POST /orders: with a valid JWT adds X-User-Id; with an invalid JWT → 401
   - rate limit by IP: 5 orders/min, 10 lookups or cancellations/min, 5 receipts/min
     (everything else, the general limit)
   - adds X-Forwarded-Host (tenant schema)
   │
   ▼
kleinkauf-sales :3003
   TenantFilter      X-Forwarded-Host → schema
   HeaderAuthFilter  X-User-Id → roles from users (cache 1 min → Redis → users)
   │
   ├── POST /api/internal/products/reservations                ──► products :3002
   ├── POST /api/internal/products/reservations/{n}/release
   ├── POST /api/internal/products/reservations/{n}/sold
   └── POST /api/internal/products/reservations/{n}/unsold
         internal token (X-Internal-Token) + the tenant's X-Forwarded-Host
         RestTemplate with Eureka, 2s/3s timeouts, Resilience4j "products"

ExpirationJob (every hour) ── for each store: expires orders and returns their stock
```

### products internal endpoints

Only with the internal microservice token (`INTERNAL` role); they do not go through the gateway.

| Call | What it does |
|---|---|
| `POST /reservations` `{reference, items:[{type, id, quantity}], local}` | Checks that everything exists, has a price and has enough stock, and records a `SALIDA` (out) per product with the order reference. **All or nothing.** Returns name, price and cost of each item in the same order, plus `shortages` |
| `POST /reservations/{reference}/release` | Records an `ENTRADA` (in) for each `SALIDA` of that reference. Deleted products do not go back to stock |
| `POST /reservations/{reference}/sold` `{items}` | Adds the quantities to the sold counter (`sold_count`) of each product and bundle |
| `POST /reservations/{reference}/unsold` `{items}` | Subtracts them |

- **Idempotent by order number** (`stock_reservations` table in products):
  - Retrying a reservation does not deduct twice: it returns the data again, with the same `shortages`.
  - Releasing twice does not return stock twice.
  - Sold is added once and subtracted once.
- If the release arrives before the reservation (timeout in sales), it is marked as released and the reservation that
  arrives later is rejected with 409. Adding to sold for a released reservation is also 409.
- **`local: true`** (in-store sale):
  - Accepts items without a price (`unitPrice: null`).
  - If stock is short it records an `AJUSTE` for the difference before the `SALIDA`, so stock ends at 0, and lists the
    products in `shortages`.
  - Releasing the reservation returns everything that went out.
- Errors: invalid data → 400; missing item, disabled bundle, no price or no stock → **409** with the item's name (e.g.
  `Not enough stock: Mesa, Silla`). sales passes that message on.
- Bundles deduct their products (bundle quantity × units). A bundle's cost is the sum of its products' costs, or `null`
  if any of them has no cost. For sold, a bundle adds to the bundle, not to its products.
- Every reservation and release invalidates the catalog cache (availability changes).

### Creating an order, step by step

1. Validates the order and that the store has payment details.
2. Generates the number (retries if it already exists).
3. **Reserves** stock in products with that number.
   - 4xx (no stock, no price, not available): answers with that error. products deducted nothing.
   - Timeout, 5xx or open circuit: the reservation may have happened, so it asks to **release** and answers 503.
4. Compares prices with the cart's. If any changed: **releases** the stock and answers 409 with the new prices.
5. Saves the order with each item's name, list price, charged price and cost copied at the time of sale. If that
   fails: **releases** the stock and answers 503.

The in-store sale follows the same steps with `local: true`, then computes prices and discount (on failure, releases
the stock), **adds to sold** (on failure, releases the stock) and saves (on failure, subtracts from sold and
releases).

If one of these rollbacks also fails, an error is logged with the order number so it can be fixed by hand (stock
movements carry the number as reference).

### Resilience4j

`products` and `users` instances in `application.properties`: circuit breaker (50% failures in the last 10 calls →
open for 10 s) and retry (3 attempts every 200 ms). 4xx responses do not count as failures and are not retried.
Retrying is safe because every products operation is idempotent.

## Data

Tables per schema in the `sales` database:

| Table | What it stores |
|---|---|
| `orders` | Number, status, channel (`ONLINE` / `LOCAL`), payment method, buyer, `user_id`, delivery and address, notes, discount, total, `stock_review`, `expires_at`, `paid_at` (sale date in reports), `version` (optimistic locking), dates |
| `order_items` | Type (`PRODUCT` / `BUNDLE`), `item_id`, name, quantity, list price (null if it was "ask for price"), charged price, unit cost and subtotal at the time of sale |
| `order_status_history` | Status, date, user (null for a guest or the system) and reason |
| `payment_receipts` | File path (relative to `receipts.path`), type, size and date |
| `sales_settings` | Alias, CBU, holder, bank and expiration hours (one row) |

In products:

- `stock_reservations`: reference, status, whether it already added to sold (`sold`) and the products that were short
  (`shortages`).
- The `reference` column in `stock_movements`.
- `sold_count` on products and bundles: units sold ever. Shown in `GET /api/v1/products/{id}/pricing` (`PRECIOS`
  permission) and used to sort the catalog with `sort=best_sellers`, without showing the number.

## Known limitations

- **No events yet:** stock and sold counters use synchronous calls. If a rollback in products also fails (returning
  stock or subtracting sold), it is only logged with the order number. And if cancelling returns the stock while
  someone else changed the order at the same time, the stock comes back and the order stays active (validating it
  later fails). An outbox would solve this.
- Single time zone for the whole service.
