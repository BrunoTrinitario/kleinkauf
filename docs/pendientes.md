# Pendientes

_Última actualización: 2026-11-04 (deuda técnica cerrada: refresh revocable, rate limit en Redis, CI y auditoría; A1 cerrado)_

Lista de lo que queda por hacer, armada comparando la documentación ([MVP.md](../MVP.md), `docs/` y la referencia
por servicio en [docs/backend/](backend/README.md)) con el código. Cada tarea tiene el flujo previsto para
implementarla.

**Resumen:** el backend de los dos paneles, las ventas, la observabilidad (también los canales de la tienda) y la
auditoría está hecho. Lo que más falta es el **frontend**: hoy cubre la cuenta, el
catálogo con destacados, combos y página de producto, la compra online, el panel de productos (stock, destacados, CSV
y combos), usuarios y permisos, el resumen de la tienda, ajustes, el panel de la plataforma y el panel de ventas;
falta el tiempo real. El backend del MVP está completo. La **gestión presencial** todavía no tiene decisiones tomadas.

### Resuelto desde la versión anterior

- **Auditoría**: `audit_events` está particionada por mes (las tablas de antes se pasan solas la primera vez) y el
  historial se exporta en CSV (`/store/audit/export` y el de la plataforma), hasta 50.000 eventos, con la hora
  argentina y protegido contra fórmulas de Excel. Ver [auditoria.md](auditoria.md#consultas). Falta el botón en el
  frontend, que va con el feed de auditoría de B8.
- **A1 cerrado**: las altas por período de los paneles van en hora argentina (`DashboardTime`,
  `users.dashboard.zone`). Se decidió no sumar pedidos y facturación de todas las tiendas (el superadmin no ve los
  datos de las tiendas) y dejar "activos" como el último login en 30 días.
- **Deuda técnica (D) completa**, salvo lo que hay que decidir:
  - **Refresh revocable**: cada refresh tiene un `jti` y se guarda en Redis. Se usa una vez (renovar da uno nuevo y el
    viejo dura 30 segundos más, por las pestañas) y `POST /auth/logout` (y el de la plataforma) corta el de esa
    sesión; el frontend lo llama al cerrar sesión.
  - **Rate limit en Redis**: el contador lo comparten todos los gateways; si Redis falla, cuenta en memoria.
  - **CI** (`.github/workflows/ci.yml`): tests de cada servicio y build del frontend en cada push y PR a `main`.

- **Recupero de contraseña** (B1): "¿Olvidaste tu contraseña?" en el login de las tiendas lleva a `/recuperar`, que pide
  el mail; el link lleva a `/reset-password?token=`, que pide la nueva y vuelve al login. Si el link venció o ya se
  usó, ofrece pedir otro. Para que lleguen los mails, el server necesita `MAIL_USERNAME` y `MAIL_PASSWORD` (ver
  [E. Server](#e-server)).
- **Caché de roles**: `CachedUser` no guardaba los roles y en Redis quedaba `"null"`, así que products, sales y el
  gateway le pedían los roles a users en cada request (y con users caído, 403). Lo encontró la prueba de punta a punta.

- **Deuda técnica (D)**, salvo lo que hay que decidir:
  - products: crear o renombrar con el nombre de un producto (también borrado) responde 409 y no 500; el nombre se
    valida (obligatorio, hasta 255). `PUT /products/{id}` cambia solo lo que se manda. `GET` y `DELETE /bundles/{id}`
    responden 404 si el combo no existe. Las imágenes se validan por sus primeros bytes (JPG, PNG, WEBP o GIF, 415 si
    no) y se guardan con su extensión.
  - Respuestas: `POST /products` y `POST /bundles` responden 201; un email repetido da 409 también en las tiendas; un
    rol inexistente da 400 si viene como parámetro y 404 si viene en la ruta.
  - El mail del recupero que falla queda en el log como `ERROR` (`MailService`).
  - **Refresh token**: el login devuelve un `refreshToken` de 7 días y `POST /auth/refresh` (y el de la plataforma)
    renueva la sesión; el frontend lo hace solo ante un 401. Deja de servir si cambia la contraseña.
  - Rate limit: usa la IP de la conexión y no el `X-Forwarded-For` del cliente; detrás de Caddy la arma
    `forward-headers-strategy`.
  - users migra el schema de cada tienda al arrancar (como products) y no en la primera request de cada una.
  - Tests: los que levantan la app corren con **Testcontainers** (Postgres y Redis reales; sin Docker se saltean) y
    en UTC; se arreglaron `ProductServiceTest.deleteProductById_*` y `UserServiceTest`, que estaba desactualizado.
    Pasan los 471 tests del backend.
  - **Prueba de punta a punta** (`backend/deploy/e2e/e2e.mjs`): alta de tienda, sesión y refresh, productos e
    imágenes, catálogo, pedido como invitado, reporte de un problema y panel de la plataforma, contra todo levantado.
    Ver el README.
  - README (orden de arranque, bases de `init.sql` y cómo crear las que falten) y [frontend.md](frontend.md) al día.
- **Seguridad de la API**: el gateway responde 404 en las rutas que no rutea (antes 401) y ya no expone
  `/api/internal/tenants/**`; los JWT llevan su tipo (`access`, `refresh`, `password`), así un token de sesión no
  cambia la contraseña y el del mail no sirve para entrar; el link de recupero se usa una sola vez; registro,
  recupero y reportes tienen el límite de 3 por minuto; users pide el token interno en todo `/api/internal/**`;
  products rechaza lo que no tiene regla propia en vez de dejarlo abierto.
- **Reportar un problema**: link en el footer de las tiendas (`POST /api/v1/bug-reports`, con o sin sesión) y lista
  en el panel de la plataforma (`/bug-reports`) para marcarlos resueltos.
- **Privacidad y términos**: páginas `/privacidad` y `/terminos` con links en el footer (textos base, para que los
  revise un abogado).
- **Server**: headers de seguridad en Caddy (la CSP en Report-Only) y el backup diario armado pero sin activar
  ([deploy.md](../deploy.md)).

- **Panel de ventas completo** (B7): la lista de pedidos suma los filtros por medio de pago y "stock para revisar";
  el historial muestra el nombre de quien hizo cada cambio (`GET /users/{id}`, `Usuario #id` si se borró); **venta
  del local** en `/admin/orders/new` (productos y combos, precio cobrado, descuento, medio de pago, entregado o
  pagado, comprador y entrega opcionales, aviso cuando falta stock) y **reportes** en `/admin/reports` (rango, día /
  semana / mes, canal y medio de pago, totales con ticket promedio, gráfico, desglose y ranking por unidades,
  facturado o ganancia).
- **Una venta del local entregada no se podía anular**: `ENTREGADO` es final y el cancelar respondía 409, aunque
  [ventas.md](ventas.md) decía que se anula como cualquier pedido. Ahora sales permite `ENTREGADO → CANCELADO` solo
  para el canal `LOCAL` (devuelve el stock y resta de vendidos); los pedidos online entregados siguen siendo finales.
- **Seguimiento de pedidos** (B7, primera parte): "Ventas" en el menú del `ADMINISTRADOR`, con el contador de
  comprobantes para revisar. Lista en `/admin/orders` (búsqueda, estado, canal y fechas en la URL), detalle en
  `/admin/orders/{número}` (ítems con precio de lista, cobrado y costo, ganancia, comprador, entrega, historial y
  comprobantes para ver o descargar) y los cambios de estado de [ventas.md](ventas.md) con confirmación y motivo.
  "Datos de cobro" (alias, CBU, titular, banco y plazo) en un diálogo; la lista avisa si faltan, y el checkout le
  ofrece al administrador cargarlos en vez de solo decir que la tienda no vende online.
- **Descargar un comprobante daba 500**: la lectura corría en una transacción `readOnly` y el registro de auditoría
  (insert en `outbox_events`) fallaba. Ahora la transacción admite escritura.
- **Panel de la tienda** en el backend: `GET /api/v1/products/dashboard` y `GET /api/v1/users/dashboard`, solo
  `ADMINISTRADOR`. Se borró `/users/stats` y [panel-admin.md](panel-admin.md) pasó a documentación.
- **Canales WS de la tienda**: `store.activity` (pedidos, comprobantes, movimientos y stock bajo), `store.usage`
  (visitas, más vistos y tráfico) y `store.users` (conectados), con sus `GET`.
- **Última actividad** del uso por tienda: toma lo más reciente entre el último login y las keys `activity:*`.
- **Lecturas con stock exacto** (`GET /products/{id}`, `/featured`, `/search`, `/bundles/**`): ya no son públicas,
  piden un área del panel. Los destacados públicos están en `GET /api/v1/catalog/featured`.
- **Link del mail de recupero**: lleva a `<URL de la tienda>/reset-password?token=`.
- `endpoints.md` se reemplazó por la referencia por servicio en `docs/backend/`.
- **CORS en las respuestas que corta el gateway** (paso 0 de B0): `CorsConfig` corre antes del rate limit, del
  tenant y del JWT, y acepta los subdominios de la plataforma aunque la tienda no exista. Los 401, 404/403 y 429
  salen con las cabeceras CORS.
- **Cliente HTTP único** (paso 1 de B0): todas las llamadas pasan por `lib/api.ts`.
- **TanStack Query** (paso 2 de B0): las pantallas leen con hooks por dominio (`src/api/catalog.ts`, `products.ts`,
  `users.ts`, `account.ts`, `platform.ts`); los cambios invalidan lo que corresponde (un producto nuevo se ve en el
  panel y en el catálogo) y la caché se vacía al entrar o salir de la sesión.
- **Tienda no disponible y 429** (paso 3 de B0): si el gateway dice que la tienda no existe (404) o está
  deshabilitada (403), la app muestra una página en lugar de las rutas, también al entrar directo al login. El 429
  sale como un solo aviso aunque fallen varias llamadas.
- **Rutas protegidas** (paso 4 de B0, con lo que B0 queda completo): `RequireAuth`, `RequireRole`, `RequireArea` y
  `RequireSuperadmin` en `App.tsx`, en lugar del guard de cada página. Siguen al **rol activo** (el de "Cambiar
  rol"): el panel de productos abre con cualquier área del panel y crear, editar o borrar piden `PRODUCTOS`;
  usuarios pide `USUARIOS`; el panel de la tienda, `ADMINISTRADOR`. El menú usa los mismos criterios. Cerrar sesión
  lleva a la home (a `/login` en la plataforma).
- **Panel de productos** (B3): movimientos de stock con historial (`STOCK`), tarjeta de stock bajo y contador en el
  menú, destacados con estrella y diálogo para ordenarlos (`PRODUCTOS`), importar y exportar CSV con la tabla de
  errores por fila (`PRODUCTOS` y `PRECIOS`) y página de combos en `/admin/bundles` (`COMBOS` para cambiar).
- **Usuarios y permisos** (B4): los roles se dan y se sacan desde la edición de cada usuario, y la página de usuarios
  suma la matriz de permisos por rol (`EMPLEADO` y `CLIENTE` configurables, `ADMINISTRADOR` solo lectura,
  `USUARIOS` bloqueado, aviso de que tarda hasta 1 minuto).
- **Resumen de la tienda** (B5): `/admin` muestra ventas (facturado, ventas, unidades, ganancia con el aviso de
  unidades sin costo, gráfico por período, canal, medio de pago y más vendidos), productos, stock (valor del
  inventario y últimos movimientos) y usuarios (activos, por rol y altas). Cada bloque carga por separado y si un
  servicio falla el resto sigue. El tiempo real de la tienda queda para B8.
- **Catálogo y compartir** (B2 y A2): la home muestra los destacados y los combos (con el ahorro), y cada producto
  tiene su página en `/productos/{slug}`. Compartir por WhatsApp o copiando el link usa
  `GET /share/{tienda}/products/{slug}`: la tienda va en la ruta (el crawler no manda `Origin`) y products responde
  un HTML con Open Graph que redirige a esa página.
- **Limpieza de endpoints** (A3): se borraron `GET /products/all`, `/products/search`, `GET /users/all` y
  `/api/internal/users/all` (sin uso); el combo se habilita con `PUT /bundles/{id}/enabled` y la cuenta propia se
  edita y se borra con `PATCH` y `DELETE /auth/me`. `/products/featured` y `/api/internal/tenants/all` quedan porque
  se usan. Además, en users, products y sales una ruta inexistente responde 404, un método que la ruta no tiene 405
  y un parámetro o cuerpo inválido 400 (antes, 500).
- **Movimientos de stock sin motivo daban 500**: `KleinKaufEvent` copiaba los datos con `Map.copyOf`, que no acepta
  `null`, y el `NullPointerException` deshacía el movimiento. Ahora acepta `null`. Además, `GlobalExceptionHandler`
  loguea con la traza cualquier 500 no previsto (antes no dejaba rastro).
- **Sesión vencida** (B1): un 401 cierra la sesión y vuelve a `/login?next=<página>`; las páginas privadas también
  mandan al login con `next`, y el login vuelve ahí.
- **Borrar la propia cuenta** (B1): "Eliminar mi cuenta" en ajustes, con confirmación.
- **La tienda no se queda sin administrador**: users responde 409 si se quiere borrar o sacarle el rol al último
  `ADMINISTRADOR` (a sí mismo o desde el panel de usuarios).
- **`TODO`**: se sacaron las notas viejas (también la de que ventas "no entra"). Después se unió a este archivo y se
  borró.
- **Altas con 201**: usuarios, tiendas, superadmins, pedidos y movimientos de stock ya responden 201.
- **Compra online** (B6): carrito en `localStorage` con productos y combos (`lib/cart.tsx`, ícono con la cantidad en
  el header); checkout en `/checkout` como invitado o con sesión, con retiro o envío, que ante el 409 de precios
  actualiza el carrito y muestra antes y después; pantalla del pedido en `/pedido/{número}` con los datos para
  transferir, cuenta regresiva, comprobante, motivo del rechazo, cancelar e historial; consulta del invitado en
  `/pedido` con número y email, y "Mis pedidos" en `/mis-pedidos`. El navegador recuerda número y email de los
  pedidos (`lib/known-orders.ts`) para abrirlos sin volver a pedir el email.
- **Configuración de la tienda** (parte de B12): en `/admin/store` el administrador elige el nombre (sin nombre
  propio sale del identificador), uno de 7 temas de colores (6 claros y "Noche", oscuro) y un ícono, y si el ícono va
  junto al nombre y como favicon. Lo guarda products (`store_settings`, una fila por schema; `GET
  /api/v1/catalog/store` público y `/api/v1/store/settings` solo `ADMINISTRADOR`). El frontend aplica el tema pisando
  las variables de `index.css` (`lib/store-themes.ts`) y lo recuerda en el navegador para no arrancar con otros
  colores. El footer dice "Powered by KleinKauf" y la plataforma tiene su favicon propio.
- **Home, catálogo y combos separados**: `/` es una vidriera corta (destacados y combos en carruseles con flechas que
  pasan de a 4 y vuelven al principio, y la primera página del catálogo con "Ver todos"); en el celular la navegación
  va en un menú lateral. El listado completo está en `/catalogo`, con búsqueda, categoría, orden y
  página en la URL (`?q=&categoria=&orden=&pagina=`, `lib/catalog-url.ts`), así "atrás" vuelve al mismo listado y una
  búsqueda se puede compartir; los combos tienen su página en `/combos`. En la página de un producto, "Volver" va a la
  página anterior y, si se entró por un link compartido, a `/catalogo`.
- **Combos completos**: `POST /bundles` crea el combo con nombre, productos y precio de una vez y valida todo antes de
  guardar (sin productos, cantidad 0, repetidos o inexistentes no se crea). `PUT /bundles/{id}/product` responde 404
  en vez de 500, ya no duplica el producto al cambiar la cantidad y el bulk aplica todo o nada. En `/admin/bundles` el
  diálogo de crear pide nombre, productos (con imagen, precio y subtotal) y al final el precio con la suma a la vista,
  y las tarjetas muestran miniaturas. En `/combos` cada combo lista sus productos con foto y precio, y tocar la foto
  abre la descripción de cada uno y la cuenta contra el precio del combo (el catálogo trae categoría, descripción,
  precio e imagen de cada producto del combo).
- **Combos con descuento** (A4): el combo puede tener precio fijo, % de descuento o $ de descuento sobre la suma de
  sus productos (`pricingType` y `discount` en `bundles`; los que ya existían quedaron en `FIXED`). Con descuento el
  precio no se guarda: lo calcula `BundleFactory.price` y lo usan el panel, el catálogo y la reserva que le pasa el
  precio a sales, así sigue al precio de los productos sin actualizar el combo. En `/admin/bundles` se elige el tipo
  y se ve el precio final y el ahorro mientras se escribe.

---

## A. Backend

### A1. Ajustes de plataforma

Resuelto: las altas por período de los paneles van en hora argentina (`DashboardTime`, `users.dashboard.zone`).
Decidido no hacer:

- **Pedidos y facturación de toda la plataforma**: el superadmin no ve los datos de las tiendas.
- **"Activos" por última request**: sigue siendo el último login en 30 días.

---

## B. Frontend

Sigue el orden de [frontend.md](frontend.md). Lo de abajo es lo que no existe en `frontend/src`. El backend de todo
esto ya está.

### B8. Observabilidad y auditoría

1. Hook `useLiveChannel`: pide un ticket (un solo uso, 30 s), conecta a `/ws?ticket=`, manda ping cada 30 s,
   reconecta con un ticket nuevo y no reintenta si el ticket responde 403.
2. Plataforma: salud y tráfico (`overview` más `platform.health` y `platform.traffic`), auditoría
   (`platform.audit`) y refresco del resumen con `platform.business`.
3. Tienda: feed de auditoría (`store.audit`), actividad en vivo (`store.activity`), uso del día (`GET /store/usage`
   más `store.usage`) y conectados (`GET /store/users` más `store.users`).
4. El historial se carga por REST al abrir el panel y el WS solo trae lo nuevo.
5. Botón "Exportar CSV" en la auditoría de la tienda y de la plataforma (`/store/audit/export`, `/audit/export`),
   con los filtros que estén puestos.

### B10. Faltantes chicos (de la revisión de [frontend.md](frontend.md))

- **Margen en precios**: mostrar `margin` y `marginPercent` (ya vienen en `GET /products/{id}/pricing`) junto al
  precio y el costo, solo con `PRECIOS`.
- **Más vendidos**: mostrar `soldCount` en el panel y sumar el orden `best_sellers` al catálogo (el backend ya lo
  acepta).
- **Estados vacíos y de carga comunes**: hoy cada página arma los suyos.
- **Plataforma**: landing y alta pública de tienda (hoy `/` redirige a `/dashboard` y la tienda se crea desde el
  panel del superadmin).

### B9. Calidad

- Tipos de todas las respuestas (las fichas de `docs/backend/` tienen los ejemplos).
- Tests de permisos y de los flujos principales.
- Accesibilidad básica.

### B11. Publicación

- SEO: meta tags, Open Graph, sitemap, robots y performance de las páginas.
- Que un abogado revise los textos de `/privacidad` y `/terminos` (`lib/strings.ts`, `legal`).

### B12. Configuración por tienda

- Textos de la tienda configurables (nombre, tema e ícono ya están, ver arriba).
- Que cada visitante elija claro u oscuro (hoy lo decide la tienda con el tema "Noche").
- Mails de recupero de contraseña configurables por tienda.
- Configuración general de la tienda desde su panel.

---

## C. Gestión presencial

Ninguna funcionalidad de la sección 3 de [MVP.md](../MVP.md) tiene la decisión marcada. Cada una se planifica con
preguntas antes de implementarla, como se hizo con ventas.

### C1. Próxima etapa de ventas (P1, P2, P5 y P6)

1. **P1 – área `VENTAS` (bit 32)**: users suma el bit a la máscara; sales cambia las validaciones de solo
   `ADMINISTRADOR` a `PERM_VENTAS`; el frontend lo suma a la matriz de permisos.
2. **P2 – código de producto o de barras**: campo `code` en products, único por tienda; endpoint de búsqueda por
   código; columna en el CSV; buscador del mostrador con soporte para escáner (funciona como un teclado).
3. **P5 – stock reservado visible**: products calcula lo reservado con `stock_reservations` activas y lo expone en el
   panel y en la venta del local.
4. **P6 – pagar al retirar**: medio de pago al validar el pedido online y la transición a `PAGADO` con medio, sin
   comprobante.

### C2. Caja (P3) y precio según medio de pago (P4)

- Caja: entidad `CashSession` (apertura, cierre, arqueo esperado contra real) y movimientos de ingreso y egreso.
- Medio de pago: reglas de recargo o descuento por medio, aplicadas al armar la venta.

### C3. Después

- Ficha de cliente, señas y presupuestos (P7 a P9), como módulo propio.
- Proveedores y compras (P10) y gastos (P11).
- P12 a P15 más adelante.

### C4. "Para después" de ventas

- Mercado Pago con webhook.
- Avisos por email.
- Comprobante imprimible.
- Exportar los reportes a CSV.
- Zona horaria por tienda (hoy es una para todo el servicio, y el uso del catálogo usa siempre Buenos Aires).
- Envíos.
- Stock y vendidos por eventos con outbox, que resuelve los casos de vuelta atrás que hoy quedan solo en el log.

---

## D. Deuda técnica y prolijidad

- **CHECK de los enums**: las columnas `@Enumerated(EnumType.STRING)` (estado del pedido, canal, medio de pago,
  entrega, tipo de movimiento de stock, estado de la reserva, tipo de precio del combo) tienen un `CHECK` que
  Hibernate crea con los valores de ese momento, y `hbm2ddl update` no lo actualiza: al sumar un valor nuevo, guardarlo
  falla en los schemas que ya existían. Antes de agregar un valor hay que borrar ese `CHECK` en cada schema (o guardar
  la columna como texto, como `store_settings.theme`).
- **Configuración de ventas duplicada**: `sales_settings` es "una fila por schema" pero con id autoincremental; si
  los dos primeros guardados de una tienda llegan juntos, quedan dos filas (y se usa la de menor id).
  `store_settings` lo resuelve con id fijo y un reintento (`StoreSettingsService.save`).

**Decidido**

- Eureka: queda con una sola instancia; si hace falta, se suman más.
- Observabilidad (muestreo de trazas, alertas en Grafana, retención, healthchecks con `/ready`, historial de uso más
  allá de 8 días): queda como está, para más adelante.

**Revisiones**

- Performance del código: consultas lentas, N+1, uso de caché y tiempos por endpoint.
- Performance del server: CPU, memoria y disco por contenedor, heap de cada JVM y latencia de punta a punta.
- Pasar la CSP de Report-Only a `Content-Security-Policy` cuando no aparezcan avisos en la consola (ver
  [deploy.md](../deploy.md)).

---

## E. Server

Tareas en el VPS (ver [deploy.md](../deploy.md)):

1. Cargar `MAIL_USERNAME` y `MAIL_PASSWORD` (contraseña de aplicación de Gmail) en `~/kleinkauf/.env`: sin eso el
   recupero de contraseña no manda mails.
2. Cambiar la contraseña del superadmin inicial.
3. SSH: entrar solo con clave (`PasswordAuthentication no`) y fail2ban.
4. Activar los backups de Postgres (ya están armados) y copiarlos fuera del server de vez en cuando.
5. Dominio pago: DNS con comodín (`*.dominio`) y pasar `PLATFORM_DOMAIN` y lo demás en `~/kleinkauf/.env`.

---

## Orden sugerido

1. **B8**: tiempo real en los paneles. Ya están B0 a B7 y A1 a A3.
2. **E. Server**: sobre todo los mails.
3. **Decidir y planificar C1**, la próxima etapa de ventas.
