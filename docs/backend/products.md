# products

Spring Boot (servlet) on port **3002**, registered in Eureka as `products-microservice`. Postgres database
`products`, one schema per store (created with the store's first request).

Shared conventions (headers, errors, pagination, dates): [README](README.md). Permissions by area:
[permissions.md](../features/permissions.md). Reservations for sales: [sales.md](../features/sales.md#how-it-communicates).

## What it does

- **Products** with price, cost, minimum stock, category, description, metadata (material, sizes...), images, a slug
  for the public link, and a featured flag for the home page.
- **Stock** that only changes through movements (in, out, adjustment) with a reason and a user, plus a low stock
  alert.
- **Bundles** (`COMBOS`): several products with their own price (fixed or a discount on the sum); shows the list price
  and how many can be put together.
- **CSV import and export** of products.
- **Public catalog** (no login): paginated products with search, featured, detail by slug, categories and bundles. It
  shows availability, not exact stock. Cached in Redis.
- **Store settings:** name, color theme, icon and social links.
- **Store panel:** products and stock summary.
- **For sales:** reserves and returns order stock and counts units sold (idempotent internal endpoints).
- **For the platform:** products and bundles per store.

### Concepts and rules

| Concept | Rule |
|---|---|
| Stock | Integer. Only changes through [movements](#stock): `ENTRADA` adds, `SALIDA` subtracts, `AJUSTE` sets the counted value. Creating a product with initial stock records an `ENTRADA` "Stock inicial". A manual movement never leaves it negative (409) |
| Minimum stock | `minStock` ≥ 0. With `minStock > 0` and `stock <= minStock` the product is in **low stock**. 0 = no alert |
| Price and cost | Decimals ≥ 0 or `null`. Without a price the catalog shows "ask for price" and it cannot be bought online. Setting or seeing them requires `PRECIOS`; without that permission, creating a product ignores price and cost |
| Margin | `price - cost` and `% of the price`; `null` if either is missing |
| Sold | `soldCount`: units in paid orders (updated by sales). Sorts the catalog by "best sellers" without showing the number |
| Availability | What the public sees instead of stock: `AGOTADO` (stock ≤ 0), `POCAS_UNIDADES` (stock ≤ minimum, with minimum > 0), `DISPONIBLE` |
| Slug | Generated from the name on creation (`Mesa de roble` → `mesa-de-roble`, with a number if it repeats) and **does not change** if the name changes later |
| Featured | `featured` and an optional `order` (≥ 0). Listed by order, those without one last by name |
| Name | Unique in the store, also against deleted products |
| Deletion | Soft (`deleted_at`). The product's images are deleted from disk |
| Bundle | Fixed price or discount, enabled or not. **List price**: sum of price × quantity of its products (`null` if any has no price). **Available**: how many can be put together with current stock. Deleted for real (no soft delete) |
| Bundle in the catalog | Only if enabled, with a price, and all its products exist with quantity > 0 |

## Products

### GET /api/v1/products

**Access:** any panel area (`PRODUCTOS`, `PRECIOS`, `STOCK` or `COMBOS`)

The panel's paginated list, by name, with exact stock.

| Parameter | Default | What it does |
|---|---|---|
| `q` | — | Searches name, description and category |
| `category` | — | Exact category |
| `page` | 0 | |
| `size` | 20 | At most 100 |

```bash
curl 'http://localhost:3000/api/v1/products?q=mesa&page=0&size=20' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Response **200**:

```json
{
  "items": [
    { "id": 12, "name": "Mesa ratona", "slug": "mesa-ratona", "category": "Mesas",
      "description": "Mesa baja de roble", "stock": 4, "price": 85000.00, "featured": true,
      "images": [ "3f2a..._1730.png" ], "minStock": 2 }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

`images` are file names: the URL is `/products/images/<name>` (see [Images](#images)).

Error: 400 `The page must be 0 or greater and the size between 1 and 100`.

### GET /api/v1/products/{id}

**Access:** any panel area

One product with metadata and images (`FullProductDto`). It has neither cost nor minimum stock.

```json
{
  "id": 12, "name": "Mesa ratona", "slug": "mesa-ratona", "category": "Mesas",
  "description": "Mesa baja de roble", "stock": 4, "price": 85000.00, "featured": true,
  "metadata": { "material": "Roble", "color": "Natural", "style": "Nórdico",
                "height": 45.0, "width": 60.0, "length": 100.0, "weight": 12.5 },
  "images": [ { "id": 31, "path": "3f2a..._1730.png", "order": 0 } ]
}
```

`metadata` is `null` if it was never set. Error: 404 `Product not found`.

### POST /api/v1/products

**Access:** `PRODUCTOS` (price and cost are only saved with `PRECIOS`)

Creates a product. `stock` is the initial stock (recorded as an `ENTRADA` movement).

```bash
curl -X POST http://localhost:3000/api/v1/products \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Mesa ratona","category":"Mesas","description":"Mesa baja de roble","stock":4,"minStock":2,"price":85000,"cost":52000}'
```

Response **201**: `FullProductDto`.

| Status | When | Message |
|---|---|---|
| 400 | No name, or longer than 255 characters | `The product name is required (up to 255 characters)` |
| 409 | Name used by another product | `The Product name is already in use` |
| 409 | Name of a **deleted** product (names are unique against those too) | `The name belongs to a deleted product` |
| 400 | Negative price or cost | `Price and cost must be zero or greater` |
| 400 | Negative initial stock | `The quantity must be greater than zero (zero or greater for AJUSTE)` |
| 400 | Negative minimum stock | `The minimum stock must be zero or greater` |

### PUT /api/v1/products/{id}

**Access:** `PRODUCTOS`

Changes name, category, description and minimum stock. Fields not sent (or sent as `null`) **do not change**: send only
what is being edited. To clear the category or description, send `""`. Stock is not touched here.

```json
{ "name": "Mesa ratona", "category": "Mesas", "description": "Mesa baja de roble", "minStock": 2 }
```

Response **200**: `FullProductDto`.

| Status | When | Message |
|---|---|---|
| 404 | Does not exist | `Product not found` |
| 400 | Empty name or longer than 255 characters | `The product name is required (up to 255 characters)` |
| 409 | Another product's name | `The Product name is already in use` |
| 409 | A deleted product's name | `The name belongs to a deleted product` |
| 400 | Negative minimum stock | `The minimum stock must be zero or greater` |

### DELETE /api/v1/products/{id}

**Access:** `PRODUCTOS`

Soft-deletes the product and removes its images from disk. Response **200**: `true`. Error: 404 `Product not found`.

### GET /api/v1/products/{id}/pricing

**Access:** `PRECIOS`

Price, cost, margin and units sold. Recorded in the audit log as a sensitive read.

```json
{ "productId": 12, "price": 85000.00, "cost": 52000.00, "margin": 33000.00,
  "marginPercent": 38.82, "soldCount": 7 }
```

Error: 404 `Product not found`.

### PUT /api/v1/products/{id}/pricing

**Access:** `PRECIOS`

Changes price and cost (both; `null` clears them).

```json
{ "price": 90000, "cost": 52000 }
```

Response **200**: `PricingDto`. Errors: 400 `Price and cost must be zero or greater`, 404 `Product not found`.

### PUT /api/v1/products/{id}/featured

**Access:** `PRODUCTOS`

Marks or unmarks a featured product. `order` is optional and ignored when `featured` is `false`.

```json
{ "featured": true, "order": 1 }
```

Response **200**: `FullProductDto`. Errors: 400 `The featured order must be zero or greater`, 404 `Product not found`.

### GET /api/v1/products/featured

**Access:** any panel area (the public uses [`GET /api/v1/catalog/featured`](#get-apiv1catalogfeatured))

Featured products in order, with exact stock (`LightProductDto`). The panel uses it to reorder them: the list does not
carry the stored order.

### GET /api/v1/products/{id}/metadata

**Access:** Public

```json
{ "material": "Roble", "color": "Natural", "style": "Nórdico",
  "height": 45.0, "width": 60.0, "length": 100.0, "weight": 12.5 }
```

Without metadata it answers **200** with an empty body.

### PUT /api/v1/products/{id}/metadata

**Access:** `PRODUCTOS`

Creates or replaces the metadata (every field). Sizes are numbers (0 if not sent).

Response **200**: `FullProductDto`. Error: 404 `Product not found`.

## Images

### GET /api/v1/products/{id}/images

**Access:** Public

```json
[ { "id": 31, "path": "3f2a..._1730.png", "order": 0 } ]
```

### POST /api/v1/products/{id}/images

**Access:** `PRODUCTOS` · `multipart/form-data`

Uploads one or more images (`files` field), appended at the end. JPG, PNG, WEBP and GIF are accepted: the type comes
from the file's first bytes (not from its name or `Content-Type`) and it is saved with that extension, which decides
the `Content-Type` it is served with. SVG is not accepted (it can carry scripts). Everything is validated before saving:
if one fails, none is saved.

```bash
curl -X POST http://localhost:3000/api/v1/products/12/images \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -F 'files=@mesa1.jpg' -F 'files=@mesa2.jpg'
```

Response **200**: `true`.

| Status | When | Message |
|---|---|---|
| 404 | The product does not exist | `Product not found` |
| 413 | An image is larger than 1 MB | `Each image must be 1 MB or smaller` |
| 415 | A file is not JPG, PNG, WEBP or GIF | `Only JPG, PNG, WEBP or GIF images are allowed` |
| 413 | The request exceeds 10 MB or a file 5 MB | `The file exceeds the maximum upload size` |

### DELETE /api/v1/products/images/{imageId}

**Access:** `PRODUCTOS`

Deletes the image (from the database and disk) and reorders the remaining ones. Response **200**: `true`. Error: 404
`Image not found`.

### GET /products/images/{file}

**Access:** Public, **without a store** (browsers do not send `Origin` when loading an `<img>`)

Serves the file. Used directly in `<img src="http://localhost:3000/products/images/<path>">`. File names are random.
It does not count towards the rate limit. Store icons are served here too.

## Stock

### POST /api/v1/products/{id}/stock/movements

**Access:** `STOCK`

Records a movement.

| `type` | `quantity` | `reason` |
|---|---|---|
| `ENTRADA` (in) | Units coming in (> 0) | Optional |
| `SALIDA` (out) | Units going out (> 0) | Optional |
| `AJUSTE` (adjustment) | The **counted** stock (≥ 0); the difference is stored | **Required** |

```bash
curl -X POST http://localhost:3000/api/v1/products/12/stock/movements \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"type":"ENTRADA","quantity":5,"reason":"Compra a proveedor"}'
```

Response **201**:

```json
{ "id": 88, "productId": 12, "type": "ENTRADA", "quantity": 5, "stockAfter": 9,
  "reason": "Compra a proveedor", "userId": 1, "createdAt": "2026-11-03T10:15:00" }
```

`quantity` in the response is the applied difference (negative for `SALIDA` and for an `AJUSTE` that lowers stock).

| Status | When | Message |
|---|---|---|
| 400 | No `type` | `The movement type is required (ENTRADA, SALIDA or AJUSTE)` |
| 400 | Invalid quantity | `The quantity must be greater than zero (zero or greater for AJUSTE)` |
| 400 | `AJUSTE` without a reason | `A reason is required for a stock adjustment` |
| 409 | The movement leaves stock negative | `Not enough stock` |
| 404 | Does not exist | `Product not found` |

Publishes `stock.movement` and, if the product crosses into low stock, `stock.low` (see
[How it communicates](#how-it-communicates)).

### GET /api/v1/products/{id}/stock/movements

**Access:** `STOCK`

The product's history, newest first (`StockMovementDto`, as above). Movements from orders carry the order number in the
reason (`Pedido K7QM-4X2P`). Error: 404 `Product not found`.

### GET /api/v1/products/stock/low

**Access:** `STOCK`

Products in low stock, lowest stock first.

```json
[ { "id": 12, "name": "Mesa ratona", "stock": 1, "minStock": 2 } ]
```

## Store panel

### GET /api/v1/products/dashboard

**Access:** ADMINISTRADOR (the whole panel, not by area)

Products and stock summary, always up to date. Each field is explained in
[admin-dashboards.md](../features/admin-dashboards.md).

```json
{
  "products": {
    "total": 48,
    "byCategory": [ { "category": "Mesas", "count": 8 }, { "category": null, "count": 2 } ],
    "withoutPrice": 5, "withoutImages": 0, "featured": 6
  },
  "stock": {
    "low": 7, "outOfStock": 3,
    "inventoryAtCost": 1250000.00, "inventoryAtPrice": 2380000.00,
    "excludedWithoutCost": 4, "excludedWithoutPrice": 5,
    "recentMovements": [
      { "id": 88, "productId": 12, "productName": "Mesa ratona", "type": "ENTRADA", "quantity": 5,
        "stockAfter": 9, "reason": "Compra a proveedor", "reference": null, "userId": 1,
        "createdAt": "2026-11-03T10:15:00" }
    ]
  }
}
```

## Store settings

The store's name, color theme, icon and social links. One row per schema (`store_settings`, fixed id). Until something
is saved, the store uses the name derived from its identifier, the default theme and no icon.

`StoreDto`:

```json
{ "name": "Lucía Deco", "customName": "Lucía Deco", "theme": "OCEAN", "icon": "9b1c..._1730.png",
  "showIconInHeader": true, "showIconAsFavicon": true,
  "socials": { "instagram": "https://instagram.com/luciadeco", "x": null, "facebook": null, "tiktok": null } }
```

- `name` is never `null`: without a custom name it is the one derived from the identifier; `customName` is what the
  admin set (`null` if nothing).
- `theme`: `OCEAN`, `FOREST`, `TERRACOTTA`, `GRAPE`, `ROSE`, `GRAPHITE` or `NIGHT` (the dark one). Stored as text, so
  adding a theme does not need a migration.
- `icon`: a file under `/products/images/`, or `null`.
- `socials`: never `null`; each network is its profile link (always https) or `null`.

### GET /api/v1/catalog/store

**Access:** Public

The settings the storefront needs (`StoreDto`). The frontend applies the theme and the icon, and shows the social
icons in the footer.

### GET /api/v1/store/settings

**Access:** ADMINISTRADOR

Same `StoreDto`, for the settings page.

### PUT /api/v1/store/settings

**Access:** ADMINISTRADOR

```json
{ "name": "Lucía Deco", "theme": "FOREST", "showIconInHeader": true, "showIconAsFavicon": false,
  "socials": { "instagram": "luciadeco", "tiktok": "" } }
```

Fields left out or `null` do not change. For each social network: a profile link or just the username (products builds
the link); only links of that network are accepted; `null` keeps the current one and `""` removes it. Response **200**:
`StoreDto`. Invalid values answer 400.

### POST /api/v1/store/settings/icon · DELETE /api/v1/store/settings/icon

**Access:** ADMINISTRADOR

Uploads (multipart `file`) or removes the icon. Same rules as product images: up to 1 MB, JPG, PNG, WEBP or GIF by
content. The previous file is deleted from disk. Response **200**: `StoreDto`.

## Import and export

**Access:** `PRODUCTOS` **and** `PRECIOS` (the file carries costs).

### CSV format

- `;` separator, UTF-8 with BOM (Spanish-locale Excel opens it with a double click).
- Numbers in Argentine format: `.` separates thousands and `,` is the decimal mark (`15.000,50`). Dots must separate
  groups of 3 (`15.5` is an error).
- Columns (lower-case headers, Spanish names, any order; only `nombre` is required):

| Column | What it is | Rules |
|---|---|---|
| `nombre` (name) | Identifies the product | Required, up to 255 characters, cannot repeat in the file |
| `categoria`, `descripcion` | Text | Up to 255 characters |
| `precio`, `costo` (price, cost) | Decimals | ≥ 0, up to 10 integer digits and 2 decimals |
| `material`, `color`, `estilo` (style) | Metadata | Up to 255 characters |
| `alto`, `ancho`, `largo`, `peso` (height, width, length, weight) | Metadata | Numbers ≥ 0 |

- **No stock column**: stock is handled through movements.

### GET /api/v1/products/export

Every product, by name, as `productos.csv` (`text/csv`). Recorded in the audit log as a sensitive read.

### GET /api/v1/products/import/template

Empty template with the headers, as `plantilla-productos.csv`.

### POST /api/v1/products/import

`multipart/form-data` with the `file` field (up to 5 MB and 5,000 products).

- If the name exists, it **updates**: an empty cell does not change the value.
- If it does not exist, it **creates** the product with stock 0.
- **All or nothing**: with a single error nothing is imported.

```bash
curl -X POST http://localhost:3000/api/v1/products/import \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -F 'file=@productos.csv'
```

Response **200**: `{ "created": 12, "updated": 30, "errors": [] }`

Response **400** (JSON, nothing imported):

```json
{ "created": 0, "updated": 0,
  "errors": [ { "row": 4, "message": "'precio' is not a valid number: '15.5'" } ] }
```

`row` is the file line (1 = headers; 0 = the whole file). Possible messages:

| Message | Row |
|---|---|
| `The file is empty` / `The file exceeds 5 MB` / `The file is not a valid CSV: …` / `The file has no header row` / `The file has more than 5000 products` | 0 |
| `Unknown column: '…'` / `Duplicated column: '…'` / `The 'nombre' column is required` | 1 |
| `The name is required` / `The product '…' is repeated in the file` / `The name '…' belongs to a deleted product` | The product's |
| `'…' has more than 255 characters` / `'…' must be zero or greater` / `'…' must have up to …` / `'…' is not a valid number: '…'` | The product's |

A single `IMPORT` entry with the summary is recorded in the audit log.

## Bundles

Changes require `COMBOS` (including the bundle price). Reads, any panel area.

`BundleDto`:

```json
{
  "id": 3, "name": "Comedor completo", "enabled": true,
  "price": 450000.00, "pricingType": "FIXED", "discount": null, "listPrice": 520000.00, "available": 2,
  "products": [ { "productid": 12, "productName": "Mesa", "quantity": 1, "price": 400000.00, "image": "/uploads/…" },
                { "productid": 15, "productName": "Silla", "quantity": 4, "price": 30000.00, "image": null } ]
}
```

**Bundle price.** `pricingType` says how `price` (the final price shown and charged) is computed:

| `pricingType` | `price` | `discount` |
|---|---|---|
| `FIXED` (default) | The stored one (`null` = no price, not in the catalog) | `null` |
| `PERCENT` | Sum of the products (`listPrice`) minus the percentage | Percentage, 0 to 100 |
| `AMOUNT` | `listPrice` minus the amount, never below 0 | Amount, 0 or more |

With a discount the price is **not stored**: it is computed each time (`BundleFactory.price`, rounded to 2 decimals),
so it follows the products' prices without updating the bundle. If any product has no price, the bundle has no price.
The panel, the catalog and the stock reservation (the price sales charges) use the same computation; an order keeps
the price it charged, so a later change does not affect it.

(`productid` is lower case. `price` is the product's, `null` if it has none; `image` is its first image, `null` if it has
none.)

### GET /api/v1/bundles/all

**Access:** any panel area

Every bundle, including disabled ones. Response **200**: list of `BundleDto`.

### GET /api/v1/bundles/{id}

**Access:** any panel area

Response **200**: `BundleDto`. Error: 404 `Bundle not found`.

### POST /api/v1/bundles

**Access:** `COMBOS`

Creates the whole bundle: name, products and price. Everything is validated before saving, so if anything fails nothing
is created.

```json
{ "name": "Comedor completo", "enabled": true, "price": 450000,
  "products": [ { "productId": 12, "quantity": 1 }, { "productId": 15, "quantity": 4 } ] }
```

`price` can be `null` (the bundle does not appear in the catalog until it has a price). Without `enabled` it is created
enabled. With a discount, instead of `price` send `"pricingType": "PERCENT"` (or `"AMOUNT"`) and `"discount": 15`;
without `pricingType` it is a fixed price.

Response **201**: `BundleDto` with its products. Errors:

- 400 `The bundle name is required (up to 255 characters)`
- 400 `The bundle needs at least one product`
- 400 `Each product needs an id and a quantity greater than zero`
- 400 `A product can only appear once in the bundle`
- 400 `Price and cost must be zero or greater`
- 400 `The discount is required: a percentage between 0 and 100 or an amount of zero or greater`
- 404 `Product not found` (one of the products does not exist)

### PUT /api/v1/bundles/{id}/enabled

**Access:** `COMBOS`

Enables or disables the bundle (a disabled one is not in the catalog).

```json
{ "enabled": false }
```

Response **200**: `BundleDto`. Errors: 400 `The enabled field is required (true or false)`, 404 `Bundle not found`.

### PUT /api/v1/bundles/{id}/price

**Access:** `COMBOS`

Fixed price or discount (see [bundle price](#bundles)). Without `pricingType`, a fixed price:

```json
{ "price": 430000 }
{ "pricingType": "PERCENT", "discount": 15 }
{ "pricingType": "AMOUNT", "discount": 50000 }
```

Switching to a discount clears the stored price, and switching back to fixed clears the discount. Response **200**:
`BundleDto`. Errors: 400 `Price and cost must be zero or greater`, 400 `The discount is required: …`, 404
`Bundle not found`.

### PUT /api/v1/bundles/{id}/product

**Access:** `COMBOS`

Adds a product to the bundle or changes its quantity.

```json
{ "productId": 15, "quantity": 4 }
```

Response **200**: `BundleDto`. Errors: 400 `Each product needs an id and a quantity greater than zero`, 404
`Bundle not found`, 404 `Product not found`.

### PUT /api/v1/bundles/{id}/product/bulk

**Access:** `COMBOS`

The same with a list: `[ { "productId": 12, "quantity": 1 }, { "productId": 15, "quantity": 4 } ]`. Response **200**:
`BundleDto` after the last one. Same errors; if one fails, none is applied.

### DELETE /api/v1/bundles/{id}/product

**Access:** `COMBOS`

Removes a product: body `{ "productId": 15 }`. Response **200**: `true`.

### DELETE /api/v1/bundles/{id}

**Access:** `COMBOS`

Deletes the bundle (for real). Response **200**: `true`. Error: 404 `Bundle not found`.

## Public catalog

No login, from the store's frontend. It never shows exact stock or cost: `availability` is `DISPONIBLE`,
`POCAS_UNIDADES` or `AGOTADO`. `price: null` = "ask for price". Each product and bundle carries its `id`, which the cart
uses to build the order. Everything is cached in Redis per store.

`CatalogProductDto`:

```json
{ "id": 12, "slug": "mesa-ratona", "name": "Mesa ratona", "category": "Mesas",
  "description": "Mesa baja de roble", "price": 85000.00, "availability": "POCAS_UNIDADES",
  "featured": true, "images": [ "3f2a..._1730.png" ] }
```

### GET /api/v1/catalog/products

**Access:** Public

| Parameter | Default | What it does |
|---|---|---|
| `q` | — | Searches name, description and category |
| `category` | — | Exact category |
| `sort` | `name` | `name`, `price_asc`, `price_desc` (no price last) or `best_sellers` |
| `page` | 0 | |
| `size` | 24 | At most 100 |

```bash
curl 'http://localhost:3000/api/v1/catalog/products?q=mesa&sort=price_asc' -H 'Origin: http://demo.localhost:2999'
```

Response **200**: `{ "items": [CatalogProductDto], "page", "size", "totalItems", "totalPages" }`.

| Status | When | Message |
|---|---|---|
| 400 | Invalid `sort` | `The sort must be name, price_asc, price_desc or best_sellers` |
| 400 | `page` or `size` out of range | `The page must be 0 or greater and the size between 1 and 100` |

The first page without a search counts as a catalog visit (usage panel, see
[observability.md](observability.md#websocket)).

### GET /api/v1/catalog/featured

**Access:** Public

Featured products for the home page, in the order chosen by the admin. Response **200**: list of `CatalogProductDto`.

### GET /api/v1/catalog/products/{slug}

**Access:** Public

Product page: `CatalogProductDto` with `images` in order and `metadata` (or `null`). Counts a product view for the usage
panel.

Error: 404 `Product not found`.

### GET /api/v1/catalog/categories

**Access:** Public

```json
[ { "name": "Mesas", "products": 8 }, { "name": "Sillas", "products": 12 } ]
```

### GET /api/v1/catalog/bundles

**Access:** Public

Bundles that can be bought (see [rules](#concepts-and-rules)), by name.

```json
[ { "id": 3, "name": "Comedor completo", "price": 450000.00, "listPrice": 520000.00,
    "availability": "DISPONIBLE", "image": "3f2a..._1730.png",
    "products": [ { "slug": "mesa", "name": "Mesa", "quantity": 1, "category": "Comedor",
                    "description": "Mesa de roble", "price": 400000.00, "image": "3f2a..._1730.png" } ] } ]
```

`listPrice` is there to show the savings; `image` is the first image of the first product that has one. A bundle is only
`DISPONIBLE` or `AGOTADO`. Each product carries what the bundle detail needs: `price` is the product's own price
(`null` = ask) and `image` its first image (`null` if none).

## Share link

### GET /share/{store}/products/{slug}

**Access:** public (no login). The link preview the frontend shares on WhatsApp or Instagram.

The frontend shares the product page in the store (`https://<store>.<domain>/productos/{slug}`). Preview crawlers do
not run JavaScript, so on the server Caddy recognizes their `User-Agent` and passes that request to this route; people
get the SPA.

Crawlers do not send `Origin` either: that is why the store goes in the path. The gateway resolves it from the
identifier (building the store URL with the scheme, host and port of `platform.url`) and rewrites the path to
`/share/products/{slug}` in products.

Response **200** `text/html` with the `og:title`, `og:description` (price and description, up to 200 characters),
`og:image` (the first image, with an absolute URL) and `og:url` tags, and a redirect (`<meta http-equiv="refresh">`) to
`<store URL>/productos/{slug}`, the product page in the frontend. `Cache-Control: public, max-age=3600`. Texts are
escaped. It uses the catalog detail, with its cache.

- Missing or deleted product: **404** with the same HTML, without `og:title`, redirecting to the store's home.
- Missing (404) or disabled (403) store: answered by the gateway, as text.
- Configuration: `share.store-url` (default `http://{identifier}.localhost:2999`) and `share.public-url` (the gateway as
  seen from outside, for the image; default `http://localhost:3000`). In production they come from the `.env`
  (`SHARE_STORE_URL` and `SHARE_PUBLIC_URL` in `docker-compose.prod.yml`).

```bash
curl http://localhost:3000/share/demo/products/mesa-de-roble
```

### GET /share/{store}/sitemap.xml

**Access:** public (no login). The store's sitemap for search engines: on the server Caddy serves it at
`https://<store>.<domain>/sitemap.xml`, which is where its `robots.txt` points (see
[deployment.md](../deployment.md#search-engines-robotstxt-and-sitemapxml)). The store is resolved from the path, like the
share link, and reaches products as `/share/sitemap.xml`.

Response **200** `application/xml` with the store's absolute URLs: home, `/catalogo`, `/combos` (if there are bundles
for sale), `/productos/{slug}` for each non-deleted product and the legal pages. Nothing from the panel, cart or account.
`Cache-Control: public, max-age=3600`. Missing (404) or disabled (403) store: the gateway.

```bash
curl http://localhost:3000/share/demo/sitemap.xml
```

## Platform

### GET /api/v1/platform/products/usage?sort=

**Access:** SUPERADMIN, without a store (with a store: 404 `This endpoint is only available on the platform.`)

Products (not deleted) and bundles of each store that has already used products, highest first. Kept 5 minutes in
Redis. `sort`: `products` (default) or `bundles`.

```json
[ { "schema": "demo", "products": 48, "bundles": 3 } ]
```

Stores that never used products do not appear (the frontend counts them as 0) and a deleted one may appear: the frontend
keeps the ones listed by users (`/api/v1/platform/admin/usage`), joining by `schema`.

Error: 400 `Invalid sort: use products or bundles.`

## Internal

Not called by the frontend: sales uses them, directly through Eureka, with the internal token and the order store's
`X-Forwarded-Host`. The rules are detailed in [sales.md](../features/sales.md#products-internal-endpoints).

| Method | Path | What it does | Response |
|---|---|---|---|
| POST | `/api/internal/products/reservations` | Reserves an order's stock (all or nothing) | `ReservationDto` |
| POST | `/api/internal/products/reservations/{reference}/release` | Returns the stock | 204 |
| POST | `/api/internal/products/reservations/{reference}/sold` | Adds to sold | 204 |
| POST | `/api/internal/products/reservations/{reference}/unsold` | Subtracts from sold | 204 |

Reservation:

```json
{ "reference": "K7QM-4X2P", "local": false,
  "items": [ { "type": "PRODUCT", "id": 12, "quantity": 2 }, { "type": "BUNDLE", "id": 3, "quantity": 1 } ] }
```

```json
{ "reference": "K7QM-4X2P",
  "items": [ { "type": "PRODUCT", "id": 12, "name": "Mesa ratona", "unitPrice": 85000.00, "unitCost": 52000.00 },
             { "type": "BUNDLE", "id": 3, "name": "Comedor completo", "unitPrice": 450000.00, "unitCost": null } ],
  "shortages": [] }
```

`sold` and `unsold` take `{ "items": [ { "type", "id", "quantity" } ] }`.

| Status | When | Message |
|---|---|---|
| 400 | No reference (up to 40) or invalid items | `The reservation needs a reference (up to 40 characters) and items with type, id and a quantity greater than zero` |
| 409 | Missing product or bundle, disabled bundle or bundle without products | `Item not available` (with `: <name>` for a bundle) |
| 409 | No price (online order) | `Item without price: <name>` |
| 409 | No stock (online order) | `Not enough stock: <names>` |
| 409 | The reservation was already released | `The reservation was already released` |
| 409 | The same reference is being processed | `The reservation is being processed by another request` |
| 404 | `sold` / `unsold` for a reservation that does not exist | `Reservation not found` |

Without the internal token: 403 `Token invalido` (JSON with `status`, `error`, `message`).

## How it communicates

- **Receives** from the gateway `X-Forwarded-Host` (the store, required except on `/products/images/**`, `/share/**` and
  `/api/v1/platform/**`: without it, 404) and `X-User-Id`.
- **Asks users for roles** (`GET /api/internal/users/roles/{id}`): first the local cache (1 minute), then Redis
  (`user:<id>:<schema>`, written by users) and finally users, with circuit breaker, retries and timeout. A permission
  change takes up to 1 minute to apply.
- **Redis:**

| Key | Contents | TTL |
|---|---|---|
| `catalog:<schema>:v<version>:<query>` | Catalog responses | 1 hour. Every change to products, bundles, stock, sold or store settings bumps `catalog:<schema>:version` and makes everything stale at once (after commit) |
| `platform:usage:products` | Usage per store for the platform panel | 5 minutes |
| `usage:<schema>:<yyyy-MM-dd>` (hash) | Catalog visits (`visits`) and views per product (`product:<slug>`), by Buenos Aires day | 8 days |

  If Redis fails, the catalog is read from the database and the counters are lost; nothing breaks.
- **Events** (`kleinkauf:events`, for the live `store.activity` panel), after commit:
  - `stock.movement`: a movement entered by hand (`productId`, `productName`, `movementType`, `quantity`, `stockAfter`,
    `reason`, `userId`).
  - `stock.low`: a product crosses into low stock with any movement, including orders' (`productId`, `productName`,
    `stock`, `minStock`).
- **Audit log:** `Product`, `Bundle`, `BundleProduct`, `ProductImage`, `ProductMetadata`, `StockMovement` and
  `StoreSettings` are `@Audited`. By hand: the CSV import (`IMPORT`) and the cost and export reads (`READ`). See
  [audit.md](../features/audit.md).
- It does not call sales: sales calls it.

## Data model

`products` database, one schema per store. Tables created by Hibernate (`ddl-auto=update`).

| Table | Main columns | Notes |
|---|---|---|
| `product` | `id`, `name` (unique), `slug` (unique), `category`, `description`, `stock`, `min_stock`, `price`, `cost` (`numeric(12,2)`), `featured`, `featured_order`, `sold_count`, `deleted_at` | Soft delete |
| `productmetadata` | `id`, `product_id`, `material`, `color`, `style`, `height`, `width`, `length`, `weight` | One per product |
| `productimages` | `id`, `product_id`, `path`, `image_order` | `path` is the file name |
| `stock_movements` | `id`, `product_id`, `type`, `quantity` (difference), `stock_after`, `reason`, `reference` (order number), `user_id`, `created_at` | Never modified or deleted |
| `bundles` | `id`, `name`, `enabled`, `price`, `pricing_type`, `discount`, `sold_count` | Hard delete |
| `bundle_products` | `id`, `bundle_id`, `product_id`, `quantity` | |
| `stock_reservations` | `id`, `reference` (unique), `status` (`RESERVED`/`RELEASED`), `sold`, `shortages`, `created_at`, `released_at` | Reservation idempotency |
| `store_settings` | `id` (fixed), `name`, `theme` (text), `icon`, `show_icon_in_header`, `show_icon_as_favicon`, `instagram_url`, `x_url`, `facebook_url`, `tiktok_url`, `updated_at` | One row per schema |

Images live on disk at `image.path` (`uploads/products/images`), outside any store.

## Configuration

| Property | Development value | Purpose |
|---|---|---|
| `server.port` / `management.server.port` | `3002` / `9002` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/products` | Database |
| `spring.data.redis.*` | `localhost:6379` | Cache, counters and events |
| `users.url` | `http://users-microservice/` | Roles (through Eureka) |
| `api.internal.key` | — | Internal token. **Change it outside development** |
| `image.path` | `uploads/products/images` | Image folder |
| `kleinkauf.tenant.public-paths` | `/products/images/,/api/v1/platform/` | Paths without a store |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `5MB` / `10MB` | Uploads (images are also capped at 1 MB each) |
| `http.client.connect-timeout` / `read-timeout` | `2s` / `3s` | Calls to users |
| `resilience4j.*.instances.users.*` | | Circuit breaker and retries towards users |

Running it alone: `cd backend/kleinkauf-product && ./gradlew bootRun`. SQL and security logs are at DEBUG in
development.

## Known limitations

- Images uploaded before type validation existed were all saved as `.png`, even JPGs: browsers show them anyway.
- Uploading images answers 200 with `true` instead of 201 with the images: the frontend fetches the list again.
