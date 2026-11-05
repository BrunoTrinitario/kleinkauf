# Frontend (plan)

Decisión de stack y checklist de lo que tiene que cubrir el frontend nuevo, armada a partir de la documentación del
backend ([referencia por servicio](backend/README.md), [tenants](tenants.md), [permisos](permisos.md), [ventas](ventas.md),
[panel-admin](panel-admin.md), [auditoría](auditoria.md), [observabilidad](observabilidad.md)) y del frontend actual.

Leyenda: **[listo]** el backend ya lo expone · **[plan]** está planificado y todavía no existe en el backend ·
☑ existía en el frontend anterior (Next.js, ya reemplazado) y se reescribió · **[x]** hecho en `frontend/` (revisado contra el código el
2026-11-04; lo que está a medias sigue sin marcar, con una nota de lo que falta).

---

## Stack

**Vite + React + TypeScript**, como SPA.

| Pieza | Elección | Por qué |
|---|---|---|
| Build | Vite | Solo se compila a archivos estáticos: no hace falta un servidor Node al lado de los servicios Java |
| Rutas | React Router (modo librería) | Estándar, alcanza para rutas anidadas y rutas protegidas |
| Datos | TanStack Query + axios | Caché, reintentos y estados de carga/error de los endpoints REST |
| UI | Tailwind + shadcn/ui (Radix) | Es lo que ya usa el frontend actual: los componentes de `components/ui` se copian tal cual |
| Formularios | react-hook-form + zod | Ya están en el proyecto |

**Por qué no Next.js:** el backend ya hace el trabajo de servidor (auth, datos, caché). El frontend actual casi no usa
Next: solo `next/link`, `next/navigation`, `next/font` y una ruta `/api/products` que es un proxy de una línea.
Next suma un runtime de Node que hay que desplegar y que no aporta nada al panel de administración.

**Lo único que una SPA no resuelve: las vistas previas al compartir** (WhatsApp, Instagram). El MVP prevé links
compartibles de productos (`/api/v1/catalog/products/{slug}`) con Open Graph, y los crawlers no ejecutan JavaScript.
Opciones, de menor a mayor esfuerzo:

1. Un endpoint chico en el backend (products) que, para el link de compartir, devuelva un HTML mínimo con las
   etiquetas `og:*` y redirija a la SPA. Es la recomendada: encaja con el stack Java y no cambia el frontend.
2. Prerender de las páginas del catálogo en el build.
3. Si más adelante importa el SEO del catálogo de verdad: pasar solo esa parte a **React Router v7 en modo
   framework** (SSR) o TanStack Start. Se evita decidirlo hoy.

**Alternativas descartadas:** Remix / React Router framework y TanStack Start son buenos, pero traen SSR que hoy no
se necesita; Astro es excelente para contenido y flojo para un panel tan interactivo.

### Cosas a resolver al arrancar

- **El tenant se reconoce por el header `Origin`**. Con un SPA el navegador lo manda solo; no hay que reenviarlo
  desde ningún servidor. (Con SSR habría que falsificarlo, otro motivo para evitarlo.)
- **Puertos en desarrollo:** el frontend corre en 2999 (Vite) y el gateway en 3000. `platform.domain` (users) es
  `localhost:2999` y `platform.url` (gateway) es `http://localhost:2999`, así que las tiendas se abren en
  `http://<tienda>.localhost:2999`. Los tenants creados antes de este cambio guardan su `url` con el puerto viejo.
- **Un solo build para la plataforma y las tiendas:** según el host, la app muestra la parte de plataforma
  (`localhost`) o la de tienda (`<tienda>.localhost`). Lo decide `lib/platform.ts` comparando el hostname con
  `VITE_PLATFORM_HOSTNAME` (por defecto `localhost`); `App.tsx` arma rutas distintas para cada uno.
- **Sesión del superadmin:** la plataforma no tiene `/auth/me` (es de las tiendas), así que al loguearse se guarda el
  usuario entero en `localStorage` (`platform_user`). Si el token vence, el primer 401 del panel cierra la sesión.
- **Token:** viene en el login y tiene el claim `tenant`; si se usa en otro tenant el gateway responde 401. Guardarlo
  por origen alcanza (el `localStorage` ya es por origen).

---

## Checklist

### 0. Base técnica
- [x] Proyecto Vite + TypeScript + Tailwind + shadcn/ui, en `frontend/`
- [x] Cliente HTTP único (baseURL del gateway, `Authorization`, manejo central de 401 / 403 / 404 / 429)
- [x] Detección de contexto: plataforma (sin subdominio) vs. tienda (con subdominio) ☑
- [x] Contexto de sesión: usuario, roles y **máscara de permisos** (bits 1/2/4/8/16), con helper `can('STOCK')`
- [x] Rutas protegidas por rol y por área de permiso (la UI oculta lo que no se puede usar; el backend igual valida)
- [x] Layouts: público, panel de la tienda, panel de la plataforma
- [x] Textos en español centralizados (hoy en `lib/strings.ts`), moneda en pesos, fechas locales
- [ ] Tema claro/oscuro, responsive (uso en tablet y celular en el local) — responsive está; falta el tema oscuro
  (no hay colores `.dark` ni selector)
- [ ] Estados vacíos, de carga y de error reutilizables; aviso de "demasiados intentos" para el 429 — el aviso del
  429 está; los estados vacíos y de carga se arman en cada página (no hay componentes comunes)
- [x] Páginas de tienda inexistente (404) y deshabilitada (403)
- [x] Variables de entorno (URL del gateway) y build para producción

### 1. Plataforma (sin tenant) **[listo]**
- [ ] Landing con explicación del producto
- [ ] **Alta de tienda** (`POST /platform/tenants`): identificador con la validación del backend (3–30 caracteres,
  `a-z 0-9 -`, reservados), datos del dueño, errores 400 / 409 / 429, y redirección a la URL de la tienda al terminar
  — hoy la tienda solo se crea desde el panel de tenants del superadmin; falta el alta pública (en la plataforma `/`
  redirige a `/dashboard`)
- [x] Login del superadmin (`/platform/auth/login`) ☑ en `http://localhost:2999/login`
- [x] Panel de tenants (`/tenants`): lista con búsqueda y filtro por estado ☑, alta ☑, habilitar/deshabilitar ☑, borrar
  (soft delete) con confirmación ☑; aviso de que el cambio tarda hasta 1 min ☑. El detalle no tiene pantalla propia:
  la tarjeta ya muestra todo lo que devuelve `GET /admin/tenants/{id}`
- [x] Resumen (`/dashboard`, a donde lleva el login): tenants (total / habilitados / deshabilitados / borrados) ☑ y
  altas por período en barras, con tooltip y vista de tabla ☑ (30 días por día, 12 semanas o 12 meses)
- [x] Uso por tenant en el resumen: tabla con usuarios, activos, última actividad, productos y combos ☑, orden
  elegible (por defecto productos) ☑. Junta users y products por `schema`; si products falla, la tabla sale igual
  sin esas columnas ☑
- [x] Gestión de superadmins (`/superadmins`): lista ☑, alta con contraseña temporal ☑, desactivar/reactivar ☑ (el
  propio no se puede desactivar; el último activo da 409)
- Las páginas de plataforma comparten `hooks/use-platform-session.ts` (redirige al login y cierra la sesión con el
  primer 401). Las fechas de la plataforma llegan con offset (`2026-11-02T21:00:16Z`): el backend corre en UTC.

### 2. Cuenta y sesión en la tienda **[listo]**
- [x] Login ☑ y registro como `CLIENTE` ☑
- [x] Recupero de contraseña: `/recuperar` pide el mail (link "¿Olvidaste tu contraseña?" en el login) y `/reset-password?token=`
  elige la nueva; si el link venció o ya se usó, ofrece pedir otro. Solo en las tiendas
- [x] Perfil: cambiar nombre y contraseña ☑, borrar la propia cuenta con confirmación
- [x] Sesión vencida: volver al login conservando la página de destino
- [x] Menú de usuario con los roles y logout

### 3. Catálogo público **[listo]**
- [x] Home (`/`) como vidriera corta: buscador y categorías que llevan al catálogo, **productos destacados**
  (`/catalog/featured`, en su orden) en una fila y los combos (con la misma tarjeta que `/combos`) en otra fila igual
- [x] Listado paginado (24 por página) en `/catalogo`, con búsqueda `q`, filtro por categoría y orden (nombre / precio ↑ /
  precio ↓) en la URL (`?q=&categoria=&orden=&pagina=`)
- [x] Categorías con su cantidad de productos
- [x] Detalle por **slug** (URL compartible), con imágenes, metadatos (material, color, estilo, medidas) ☑
- [x] Disponibilidad: `DISPONIBLE`, `POCAS_UNIDADES`, `AGOTADO` (el catálogo nunca muestra stock exacto ni costo)
- [x] Producto sin precio → "Consultar"
- [x] **Combos** en `/combos`: precio, precio de lista (ahorro), disponibilidad, productos que incluye (miniatura,
  precio suelto y cantidad, con link a cada producto) y agregar al carrito; la foto es un mosaico de los productos.
  Tocar la foto abre el detalle: cada producto con foto, categoría, descripción y subtotal, y la cuenta de lo que sale por
  separado contra el precio del combo
- [x] Botón para compartir el link (WhatsApp, copiar) y vista previa Open Graph (ver Stack)
- [x] Imágenes con carga diferida y tamaño controlado

### 4. Compra online **[listo]**
- [x] Carrito en el frontend (productos y combos, cantidades, persistido en el navegador)
- [x] Aviso si cambió un precio entre el carrito y el pedido
- [x] Checkout: datos del comprador (invitado o logueado), retiro o envío con dirección, notas
- [x] Pantalla del pedido: número (`K7QM-4X2P`), total, **datos para transferir** (alias, CBU, titular, banco) y vencimiento con cuenta regresiva
- [x] Subir comprobante (JPG / PNG / PDF, hasta 5 MB), con reintento si se rechaza
- [x] Consulta de pedido de invitado (email + número, por POST)
- [x] Cancelar el pedido mientras no esté validado el pago
- [x] "Mis pedidos" del cliente logueado, con paginación
- [x] Estados del pedido legibles (pendiente, en revisión, rechazado con motivo, pagado, en preparación, listo, entregado, cancelado, vencido)
- [x] Sin datos de cobro, el checkout avisa que la tienda no vende online; al `ADMINISTRADOR` le ofrece cargarlos

### 5. Panel de la tienda: productos **[listo]**
Visible según el permiso de cada área; el `ADMINISTRADOR` ve todo.
- [x] Lista de productos con búsqueda y filtros ☑ (con stock exacto)
- [x] Alta y edición: nombre, categoría, descripción, stock mínimo ☑ (el stock inicial queda como movimiento de entrada)
- [x] Borrar producto con confirmación ☑
- [x] Imágenes: subir varias (hasta 1 MB cada una, con validación en el cliente), ver y borrar
- [x] Metadatos: material, color, estilo, alto, ancho, largo, peso
- [x] Destacados: marcar/desmarcar y elegir el orden
- [ ] **Precios** (`PRECIOS`): precio, costo, margen y margen %; sin el permiso, esos campos no se muestran — precio
  y costo están (y se ocultan sin el permiso); falta mostrar `margin` y `marginPercent`, que ya vienen en
  `GET /products/{id}/pricing`
- [x] **Stock** (`STOCK`): registrar entrada / salida / ajuste con motivo, historial por producto, error 409 por stock insuficiente
- [x] Alerta de **stock bajo** (listado y contador en el menú)
- [x] **Importar CSV** (`PRODUCTOS` + `PRECIOS`): descargar plantilla, subir archivo, mostrar `{created, updated}` o la **tabla de errores por fila**; límites de 5.000 filas / 5 MB
- [x] **Exportar CSV**
- [x] **Combos** (`COMBOS`): crear el combo completo en un solo paso (nombre, productos con imagen, precio, cantidad y
  subtotal, y al final el precio, con la suma de los productos a la vista), habilitar/deshabilitar, cambiar precio
  (precio fijo, % de descuento o $ de descuento sobre la suma, con el precio final y el ahorro a la vista),
  agregar / quitar productos y cambiar cantidades, borrar. Cada combo muestra miniaturas de sus productos y los input
  numéricos no tienen flechas (`no-spinner` en `index.css`)

### 6. Panel de la tienda: usuarios y permisos **[listo]** (solo `ADMINISTRADOR`)
- [x] Lista de usuarios con sus roles ☑
- [x] Crear usuario con roles ☑, editar nombre o contraseña ☑, borrar ☑
- [x] Asignar y quitar roles (`SUPERADMIN` no se ofrece)
- [x] **Matriz de permisos por rol**: casillas de `PRODUCTOS`, `PRECIOS`, `STOCK`, `COMBOS` para `EMPLEADO` y `CLIENTE`; `USUARIOS` bloqueado; `ADMINISTRADOR` solo lectura; aviso de que products tarda hasta 1 min en aplicarlo

### 7. Panel de la tienda: resumen **[listo en el backend: panel-admin]** (`GET /products/dashboard` y `GET /users/dashboard`)
- [x] Dashboard de productos: total, por categoría, sin precio, sin imágenes, destacados
- [x] Dashboard de stock: stock bajo, agotados, valor del inventario a costo y a precio (con los excluidos), últimos 10 movimientos
- [x] Dashboard de usuarios: total, activos (30 días), por rol, altas por período (`from`, `to`, `groupBy`)
- [x] Cada bloque carga por separado: si un servicio falla, el resto del panel sigue
- [x] Gráficos con períodos en cero incluidos

### 8. Panel de la tienda: ventas **[listo]** (solo `ADMINISTRADOR`)
- [x] Lista de pedidos en `/admin/orders` con filtros (estado, canal, medio de pago, "stock para revisar", fechas) y
  búsqueda por número o email, en la URL; atajo a los comprobantes para revisar y contador en el menú ("Ventas")
- [x] Detalle del pedido en `/admin/orders/{número}`: ítems con precio de lista, cobrado y costo, ganancia, comprador,
  entrega, historial con el nombre de quien hizo cada cambio y comprobantes (ver en un diálogo o descargar)
- [x] Cambios de estado según las transiciones válidas: validar / rechazar con motivo, preparar, listo, entregar, cancelar con motivo
- [x] **Venta del local (mostrador)** en `/admin/orders/new`: buscar productos y combos, cantidad y precio cobrado por
  ítem (obligatorio para los "a consultar"), descuento, medio de pago, estado inicial (entregado o pagado), comprador
  opcional, retiro o envío y notas
- [x] Avisos de venta sin stock suficiente: al cargar la venta (se vende igual con un ajuste), al registrarla y en la
  lista y el detalle del pedido
- [x] Configuración de ventas: alias, CBU, titular, banco y plazo de vencimiento ("Datos de cobro" en la lista de
  pedidos, que avisa si faltan)
- [x] Reportes en `/admin/reports` (link desde la lista de pedidos y el resumen de `/admin`): rango de fechas,
  agrupado por día, semana o mes, canal y medio de pago; facturado, ventas, unidades, ticket promedio, costo,
  ganancia y descuentos, gráfico por período, desglose por canal y por medio de pago, y ranking de productos y combos
  por unidades, facturado o ganancia
- [ ] Contador de vendidos en productos y orden "más vendidos" en el catálogo — falta: el frontend no muestra
  `soldCount` y el orden del catálogo no ofrece `best_sellers`

### 9. Auditoría y observabilidad **[hecho]**
- [x] Panel de la plataforma: `/estado` con la salud de los servicios (instancias, componentes, circuit breakers), tráfico y recursos por servicio, un gráfico de los últimos minutos, conectados por tienda y movimientos de tiendas (`GET /platform/observability/overview` + `platform.health`, `platform.traffic` y `platform.business`, que además refresca el resumen)
- [x] Panel de la plataforma: `/auditoria` con la auditoría de la plataforma (`GET /platform/observability/audit` + `platform.audit`)
- [x] Panel de la tienda: `/admin/auditoria` con el historial filtrable (fechas, qué, acción y usuario), el detalle de cada cambio y lo nuevo en vivo (`GET /observability/store/audit` + `store.audit`)
- [x] Panel de la tienda: `/admin/actividad` con la actividad en vivo (pedidos, ventas del local, comprobantes, movimientos y stock bajo, con aviso y refresco de los contadores del menú), el uso del día y los conectados (`store.activity`, `store.usage` y `store.users`)
- [x] Exportar la auditoría a CSV con los filtros puestos (`/store/audit/export`, `/audit/export`)
- [x] Cliente WebSocket (`hooks/use-live-channels.ts`): ticket de un solo uso, `/ws?ticket=`, ping cada 30 s, reconexión con ticket nuevo y espera creciente; no reintenta con 403 ni con el cierre 1008
- [x] Historial por REST al abrir el panel; el WS solo trae lo nuevo
- [ ] Historial de una cosa puntual dentro de su pantalla (pedido, producto, usuario) con `GET /observability/store/audit/{entity}/{id}`

### 10. Calidad
- [ ] Tipos de TypeScript de todas las respuestas del backend — el chequeo de tipos ya corre en el build
  (`tsc -b && vite build`); falta revisar que los tipos cubran todas las respuestas
- [ ] Tests de las reglas de permisos del cliente y de los flujos principales (login, alta de tienda, carga de producto)
- [ ] Accesibilidad básica: foco, etiquetas, contraste
- [x] Pipeline de build y despliegue de los archivos estáticos: un push a `deploy/frontend` compila la imagen (`tsc -b` y
  `vite build`, servido por nginx) y la despliega ([deploy.md](../deploy.md)). Falta un CI que corra el build en cada push a `main`

---

## Orden sugerido

1. **Base técnica** (0) y **cuenta** (2).
2. **Panel de productos y usuarios** (5, 6): es lo que ya existe en el frontend actual y en el backend.
3. **Catálogo público** (3) y **alta de tienda / panel de plataforma** (1).
4. **Resumen** (7) cuando salga ese endpoint del backend.
5. **Compra online y ventas** (4, 8) siguiendo las etapas del plan de ventas.
6. **Auditoría y observabilidad** (9) al final.

La parte del frontend actual que se aprovecha: los componentes de `components/ui`, los textos de `lib/strings.ts`,
los tipos de `lib/types.ts` y la lógica de `lib/auth-context.tsx`.
