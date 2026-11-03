# Pendientes

_Última actualización: 2026-11-03 (después del panel de la tienda, los canales `store.*` y `docs/backend/`)_

Lista de lo que queda por hacer, armada comparando la documentación ([MVP.md](../MVP.md), `docs/` y la referencia
por servicio en [docs/backend/](backend/README.md)) con el código. Cada tarea tiene el flujo previsto para
implementarla.

**Resumen:** el backend de los dos paneles, las ventas, la observabilidad (también los canales de la tienda) y la
auditoría está hecho. Lo que más falta es el **frontend**, que hoy cubre login y registro, catálogo básico, CRUD de
productos y usuarios, ajustes y el panel de la plataforma. En el backend quedan algunos ajustes de plataforma, la
vista previa al compartir y la limpieza de endpoints. La **gestión presencial** todavía no tiene decisiones tomadas.

### Resuelto desde la versión anterior

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

---

## A. Backend

### A1. Ajustes de plataforma

- **Pedidos y facturación de toda la plataforma** en el panel del superadmin:
  1. sales: endpoint de plataforma (`/api/v1/platform/sales/...`, sin tenant, solo `SUPERADMIN`) que recorre las
     tiendas y suma pedidos y facturación por período.
  2. Cachearlo como el uso por tienda (5 minutos en Redis), porque recorrer todos los schemas es caro.
  3. Sumarlo al dashboard de la plataforma o a `/platform/observability/overview`.
- **Períodos de altas en UTC**: una tienda creada después de las 21 (hora argentina) cae en el día siguiente.
  Aplicar una zona configurable en `util/Periods.java`, como `SalesTime` en sales.
- **"Activos" sigue siendo el último login en 30 días**: un usuario con la sesión abierta hace más de 30 días cuenta
  como inactivo. Las keys `activity:*` duran 5 minutos, así que haría falta guardar la última request en la base
  (por ejemplo, actualizarla como mucho una vez por hora).

### A2. Vista previa al compartir (Open Graph)

No está implementada. MVP.md todavía dice que la arma el frontend con Next.js `generateMetadata`, que ya no aplica.

1. products: endpoint público `/share/products/{slug}` que devuelve un HTML mínimo con las etiquetas `og:*` y
   redirige a la SPA.
2. Agregarlo como ruta pública en el gateway y cachearlo con la versión del catálogo.
3. El botón de compartir del frontend apunta a esa URL.

### A3. Limpiar endpoints

Ya no exponen el stock al público, pero quedan endpoints sin uso o con reemplazo (secciones "Para limpiar" de
[backend/products.md](backend/products.md) y [backend/users.md](backend/users.md)):

- products: `GET /products/all` (usar el paginado), `/search?name=` (usar `?q=`), `/products/featured` (filtrar en el
  listado) y `PUT /bundles/{id}`, que alterna sin decir a qué estado (pasar a `PUT /bundles/{id}/enabled`).
- users: `GET /users/all`, `/api/internal/tenants/all` y `/api/internal/users/all` sin uso; `PUT /auth/update` recibe
  `email` y lo ignora (pasar a `PATCH /auth/me`); `DELETE /auth/delete` deja borrarse al dueño (ver D).

Flujo: confirmar con un grep en `frontend/src` que no se usan, borrar, sacar las rutas del gateway y actualizar
`docs/backend/`. Los que usa el frontend (`/auth/update`) se cambian junto con la página.

---

## B. Frontend

Sigue el orden de [frontend.md](frontend.md). Lo de abajo es lo que no existe en `frontend/src`. El backend de todo
esto ya está.

### B0. Base técnica

Va primero porque todo lo demás depende de esto. Hoy cada componente arma su propio axios con `VITE_API_BASE_PUBLIC`.

1. ~~`lib/api.ts` con una instancia única, el interceptor de `Authorization` y el manejo central del 401~~
   **Hecho**: todas las llamadas pasan por `api`; un 401 de una llamada con token cierra la sesión y vuelve a
   `/login?next=<página>`; la cookie dura lo mismo que el token. Quedan para el paso 3 el 403/404 de tienda y el
   429 como estado global (hoy se muestran como mensaje de error de cada pantalla).
2. Instalar TanStack Query y migrar las páginas existentes.
3. Páginas de "tienda inexistente" (404) y "tienda deshabilitada" (403), y el aviso de "demasiados intentos".
4. Rutas protegidas por rol y área usando `hasPermission`.

### B1. Cuenta

- Recupero de contraseña: pedir el mail con `POST /auth/forgot-password` y la página `/reset-password?token=`, que
  es a donde lleva el mail, con el `POST /auth/forgot-password/{token}`. El token vale 15 minutos.
- Borrar la propia cuenta con confirmación (`DELETE /auth/delete`), después de resolver lo del dueño (ver D).
- Sesión vencida: volver al login y después a la página de destino. No hay refresh: el token dura una hora.

### B2. Catálogo público

- Destacados en la home con `GET /api/v1/catalog/featured`.
- Combos: listado y detalle (`/catalog/bundles`), con el ahorro frente al precio de lista.
- Compartir por WhatsApp y copiar el link (depende de A2).

### B3. Panel de productos

Ya están el alta y la edición, imágenes, metadatos y precios en el modal. Falta:

1. Movimientos de stock: modal para entrada, salida o ajuste, historial por producto y el 409 por stock insuficiente.
2. Stock bajo: listado y contador en el menú.
3. Destacados: marcar y ordenar.
4. Importar y exportar CSV: plantilla, subida y tabla de errores por fila.
5. Combos: página propia con crear, habilitar, cambiar precio, agregar y quitar productos, y borrar. Ahora
   `GET /bundles/**` pide un área del panel.

### B4. Usuarios y permisos

- Asignar y quitar roles.
- **Matriz de permisos** (`GET`/`PUT /users/roles/.../permissions`): `USUARIOS` bloqueado, `ADMINISTRADOR` solo
  lectura y un aviso de que el cambio tarda hasta 1 minuto.

### B5. Resumen de la tienda

Reemplaza `admin-dashboard.tsx`, que hoy solo cuenta productos y usuarios.

1. Tres bloques que cargan por separado: productos y stock (`GET /products/dashboard`) y usuarios
   (`GET /users/dashboard`, con `from`, `to` y `groupBy`). Si un servicio falla, el resto sigue.
2. Gráficos que incluyen los períodos en cero, con el mismo estilo que el de altas de la plataforma.
3. Ventas: sumar los reportes de `/api/v1/sales/admin/reports/**`.
4. Tiempo real: actividad, uso y conectados (ver B8).

### B6. Compra online

1. Carrito en `localStorage`, con productos y combos.
2. Checkout como invitado o con sesión, con retiro o envío.
3. Manejar el 409 cuando cambió un precio, mostrando los precios nuevos.
4. Pantalla del pedido: número, total, datos para transferir y cuenta regresiva.
5. Subir el comprobante.
6. Consulta del invitado con email y número.
7. Cancelar.
8. "Mis pedidos".

### B7. Panel de ventas (solo `ADMINISTRADOR`)

1. Lista de pedidos con filtros.
2. Detalle con historial y comprobantes.
3. Botones de transición según la máquina de estados de [ventas.md](ventas.md).
4. Venta del local (mostrador).
5. Configuración de ventas: alias, CBU y plazo.
6. Reportes: período, ranking, ganancia, canal y medio de pago.

### B8. Observabilidad y auditoría

1. Hook `useLiveChannel`: pide un ticket (un solo uso, 30 s), conecta a `/ws?ticket=`, manda ping cada 30 s,
   reconecta con un ticket nuevo y no reintenta si el ticket responde 403.
2. Plataforma: salud y tráfico (`overview` más `platform.health` y `platform.traffic`), auditoría
   (`platform.audit`) y refresco del resumen con `platform.business`.
3. Tienda: feed de auditoría (`store.audit`), actividad en vivo (`store.activity`), uso del día (`GET /store/usage`
   más `store.usage`) y conectados (`GET /store/users` más `store.users`).
4. El historial se carga por REST al abrir el panel y el WS solo trae lo nuevo.

### B9. Calidad

- Tipos de todas las respuestas (las fichas de `docs/backend/` tienen los ejemplos).
- Tests de permisos y de los flujos principales.
- Accesibilidad básica.
- Pipeline de build.

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

**Errores y reglas**

- **El dueño se puede borrar a sí mismo** (`DELETE /auth/delete`) o sacarse el rol y dejar la tienda sin
  administrador. No permitirlo al último `ADMINISTRADOR`, como ya se hace con el último superadmin.
- **500 en vez de 4xx en products**: crear un producto con el nombre de uno borrado o renombrar con un nombre usado;
  agregar a un combo un producto inexistente. Y `GET`/`PUT`/`DELETE /bundles/{id}` responden 200 aunque no exista.
- **`PUT /products/{id}` reemplaza los cuatro campos**: mandar solo uno borra los otros. Pasar a `PATCH` o documentar
  que van todos.
- **Imágenes**: se guardan siempre como `.png` y no se valida que sean imágenes (solo el tamaño).
- **Respuestas inconsistentes**: las altas responden 200 en vez de 201; un email repetido da 400 en las tiendas y 409
  en los superadmins; un rol inexistente da 400 o 404 según el endpoint.
- **El mail del recupero puede fallar sin aviso.**

**Seguridad**

- **Rate limit esquivable con `X-Forwarded-For`**, y es por instancia: confiar en el header solo detrás de un proxy
  propio y, si no, usar la IP de la conexión.
- **`/api/internal/**` de users** no pide nada si se llama directo al puerto 3001 sin `X-internal-request`. Validar
  el token interno siempre, sin depender de que el puerto no esté expuesto.
- **Sin refresh token**: a la hora hay que volver a loguearse.

**Rendimiento y tests**

- **users migra el schema en cada conexión**: `TenantDataSource.prepareSchema` llama a `createSchema` y eso corre
  `runMigration` siempre. Hacer como products: migrar solo si el schema no existe, más todos al arrancar.
- **Tests rotos**: `ProductServiceTest.deleteProductById_*` mockea `existsById` y el servicio usa `findById`; los
  `contextLoads` necesitan Postgres. Conviene **Testcontainers**.
- **Prueba de punta a punta con Docker**: todo junto, con Postgres y Redis reales.

**Para decidir**

- Observabilidad: muestreo de trazas, alertas en Grafana, retención de métricas, trazas y logs, healthchecks con
  `/ready` e historial de uso más allá de 8 días.
- Auditoría: exportar el historial a CSV y particionar `audit_events`.
- Eureka con una sola instancia, sin réplica.

**Documentación y repo**

- `TODO` todavía dice que ventas "no entra".
- [frontend.md](frontend.md) tiene todo sin tildar y habla del "frontend actual (Next.js)".
- MVP.md menciona Next.js `generateMetadata` para Open Graph.
- El README "sin Docker" no lista sales ni observability en el orden de arranque.
- Archivo suelto `backend/eureka/nul`, sin trackear: es basura de una redirección en Windows y se puede borrar.

---

## Orden sugerido

1. **B0 y B1**: la base del frontend, así lo demás no se repite. El paso 0 (CORS del gateway) ya está.
2. **B3 y B4**: lo que el backend ya tiene listo y lo que más usa el dueño.
3. **B5**: el panel de la tienda, que ya tiene todo el backend.
4. **B2 y A2**: catálogo y compartir. Después, la limpieza de endpoints (A3).
5. **B6 y B7**: compra online y panel de ventas.
6. **B8 y A1**: tiempo real en los paneles y los ajustes de plataforma.
7. **Decidir y planificar C1**, la próxima etapa de ventas.
8. La deuda técnica (D) se intercala. Lo más barato y útil: el dueño que se borra a sí mismo, los tests y la
   migración de users.
