# products

Spring Boot (servlet) en el puerto **3002**, registrado en Eureka como `products-microservice`. Base Postgres
`products`, un schema por tienda (se crea con la primera request de la tienda).

Convenciones comunes (headers, errores, paginación, fechas): [README](README.md). Permisos por área:
[permisos.md](../permisos.md). Reservas para ventas: [ventas.md](../ventas.md#cómo-se-comunica).

## Qué hace

- **Productos** con precio, costo, stock mínimo, categoría, descripción, metadatos (material, medidas...), imágenes,
  slug para el link público y destacados para la home.
- **Stock** que solo cambia con movimientos (entrada, salida, ajuste) con motivo y usuario, y alerta de stock bajo.
- **Combos** (bundles): varios productos con precio propio; muestra el precio de lista y cuántos se pueden armar.
- **Importar y exportar** productos en CSV.
- **Catálogo público** (sin login): productos paginados con búsqueda, destacados, detalle por slug, categorías y
  combos. Muestra disponibilidad, no el stock exacto. Cacheado en Redis.
- **Panel de la tienda:** resumen de productos y stock.
- **Para sales:** reserva y devuelve stock de los pedidos y cuenta los vendidos (endpoints internos idempotentes).
- **Para la plataforma:** productos y combos por tienda.

### Conceptos y reglas

| Concepto | Regla |
|---|---|
| Stock | Entero. Solo cambia con [movimientos](#stock): `ENTRADA` suma, `SALIDA` resta, `AJUSTE` fija el valor contado. El alta con stock inicial registra una `ENTRADA` "Stock inicial". Nunca queda negativo por un movimiento manual (409) |
| Stock mínimo | `minStock` ≥ 0. Con `minStock > 0` y `stock <= minStock` el producto está en **stock bajo**. 0 = sin alerta |
| Precio y costo | Decimales ≥ 0 o `null`. Sin precio el catálogo muestra "consultar precio" y no se puede comprar online. Para cargarlos o verlos hace falta `PRECIOS`; sin ese permiso, el alta ignora precio y costo |
| Margen | `price - cost` y `% sobre el precio`; `null` si falta alguno |
| Vendidos | `soldCount`: unidades en pedidos pagos (lo actualiza sales). Ordena el catálogo por "más vendidos" sin mostrar el número |
| Disponibilidad | Lo que ve el público en vez del stock: `AGOTADO` (stock ≤ 0), `POCAS_UNIDADES` (stock ≤ mínimo, con mínimo > 0), `DISPONIBLE` |
| Slug | Se genera del nombre al crear (`Mesa de roble` → `mesa-de-roble`, con número si se repite) y **no cambia** si después cambia el nombre |
| Destacados | `featured` y un `order` opcional (≥ 0). Se listan por orden, los sin orden al final por nombre |
| Nombre | Único en la tienda, también contra los productos borrados |
| Borrado | Lógico (`deleted_at`). Las imágenes del producto se borran del disco |
| Combo | Precio propio (≥ 0 o `null`), habilitado o no. **Precio de lista**: suma de precio × cantidad de sus productos (`null` si alguno no tiene precio). **Disponibles**: cuántos se pueden armar con el stock actual. Se borran de verdad (sin borrado lógico) |
| Combo en el catálogo | Solo si está habilitado, tiene precio y todos sus productos existen con cantidad > 0 |

## Productos

### GET /api/v1/products

**Acceso:** cualquier área del panel (`PRODUCTOS`, `PRECIOS`, `STOCK` o `COMBOS`)

Listado paginado del panel, por nombre, con el stock exacto.

| Parámetro | Default | Qué hace |
|---|---|---|
| `q` | — | Busca en nombre, descripción y categoría |
| `category` | — | Filtra por categoría exacta |
| `page` | 0 | |
| `size` | 20 | Máximo 100 |

```bash
curl 'http://localhost:3000/api/v1/products?q=mesa&page=0&size=20' \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>'
```

Respuesta **200**:

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

`images` son nombres de archivo: la URL es `/products/images/<nombre>` (ver [Imágenes](#imágenes)).

Error: 400 `The page must be 0 or greater and the size between 1 and 100`.

### GET /api/v1/products/{id}

**Acceso:** cualquier área del panel

Un producto con metadatos e imágenes (`FullProductDto`). No trae costo ni stock mínimo.

```json
{
  "id": 12, "name": "Mesa ratona", "slug": "mesa-ratona", "category": "Mesas",
  "description": "Mesa baja de roble", "stock": 4, "price": 85000.00, "featured": true,
  "metadata": { "material": "Roble", "color": "Natural", "style": "Nórdico",
                "height": 45.0, "width": 60.0, "length": 100.0, "weight": 12.5 },
  "images": [ { "id": 31, "path": "3f2a..._1730.png", "order": 0 } ]
}
```

`metadata` es `null` si nunca se cargó. Error: 404 `Product not found`.

### POST /api/v1/products

**Acceso:** `PRODUCTOS` (precio y costo se guardan solo con `PRECIOS`)

Crea un producto. `stock` es el stock inicial (queda como movimiento `ENTRADA`).

```bash
curl -X POST http://localhost:3000/api/v1/products \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Mesa ratona","category":"Mesas","description":"Mesa baja de roble","stock":4,"minStock":2,"price":85000,"cost":52000}'
```

Respuesta **200** (no 201): `FullProductDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 409 | Nombre usado por otro producto activo | `The Product name is already in use` |
| 400 | Precio o costo negativos | `Price and cost must be zero or greater` |
| 400 | Stock inicial negativo | `The quantity must be greater than zero (zero or greater for AJUSTE)` |
| 400 | Stock mínimo negativo | `The minimum stock must be zero or greater` |
| 500 | Nombre de un producto **borrado** (choca con el único de la base) | Mensaje de la excepción |

### PUT /api/v1/products/{id}

**Acceso:** `PRODUCTOS`

Cambia nombre, categoría, descripción y stock mínimo. **Reemplaza los cuatro**: los que no se manden quedan vacíos
(`minStock` en 0). El stock no se toca acá.

```json
{ "name": "Mesa ratona", "category": "Mesas", "description": "Mesa baja de roble", "minStock": 2 }
```

Respuesta **200**: `FullProductDto`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 404 | No existe | `Product not found` |
| 400 | Stock mínimo negativo | `The minimum stock must be zero or greater` |
| 500 | Nombre repetido (no se valida antes) | Mensaje de la excepción |

### DELETE /api/v1/products/{id}

**Acceso:** `PRODUCTOS`

Borra (lógico) el producto y sus imágenes del disco. Respuesta **200**: `true`. Error: 404 `Product not found`.

### GET /api/v1/products/{id}/pricing

**Acceso:** `PRECIOS`

Precio, costo, margen y vendidos. Se registra en la auditoría como lectura sensible.

```json
{ "productId": 12, "price": 85000.00, "cost": 52000.00, "margin": 33000.00,
  "marginPercent": 38.82, "soldCount": 7 }
```

Error: 404 `Product not found`.

### PUT /api/v1/products/{id}/pricing

**Acceso:** `PRECIOS`

Cambia precio y costo (los dos; `null` los borra).

```json
{ "price": 90000, "cost": 52000 }
```

Respuesta **200**: `PricingDto`. Errores: 400 `Price and cost must be zero or greater`, 404 `Product not found`.

### PUT /api/v1/products/{id}/featured

**Acceso:** `PRODUCTOS`

Marca o desmarca un destacado. `order` es opcional y se ignora si `featured` es `false`.

```json
{ "featured": true, "order": 1 }
```

Respuesta **200**: `FullProductDto`. Errores: 400 `The featured order must be zero or greater`, 404
`Product not found`.

### GET /api/v1/products/featured

**Acceso:** cualquier área del panel (el público usa [`GET /api/v1/catalog/featured`](#get-apiv1catalogfeatured))

Destacados en orden, con stock exacto (`LightProductDto`). Lo usa el panel para ordenarlos: el listado no trae el
orden guardado.

### GET /api/v1/products/{id}/metadata

**Acceso:** Público

```json
{ "material": "Roble", "color": "Natural", "style": "Nórdico",
  "height": 45.0, "width": 60.0, "length": 100.0, "weight": 12.5 }
```

Sin metadatos responde **200** con el cuerpo vacío.

### PUT /api/v1/products/{id}/metadata

**Acceso:** `PRODUCTOS`

Crea o reemplaza los metadatos (todos los campos). Las medidas son números (0 si no se mandan).

Respuesta **200**: `FullProductDto`. Error: 404 `Product not found`.

## Imágenes

### GET /api/v1/products/{id}/images

**Acceso:** Público

```json
[ { "id": 31, "path": "3f2a..._1730.png", "order": 0 } ]
```

### POST /api/v1/products/{id}/images

**Acceso:** `PRODUCTOS` · `multipart/form-data`

Sube una o más imágenes (campo `files`), que se agregan al final.

```bash
curl -X POST http://localhost:3000/api/v1/products/12/images \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -F 'files=@mesa1.jpg' -F 'files=@mesa2.jpg'
```

Respuesta **200**: `true`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 413 | Alguna imagen pesa más de 1 MB | `Each image must be 1 MB or smaller` |
| 413 | El request supera 10 MB o un archivo 5 MB | `The file exceeds the maximum upload size` |

### DELETE /api/v1/products/images/{imageId}

**Acceso:** `PRODUCTOS`

Borra la imagen (de la base y del disco) y reordena las que quedan. Respuesta **200**: `true`. Error: 404
`Image not found`.

### GET /products/images/{archivo}

**Acceso:** Público, **sin tienda** (el navegador no manda `Origin` al cargar un `<img>`)

Sirve el archivo. Se usa directo en un `<img src="http://localhost:3000/products/images/<path>">`. Los nombres
son aleatorios. No cuenta para el rate limit.

## Stock

### POST /api/v1/products/{id}/stock/movements

**Acceso:** `STOCK`

Registra un movimiento.

| `type` | `quantity` | `reason` |
|---|---|---|
| `ENTRADA` | Unidades que entran (> 0) | Opcional |
| `SALIDA` | Unidades que salen (> 0) | Opcional |
| `AJUSTE` | El stock **contado** (≥ 0); se guarda la diferencia | **Obligatorio** |

```bash
curl -X POST http://localhost:3000/api/v1/products/12/stock/movements \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"type":"ENTRADA","quantity":5,"reason":"Compra a proveedor"}'
```

Respuesta **201**:

```json
{ "id": 88, "productId": 12, "type": "ENTRADA", "quantity": 5, "stockAfter": 9,
  "reason": "Compra a proveedor", "userId": 1, "createdAt": "2026-11-03T10:15:00" }
```

`quantity` en la respuesta es la diferencia aplicada (negativa en `SALIDA` y en un `AJUSTE` que baja).

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Sin `type` | `The movement type is required (ENTRADA, SALIDA or AJUSTE)` |
| 400 | Cantidad inválida | `The quantity must be greater than zero (zero or greater for AJUSTE)` |
| 400 | `AJUSTE` sin motivo | `A reason is required for a stock adjustment` |
| 409 | La salida deja el stock negativo | `Not enough stock` |
| 404 | No existe | `Product not found` |

Publica `stock.movement` y, si el producto cruza a stock bajo, `stock.low` (ver [Cómo se comunica](#cómo-se-comunica)).

### GET /api/v1/products/{id}/stock/movements

**Acceso:** `STOCK`

Historial del producto, del más nuevo al más viejo (`StockMovementDto`, como arriba). Los movimientos de los pedidos
tienen el número de pedido en el motivo (`Pedido K7QM-4X2P`). Error: 404 `Product not found`.

### GET /api/v1/products/stock/low

**Acceso:** `STOCK`

Productos en stock bajo, del menor stock al mayor.

```json
[ { "id": 12, "name": "Mesa ratona", "stock": 1, "minStock": 2 } ]
```

## Panel de la tienda

### GET /api/v1/products/dashboard

**Acceso:** ADMINISTRADOR (todo el panel, no por áreas)

Resumen de productos y stock, siempre al día. Detalle de cada dato en [panel-admin.md](../panel-admin.md).

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

- `byCategory`: de mayor a menor; los sin categoría van con `null`, al final si empatan.
- `inventoryAtCost` / `AtPrice`: Σ stock × costo / precio de los que tienen stock > 0; los que no tienen costo /
  precio se cuentan en `excluded*`.
- `recentMovements`: los últimos 10, con el nombre aunque el producto esté borrado. `reference` es el número de
  pedido en los movimientos de ventas.

## Importar y exportar

**Acceso:** `PRODUCTOS` **y** `PRECIOS` (el archivo trae costos).

### Formato del CSV

- Separador `;`, UTF-8 con BOM (Excel en castellano lo abre con doble clic).
- Números en formato argentino: `.` separa miles y `,` es el decimal (`15.000,50`). Si hay puntos tienen que
  separar grupos de 3 (`15.5` es un error).
- Columnas (encabezados en minúscula, en cualquier orden; solo `nombre` es obligatoria):

| Columna | Qué es | Reglas |
|---|---|---|
| `nombre` | Identifica al producto | Obligatoria, hasta 255 caracteres, no se puede repetir en el archivo |
| `categoria`, `descripcion` | Texto | Hasta 255 caracteres |
| `precio`, `costo` | Decimales | ≥ 0, hasta 10 enteros y 2 decimales |
| `material`, `color`, `estilo` | Metadatos | Hasta 255 caracteres |
| `alto`, `ancho`, `largo`, `peso` | Metadatos | Números ≥ 0 |

- **No trae stock**: se maneja con movimientos.

### GET /api/v1/products/export

Todos los productos, por nombre, como `productos.csv` (`text/csv`). Se registra en la auditoría como lectura
sensible.

### GET /api/v1/products/import/template

Plantilla vacía con los encabezados, como `plantilla-productos.csv`.

### POST /api/v1/products/import

`multipart/form-data` con el campo `file` (hasta 5 MB y 5.000 productos).

- Si el nombre existe, **actualiza**: una celda vacía no cambia el dato.
- Si no existe, **crea** el producto con stock 0.
- **Todo o nada**: con un error no se importa nada.

```bash
curl -X POST http://localhost:3000/api/v1/products/import \
  -H 'Origin: http://demo.localhost:2999' -H 'Authorization: Bearer <token>' \
  -F 'file=@productos.csv'
```

Respuesta **200**: `{ "created": 12, "updated": 30, "errors": [] }`

Respuesta **400** (JSON, nada importado):

```json
{ "created": 0, "updated": 0,
  "errors": [ { "row": 4, "message": "'precio' is not a valid number: '15.5'" } ] }
```

`row` es la línea del archivo (1 = encabezados; 0 = el archivo entero). Mensajes posibles:

| Mensaje | Fila |
|---|---|
| `The file is empty` / `The file exceeds 5 MB` / `The file is not a valid CSV: …` / `The file has no header row` / `The file has more than 5000 products` | 0 |
| `Unknown column: '…'` / `Duplicated column: '…'` / `The 'nombre' column is required` | 1 |
| `The name is required` / `The product '…' is repeated in the file` / `The name '…' belongs to a deleted product` | La del producto |
| `'…' has more than 255 characters` / `'…' must be zero or greater` / `'…' must have up to …` / `'…' is not a valid number: '…'` | La del producto |

Se registra en la auditoría una sola entrada `IMPORT` con el resumen.

## Combos

Los cambios piden `COMBOS` (incluido el precio del combo). Las lecturas, cualquier área del panel.

`BundleDto`:

```json
{
  "id": 3, "name": "Comedor completo", "enabled": true,
  "price": 450000.00, "listPrice": 520000.00, "available": 2,
  "products": [ { "productid": 12, "productName": "Mesa", "quantity": 1 },
                { "productid": 15, "productName": "Silla", "quantity": 4 } ]
}
```

(`productid` va en minúscula.)

### GET /api/v1/bundles/all

**Acceso:** cualquier área del panel

Todos los combos, también los deshabilitados. Respuesta **200**: lista de `BundleDto`.

### GET /api/v1/bundles/{id}

**Acceso:** cualquier área del panel

Respuesta **200**: `BundleDto`, o **200 con el cuerpo vacío** si no existe.

### POST /api/v1/bundles

**Acceso:** `COMBOS`

```json
{ "name": "Comedor completo", "enabled": true, "price": 450000 }
```

Respuesta **200** (no 201): `BundleDto` sin productos. Error: 400 `Price and cost must be zero or greater`.

### PUT /api/v1/bundles/{id}/enabled

**Acceso:** `COMBOS`

Habilita o deshabilita el combo (deshabilitado no aparece en el catálogo).

```json
{ "enabled": false }
```

Respuesta **200**: `BundleDto`. Errores: 400 `The enabled field is required (true or false)`, 404 `Bundle not found`.

### PUT /api/v1/bundles/{id}/price

**Acceso:** `COMBOS`

```json
{ "price": 430000 }
```

Respuesta **200**: `BundleDto`. Errores: 400 `Price and cost must be zero or greater`, 404 `Bundle not found`.

### PUT /api/v1/bundles/{id}/product

**Acceso:** `COMBOS`

Agrega un producto al combo o cambia su cantidad.

```json
{ "productId": 15, "quantity": 4 }
```

Respuesta **200**: `BundleDto`. Un combo o producto inexistente responde **500**.

### PUT /api/v1/bundles/{id}/product/bulk

**Acceso:** `COMBOS`

Igual con una lista: `[ { "productId": 12, "quantity": 1 }, { "productId": 15, "quantity": 4 } ]`. Respuesta
**200**: `BundleDto` después del último.

### DELETE /api/v1/bundles/{id}/product

**Acceso:** `COMBOS`

Saca un producto: body `{ "productId": 15 }`. Respuesta **200**: `true`.

### DELETE /api/v1/bundles/{id}

**Acceso:** `COMBOS`

Borra el combo (de verdad). Respuesta **200**: `true` (también si no existe).

## Catálogo público

Sin login, desde el frontend de la tienda. No muestra stock exacto ni costo: `availability` es `DISPONIBLE`,
`POCAS_UNIDADES` o `AGOTADO`. `price: null` = "consultar precio". Cada producto y combo trae su `id`, que es lo que
usa el carrito para armar el pedido. Todo se cachea en Redis por tienda.

`CatalogProductDto`:

```json
{ "id": 12, "slug": "mesa-ratona", "name": "Mesa ratona", "category": "Mesas",
  "description": "Mesa baja de roble", "price": 85000.00, "availability": "POCAS_UNIDADES",
  "featured": true, "images": [ "3f2a..._1730.png" ] }
```

### GET /api/v1/catalog/products

**Acceso:** Público

| Parámetro | Default | Qué hace |
|---|---|---|
| `q` | — | Busca en nombre, descripción y categoría |
| `category` | — | Categoría exacta |
| `sort` | `name` | `name`, `price_asc`, `price_desc` (sin precio al final) o `best_sellers` |
| `page` | 0 | |
| `size` | 24 | Máximo 100 |

```bash
curl 'http://localhost:3000/api/v1/catalog/products?q=mesa&sort=price_asc' -H 'Origin: http://demo.localhost:2999'
```

Respuesta **200**: `{ "items": [CatalogProductDto], "page", "size", "totalItems", "totalPages" }`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | `sort` inválido | `The sort must be name, price_asc, price_desc or best_sellers` |
| 400 | `page` o `size` fuera de rango | `The page must be 0 or greater and the size between 1 and 100` |

La primera página sin búsqueda cuenta como una visita al catálogo (panel de uso, ver
[observability.md](observability.md#websocket)).

### GET /api/v1/catalog/featured

**Acceso:** Público

Destacados para la home, en el orden que eligió el administrador. Respuesta **200**: lista de `CatalogProductDto`.

### GET /api/v1/catalog/products/{slug}

**Acceso:** Público

Detalle para el link compartido: `CatalogProductDto` con `images` en orden y `metadata` (o `null`). Cuenta una vista
del producto para el panel de uso.

Error: 404 `Product not found`.

### GET /api/v1/catalog/categories

**Acceso:** Público

```json
[ { "name": "Mesas", "products": 8 }, { "name": "Sillas", "products": 12 } ]
```

### GET /api/v1/catalog/bundles

**Acceso:** Público

Combos que se pueden comprar (ver [reglas](#conceptos-y-reglas)), por nombre.

```json
[ { "id": 3, "name": "Comedor completo", "price": 450000.00, "listPrice": 520000.00,
    "availability": "DISPONIBLE", "image": "3f2a..._1730.png",
    "products": [ { "slug": "mesa", "name": "Mesa", "quantity": 1 } ] } ]
```

`listPrice` sirve para mostrar el ahorro; `image` es la primera imagen del primer producto que tenga. Un combo
solo está `DISPONIBLE` o `AGOTADO`.

## Link para compartir

### GET /share/{tienda}/products/{slug}

**Acceso:** público (sin login). Es la vista previa del link que comparte el frontend en WhatsApp o Instagram.

El frontend comparte la página del producto en la tienda (`https://<tienda>.<dominio>/productos/{slug}`). Los
crawlers de las vistas previas no ejecutan JavaScript, así que en el server Caddy reconoce su `User-Agent` y les
pasa ese pedido a esta ruta (ver `backend/deploy/caddy/Caddyfile`); las personas abren la SPA.

Los crawlers tampoco mandan `Origin`: por eso la tienda va en la ruta. El
gateway la resuelve con su identificador (arma la URL de la tienda con el esquema, host y puerto de `platform.url`)
y reescribe la ruta a `/share/products/{slug}` en products.

Respuesta **200** `text/html` con las etiquetas `og:title`, `og:description` (precio y descripción, hasta 200
caracteres), `og:image` (la primera imagen, con URL absoluta), `og:url` y una redirección
(`<meta http-equiv="refresh">`) a `<URL de la tienda>/productos/{slug}`, la página del producto en el frontend.
`Cache-Control: public, max-age=3600`. Los textos se escapan. Usa el detalle del catálogo, con su caché.

- Producto inexistente o borrado: **404** con el mismo HTML, sin `og:title`, que redirige a la home de la tienda.
- Tienda inexistente (404) o deshabilitada (403): los responde el gateway, en texto.
- Configuración: `share.store-url` (default `http://{identifier}.localhost:2999`) y `share.public-url` (el gateway
  visto desde afuera, para la imagen; default `http://localhost:3000`). En producción salen del `.env`
  (`SHARE_STORE_URL` y `SHARE_PUBLIC_URL` en `docker-compose.prod.yml`).

```bash
curl http://localhost:3000/share/demo/products/mesa-de-roble
```

## Plataforma

### GET /api/v1/platform/products/usage?sort=

**Acceso:** SUPERADMIN, sin tienda (con tienda: 404 `This endpoint is only available on the platform.`)

Productos (no borrados) y combos de cada tienda que ya abrió products, de mayor a menor. Se guarda 5 minutos en
Redis. `sort`: `products` (default) o `bundles`.

```json
[ { "schema": "demo", "products": 48, "bundles": 3 } ]
```

Las tiendas que nunca abrieron products no aparecen (el frontend las cuenta como 0) y puede aparecer alguna
borrada: el frontend se queda con las que lista users (`/api/v1/platform/admin/usage`), juntando por `schema`.

Error: 400 `Invalid sort: use products or bundles.`

## Internos

No los llama el frontend: los usa sales, directo por Eureka, con el token interno y el `X-Forwarded-Host` de la
tienda del pedido. Detalle de las reglas en [ventas.md](../ventas.md#endpoints-internos-de-products).

| Método | Ruta | Qué hace | Respuesta |
|---|---|---|---|
| POST | `/api/internal/products/reservations` | Reserva el stock de un pedido (todo o nada) | `ReservationDto` |
| POST | `/api/internal/products/reservations/{reference}/release` | Devuelve el stock | 204 |
| POST | `/api/internal/products/reservations/{reference}/sold` | Suma a vendidos | 204 |
| POST | `/api/internal/products/reservations/{reference}/unsold` | Resta de vendidos | 204 |

Reserva:

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

`sold` y `unsold` reciben `{ "items": [ { "type", "id", "quantity" } ] }`.

| Código | Cuándo | Mensaje |
|---|---|---|
| 400 | Sin referencia (hasta 40) o ítems inválidos | `The reservation needs a reference (up to 40 characters) and items with type, id and a quantity greater than zero` |
| 409 | Producto o combo inexistente, combo deshabilitado o sin productos | `Item not available` (con `: <nombre>` cuando es un combo) |
| 409 | Sin precio (pedido online) | `Item without price: <nombre>` |
| 409 | Sin stock (pedido online) | `Not enough stock: <nombres>` |
| 409 | La reserva ya se devolvió | `The reservation was already released` |
| 409 | La misma referencia se está procesando | `The reservation is being processed by another request` |
| 404 | `sold` / `unsold` de una reserva que no existe | `Reservation not found` |

Sin el token interno: 403 `Token invalido` (JSON con `status`, `error`, `message`).

## Cómo se comunica

- **Recibe** del gateway `X-Forwarded-Host` (la tienda, obligatoria salvo en `/products/images/**` y
  `/api/v1/platform/**`: sin ella responde 404) y `X-User-Id`.
- **Pide los roles** a users (`GET /api/internal/users/roles/{id}`): primero caché local (1 minuto), después Redis
  (`user:<id>:<schema>`, lo escribe users) y por último users, con circuit breaker, reintentos y timeout. Un cambio
  de permisos tarda hasta 1 minuto en aplicarse.
- **Redis:**

| Key | Contenido | Dura |
|---|---|---|
| `catalog:<schema>:v<versión>:<consulta>` | Respuestas del catálogo | 1 hora. Cada cambio de productos, combos, stock o vendidos sube `catalog:<schema>:version` y deja todo viejo al instante (después del commit) |
| `platform:usage:products` | Uso por tienda del panel de la plataforma | 5 minutos |
| `usage:<schema>:<yyyy-MM-dd>` (hash) | Visitas al catálogo (`visits`) y vistas por producto (`product:<slug>`), por día de Buenos Aires | 8 días |

  Si Redis falla, el catálogo se lee de la base y los contadores se pierden; nada se corta.
- **Eventos** (`kleinkauf:events`, para el panel en vivo `store.activity`), después del commit:
  - `stock.movement`: movimiento cargado a mano (`productId`, `productName`, `movementType`, `quantity`,
    `stockAfter`, `reason`, `userId`).
  - `stock.low`: un producto cruza a stock bajo con cualquier movimiento, también los de pedidos (`productId`,
    `productName`, `stock`, `minStock`).
- **Auditoría:** `Product`, `Bundle`, `BundleProduct`, `ProductImage`, `ProductMetadata` y `StockMovement` son
  `@Audited`. A mano: la importación CSV (`IMPORT`) y las lecturas de costo y del export (`READ`). Ver
  [auditoria.md](../auditoria.md).
- No llama a sales: sales lo llama a él.

## Modelo de datos

Base `products`, un schema por tienda. Tablas creadas por Hibernate (`ddl-auto=update`).

| Tabla | Columnas principales | Notas |
|---|---|---|
| `product` | `id`, `name` (único), `slug` (único), `category`, `description`, `stock`, `min_stock`, `price`, `cost` (`numeric(12,2)`), `featured`, `featured_order`, `sold_count`, `deleted_at` | Borrado lógico |
| `productmetadata` | `id`, `product_id`, `material`, `color`, `style`, `height`, `width`, `length`, `weight` | Uno por producto |
| `productimages` | `id`, `product_id`, `path`, `image_order` | `path` es el nombre del archivo |
| `stock_movements` | `id`, `product_id`, `type`, `quantity` (diferencia), `stock_after`, `reason`, `reference` (número de pedido), `user_id`, `created_at` | No se modifica ni se borra |
| `bundles` | `id`, `name`, `enabled`, `price`, `sold_count` | Borrado real |
| `bundle_products` | `id`, `bundle_id`, `product_id`, `quantity` | |
| `stock_reservations` | `id`, `reference` (única), `status` (`RESERVED`/`RELEASED`), `sold`, `shortages`, `created_at`, `released_at` | Idempotencia de las reservas |

Las imágenes están en disco en `image.path` (`uploads/products/images`), fuera de cualquier tienda.

## Configuración

| Propiedad | Valor en desarrollo | Para qué |
|---|---|---|
| `server.port` / `management.server.port` | `3002` / `9002` | |
| `spring.datasource.*` | `jdbc:postgresql://localhost:5432/products` | Base |
| `spring.data.redis.*` | `localhost:6379` | Caché, contadores y eventos |
| `users.url` | `http://users-microservice/` | Roles (por Eureka) |
| `api.internal.key` | — | Token interno. **Cambiarlo fuera de desarrollo** |
| `image.path` | `uploads/products/images` | Carpeta de las imágenes |
| `kleinkauf.tenant.public-paths` | `/products/images/,/api/v1/platform/` | Rutas sin tienda |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `5MB` / `10MB` | Subidas (las imágenes además tienen 1 MB cada una) |
| `http.client.connect-timeout` / `read-timeout` | `2s` / `3s` | Llamadas a users |
| `resilience4j.*.instances.users.*` | | Circuit breaker y reintentos hacia users |

Levantarlo solo: `cd backend/kleinkauf-product && ./gradlew bootRun`. Los logs de SQL y seguridad están en DEBUG en
desarrollo.

## Límites conocidos

- Crear un producto con el nombre de uno **borrado**, o renombrar uno con un nombre usado, responde 500 (choca con
  el único de la base; el alta solo mira los activos y la edición no mira).
- `PUT /products/{id}` reemplaza los cuatro campos: mandar solo uno borra los otros.
- Combos: agregar un producto a un combo o con un producto inexistente responde 500; `GET /bundles/{id}`,
  `PUT /bundles/{id}` y `DELETE /bundles/{id}` responden 200 aunque no exista.
- Las imágenes se guardan siempre como `.png` y no se valida que sean imágenes (solo el tamaño).
- Los altas responden 200 en vez de 201.

## Para limpiar

| Endpoint | Por qué | En su lugar |
|---|---|---|
| — | Nada por ahora: `/all`, `/search` y el toggle de combos se borraron (2026-11-03). `GET /products/featured` queda: lo usa el panel para ordenar los destacados (el listado no trae el orden) | — |
