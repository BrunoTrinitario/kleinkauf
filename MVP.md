# Decor — Análisis y propuesta de MVP

_Última actualización: 2026-11-01_

Objetivos del proyecto:
- Probar tecnologías y arquitecturas modernas: microservicios, caché distribuida, multi-tenant.
- Construir un software libre de administración para pequeños emprendimientos.

---

## 1. Estado actual

### Arquitectura
- **Gateway** (Spring Cloud Gateway): punto de entrada. Valida JWT, tenant, CORS y rate limit.
- **decorusers**: auth (login, registro, refresh, recupero de contraseña), usuarios, roles
  (`ADMINISTRADOR` / `EMPLEADO` / `CLIENTE`) y estadísticas de usuarios.
- **product**: productos con imágenes y metadatos (material, color, medidas), combos (bundles), búsqueda,
  **precio y costo**, **movimientos de stock** con alerta de stock bajo y **productos destacados**.
- **decor-common**: librería común (DTOs, headers, Redis, tokens internos, multi-tenant de Hibernate, filtros).
- **Multi-tenant por schema** en Postgres.
- **Caché en dos niveles**: Caffeine (local) + Redis (distribuido).
- **Comunicación entre servicios** con token interno firmado aparte (`/api/internal/**`), protegida con
  **Resilience4j** (circuit breaker, reintentos y timeouts).

### Faltantes detectados
1. ~~**Los productos no tienen precio.**~~ **Resuelto:** precio y costo en productos y combos (punto 1).
2. ~~**El stock es un número que se sobreescribe** con `PUT`.~~ **Resuelto:** el stock solo cambia con movimientos (punto 2).
3. ~~**No hay alta de tenants.**~~ **Resuelto:** alta por autoservicio y panel del superadmin (punto 5).
4. ~~**El campo `permission` de `Role` no se usa.**~~ **Resuelto:** permisos por área configurables por rol en cada tienda.

---

## 2. Funcionalidades propuestas para el MVP

Ordenadas por prioridad sugerida. Marcar la decisión en cada una.

| # | Funcionalidad | Qué agrega | Tecnología a probar | Decisión |
|---|---|---|---|---|
| 1 | **Precio y costo** en productos y combos | Calcula el margen. Sin esto no sirve para vender | — | ☑ Sí — **hecho** |
| 2 | **Movimientos de stock** (entrada / salida / ajuste, con motivo y usuario) + **alerta de stock mínimo** | Trazabilidad del inventario | Eventos (Redis Streams / RabbitMQ) — pendiente para cuando exista ventas | ☑ Sí — **hecho** (sin eventos todavía) |
| 3 | **Ventas**: carrito, pedido, pago por transferencia con comprobante y validación del administrador | Uso diario del negocio | **MS de ventas** que publica `VentaRegistrada` y productos descuenta stock (outbox + mensajería) | ☑ Sí — **en curso** (plan en [docs/ventas.md](docs/ventas.md)) |
| 4 | **Panel de administración**: ventas del día y del mes, más vendidos, stock bajo, usuarios | Vista del dueño | Agregación entre servicios, caché de reportes | ☐ Sí ☐ No ☐ Después |
| 5 | **Alta de tenant (onboarding)**: registrarse y que se cree el schema solo | Autoservicio real | Flyway / Liquibase por schema | ☑ Sí — **hecho** (autoservicio + superadmin; schemas con `hbm2ddl`, sin Flyway) |
| 6 | **Catálogo público** con link para compartir (WhatsApp / Instagram) | Muy útil para emprendimientos chicos. Las imágenes ya están | Endpoint público cacheable | ☑ Sí — **hecho** (API; las vistas previas Open Graph las arma el frontend) |
| 7 | **Auditoría** (quién creó o modificó cada cosa) | Control sobre los empleados | Outbox + Redis Streams hacia un MS de observabilidad | ☑ Sí — **planificado** ([docs/auditoria.md](docs/auditoria.md)) |
| 8 | **Importar y exportar productos** (CSV / Excel) | Carga inicial rápida | — | ☑ Sí — **hecho** (solo CSV) |
| 9 | **Productos destacados** (agregado después) | Elegir qué productos se muestran en la home y en qué orden. Suma al catálogo público (punto 6) | — | ☑ Sí — **hecho** |

> **Sobre ventas:** el `TODO` marca "MS ventas y logística" como **no entra**. Se recomienda incluir al menos
> una versión mínima de ventas (punto 3). Sin ventas el sistema queda como un catálogo, y además es lo que
> mejor muestra el valor de los microservicios: eventos, consistencia eventual y desacoplamiento.
> La logística sí puede quedar afuera.

---

## 3. Mejoras técnicas

| Mejora | Para qué | Decisión |
|---|---|---|
| **Resilience4j** (circuit breaker + reintentos) | Proteger las llamadas de productos a usuarios, que hoy usan `RestTemplate` sin protección | ☑ Sí — **hecho** |
| **Observabilidad**: Micrometer + OpenTelemetry + Grafana / Tempo | Seguir una request a través de los servicios | ☑ Sí — **planificado** ([docs/observabilidad.md](docs/observabilidad.md)): Actuator, OpenTelemetry, Prometheus, Grafana, Tempo, Loki y panel en tiempo real por WS |
| **Testcontainers** | Tests de integración con Postgres y Redis reales | ☐ Sí ☐ No ☐ Después |
| **Librería común** | Centralizar lo repetido en los tres servicios: `JwtUtil`, `CacheUtil`, filtros, DTOs de tenant y rol | ☑ Sí — **hecho** (`decor-common`) |
| **Definir el uso de `permission`** en roles | Permisos más finos que el rol (por ejemplo, un empleado que puede ver pero no editar precios) | ☑ Sí — **hecho** (permisos por área y por rol) |

---

## 4. Orden sugerido

1. ~~**Precios** (punto 1)~~ — **hecho**.
2. ~~**Movimientos de stock** (punto 2)~~ — **hecho**.
3. **Ventas** (punto 3) — **en curso**, en 4 etapas: ver [docs/ventas.md](docs/ventas.md). El pedido reserva stock
   (`SALIDA` por cada producto o por cada producto del combo) y lo devuelve si se cancela o vence.
4. **Panel de administración** (punto 4) con los datos de ventas y stock.
5. ~~**Onboarding de tenants** (punto 5)~~ — **hecho**.

---

## 5. Decisiones tomadas

<!-- Completar a medida que se decida -->

| Fecha | Decisión | Motivo |
|---|---|---|
| 2026-11-01 | Antes de sumar funcionalidades: **librería común** y **Resilience4j** | Ordenar la base antes de agregar servicios nuevos (ventas, etc.) |
| 2026-11-01 | Librería como **build compuesto de Gradle** (`decor-common`, con `includeBuild`) en vez de un build multi-proyecto en la raíz | Cada servicio sigue siendo un proyecto Gradle independiente; no hay que publicar nada |
| 2026-11-01 | `decor-common` es **un solo proyecto**: lo común en `com.decor.common` y lo servlet en `com.decor.common.servlet` | Más simple de manejar en Eclipse. Las dependencias de Spring son `compileOnly` y la parte servlet no se activa en el gateway (reactivo) |
| 2026-11-01 | La librería se configura sola con **autoconfiguración de Spring Boot** | Los servicios solo agregan la dependencia; cada bean se puede reemplazar |
| 2026-11-01 | Resilience4j en products → users y gateway → users (circuit breaker + retry + timeouts). Los 4xx no cuentan como falla | Si users se cae, el resto responde rápido con caché o con un error claro, sin quedarse colgado |
| 2026-11-01 | Precio y costo en `BigDecimal`. El **precio es público**; el **costo y el margen solo los ve el administrador** (`/products/{id}/pricing`) | Evitar errores de redondeo con plata y no exponer el costo en el catálogo |
| 2026-11-01 | El stock **solo cambia con movimientos** (`ENTRADA`, `SALIDA`, `AJUSTE`); `PUT /products/{id}` ya no lo modifica | Trazabilidad: quién cambió el stock, cuándo y por qué |
| 2026-11-01 | Los movimientos los registran administrador y empleado; bloqueo de fila (`PESSIMISTIC_WRITE`) por producto | El empleado es quien recibe mercadería; el bloqueo evita que dos movimientos simultáneos se pisen |
| 2026-11-01 | El combo tiene su propio precio; muestra además el precio de lista (suma de productos) y cuántos se pueden armar con el stock | El combo no tiene stock propio: depende del de sus productos |
| 2026-11-01 | **Productos destacados**: `featured` + `featuredOrder` opcional. La lista es pública; marcar/desmarcar es solo del administrador | Mostrar productos elegidos en la home del catálogo, en el orden que quiera el negocio |
| 2026-11-01 | **Alta de tenants** por autoservicio público y por un **superadmin**; toda la lógica en decorusers | Que un emprendimiento pueda registrarse solo y que la plataforma pueda administrar los tenants |
| 2026-11-01 | URL del tenant = **subdominio automático** `<identificador>.<platform.domain>` (dev: `http://mitienda.localhost:3000`). El identificador lo elige el usuario: 3–30 caracteres, empieza con letra, `a-z 0-9 -`, único y no reservado; el schema usa `_` | El gateway reconoce al tenant por la URL; la validación estricta evita inyección SQL en `CREATE SCHEMA` |
| 2026-11-01 | El alta crea al **dueño como ADMINISTRADOR y EMPLEADO**; el tenant queda **activo al instante**; devuelve los datos y la URL (sin token) | Sin dueño nadie podría administrar el tenant; el token queda atado al tenant y se loguea en su subdominio |
| 2026-11-01 | `configId` = UUID generado en el alta | Referencia a la configuración propia de cada tenant (próximamente) |
| 2026-11-01 | **Superadmin** en el schema `public` con rol `SUPERADMIN` (no asignable desde una tienda); el primero se crea al arrancar con `SUPERADMIN_EMAIL` / `SUPERADMIN_PASSWORD`. Puede crear, listar, habilitar/deshabilitar y borrar (soft delete) tenants | Usuarios de plataforma separados de los de cada tienda |
| 2026-11-01 | Tenant **deshabilitado → 403** en el gateway; **borrado → 404** (no existe para la app, el identificador queda reservado) | La decisión la toma users; el gateway solo lee el flag que ya recibe |
| 2026-11-01 | Schemas: users al dar de alta, products al primer uso | Products ya crea el schema solo; no hace falta acoplar los servicios |
| 2026-11-01 | **JWT atado al tenant** (claim `tenant`; `public` para el superadmin) y validado en el gateway; también el token de reset de contraseña | Los ids de usuario se repiten entre tenants: un token de una tienda servía en otra |
| 2026-11-01 | El gateway **borra los headers internos** (`X-User-Id`, `X-Forwarded-Host`, `X-Open-Path`, `X-Tenant-Name`) que mande el cliente | Los servicios confían en esos headers |
| 2026-11-01 | **Rate limit** arreglado (ventanas de 1 minuto): login 5, alta de tenants 3, global 300 (sin contar imágenes) por IP y origen. La IP sale de `X-Forwarded-For` | Frenar abuso del alta pública sin cortar la navegación del catálogo |
| 2026-11-01 | **Caché de tenants del gateway por URL con TTL de 1 minuto** (antes nunca acertaba) | Menos llamadas a users; deshabilitar o borrar tarda hasta 1 minuto en aplicarse |
| 2026-11-01 | **Importar/exportar productos solo en CSV**, solo administrador (el archivo incluye el costo) | Sin dependencias pesadas; Excel lo abre igual |
| 2026-11-01 | Columnas: básicas, precio, costo y metadatos (**sin stock**: se maneja con movimientos). Encabezados en español | Que el dueño entienda el archivo y que el stock siga teniendo trazabilidad |
| 2026-11-01 | Formato: `;`, UTF-8 con BOM y **números en formato argentino** (`15.000,50`); un formato ambiguo como `15.5` es error. Apache Commons CSV | Excel en español lo abre bien con doble clic; nadie carga 15 queriendo 15.000 |
| 2026-11-01 | Importar: el **nombre identifica al producto**; si existe se actualiza y **una celda vacía no cambia el dato**; si no existe se crea con stock 0. **Todo o nada**, con errores por fila. Columnas desconocidas = error | Sirve para cargar el catálogo y para actualizar precios en masa sin pisar datos |
| 2026-11-01 | Límites: 5.000 filas y 5 MB. El límite general de subida pasa a 5 MB y **las imágenes siguen en 1 MB** (validación propia); archivo más grande que el límite → 413 | Evitar que un archivo enorme bloquee el servicio |
| 2026-11-01 | **Catálogo público** en `/api/v1/catalog` (paginado, búsqueda, categoría, orden por nombre o precio, detalle por slug y categorías). Los endpoints públicos viejos quedan por ahora | El frontend se va a reescribir sobre el catálogo nuevo; después se cierran los viejos |
| 2026-11-01 | El catálogo **no muestra el stock exacto**: `DISPONIBLE`, `POCAS_UNIDADES` (stock ≤ mínimo) o `AGOTADO`. Sin precio = "consultar" (`price: null`), al final al ordenar por precio | No exponer el inventario; muebles a medida sin precio fijo |
| 2026-11-01 | **Slug fijo** por producto (se genera al crear, no cambia con el nombre; si se repite, `-2`). Los productos existentes lo reciben al migrar el schema del tenant | Los links compartidos no se rompen |
| 2026-11-01 | Búsqueda sin distinguir mayúsculas pero **sí acentos** (sin `unaccent`). 24 por página, máximo 100. Sin combos en el catálogo por ahora | Simple, sin extensiones de Postgres |
| 2026-11-01 | **Caché del catálogo en Redis** con versión por tenant (cada cambio de producto, precio, stock, imágenes o importación la sube después del commit) y TTL de 1 hora; si Redis falla se lee de la base | Probar caché distribuida sin mostrar datos viejos |
| 2026-11-01 | **Permisos por área, por rol y configurables por tienda**: PRODUCTOS=1, PRECIOS=2, STOCK=4, COMBOS=8, USUARIOS=16 (máscara de bits, también en la API) | Más fino que el rol sin la complejidad de permisos por usuario o roles propios |
| 2026-11-01 | La máscara vive en una **tabla nueva por tienda** (`role_permissions`, una fila por rol); se elimina `roles.permission` (int por usuario, siempre 0) | Una sola fuente por rol; la columna vieja era `NOT NULL` y rompía las altas al sacarla de la entidad |
| 2026-11-01 | **ADMINISTRADOR siempre tiene todo** y es el único que configura; EMPLEADO y CLIENTE son configurables; **USUARIOS no se delega** | Que nadie se quede afuera ni pueda darse el rol ADMINISTRADOR |
| 2026-11-01 | Por defecto igual que antes: EMPLEADO = STOCK, CLIENTE = nada | No cambiar nada hasta que el administrador lo configure |
| 2026-11-01 | Importar/exportar CSV pide PRODUCTOS **y** PRECIOS. Crear un producto sin PRECIOS **ignora** precio y costo | El archivo trae costo; el alta no debe saltear el permiso de precios |
| 2026-11-01 | Propagación: al cambiar permisos se borran de Redis los roles cacheados de la tienda y products cachea roles 1 minuto | El cambio se aplica en menos de 1 minuto |
| 2026-11-01 | **Ventas en un MS nuevo `decor-sales`**, comprador invitado o registrado, carrito en el frontend, productos y combos | Desacoplar ventas de productos y practicar comunicación entre servicios |
| 2026-11-01 | Pago por **transferencia con comprobante** que valida **solo el administrador**; Mercado Pago después | Lo más simple que sirve hoy a un emprendimiento chico |
| 2026-11-01 | El pedido **reserva stock al crearse** (llamada síncrona e idempotente a products) y lo devuelve si se cancela o **vence a las 48 h** sin comprobante. Eventos después | No vender dos veces lo mismo mientras se espera la transferencia |
| 2026-11-01 | Cuenta como venta **al validar el pago**. Reportes por período, por producto, ganancia (costo guardado al vender) y contador `vendidos` solo en el panel | Medir lo vendido y lo generado sin contar pedidos que no se pagan |
| 2026-11-01 | El invitado sigue su pedido con **email + número aleatorio** (`K7QM-4X2P`) | Un número correlativo se podría recorrer y exponer datos de otros compradores |
| 2026-11-01 | **Observabilidad**: Actuator con `/health`, `/ready` y `/metrics` en un puerto de gestión interno; OpenTelemetry + Prometheus + Grafana + Tempo + Loki; MS nuevo `decor-observability` con panel de plataforma y de tienda en tiempo real por **WebSocket puro** con ticket corto | Saber cómo está la plataforma y cada tienda en vivo, sin exponer métricas afuera |
| 2026-11-01 | **Auditoría** de cambios de datos, acciones de plataforma y lecturas sensibles, con antes/después de los campos cambiados, por **outbox** hacia observability; se guarda **para siempre** y cada uno ve lo suyo (admin su tienda, superadmin la plataforma) | Control sobre lo que hace cada usuario sin perder eventos ni mezclar datos de tiendas |

### Detalle de la librería común

- **`com.decor.common`** (todos los servicios): `TenantDto`, `RoleDto`, `Headers`, `RedisTemplate`, caché Caffeine e
  `InternalTokenService` (tokens entre servicios).
- **`com.decor.common.servlet`** (users, products): Hibernate multi-tenant por schema, `TenantFilter`, `TenantContext`,
  `CustomUserDetails`, `ApiException` y `GlobalExceptionHandler`.
- Cada servicio implementa `TenantSchemaManager` en su `TenantDataSource`.
- En users el tenant es opcional: `decor.tenant.required=false`.

### Pendientes detectados en el camino

- ~~**CORS del gateway bloquea el event loop**~~ **Resuelto:** `CorsConfig` ahora es un `WebFilter` reactivo
  (caché local → Redis reactivo → users), sin `.block()`.
- ~~**`getTenant` del gateway lee Redis de forma bloqueante**~~ **Resuelto:** `CacheUtil.getTenantByUrl`
  usa un `ReactiveRedisTemplate` con los mismos serializers que el `redisTemplate` de `decor-common`.
- **users migra el schema en cada conexión:** su `TenantDataSource.prepareSchema` corre `runMigration`
  (levanta un `EntityManagerFactory`) cada vez que se pide una conexión de un tenant. Products solo lo hace
  si el schema no existe. Se mantuvo el comportamiento, pero es costoso.
- **Tests que ya fallaban:** los `contextLoads` y el `UserServiceTest` de users necesitan Postgres levantado;
  dos tests de `ProductServiceTest` (`deleteProductById_*`) no coinciden con `ProductService` (stub de más y
  "Product not found").
- **Rate limit por `X-Forwarded-For`:** ese header lo puede mandar el cliente, así que se puede esquivar el límite
  (también el del alta de tenants) cambiándolo en cada request. Si el gateway queda expuesto directo a internet,
  conviene tomar la IP de la conexión o confiar en el header solo detrás de un proxy propio.
- **Tokens anteriores al alta de tenants:** no tienen el claim `tenant`, así que todos los usuarios tienen que volver
  a iniciar sesión una vez.
- **Tenants creados a mano antes del alta:** siguen funcionando con la `url` que tengan cargada; su identificador en el
  panel del superadmin se deriva del schema.
- **Frontend nuevo (se va a reescribir):** tiene que usar `/api/v1/catalog` para la parte pública y mandar el `Origin`
  del tenant en cada llamada a la API (también si llama desde el servidor), porque el gateway reconoce el tenant por ese
  header. Cuando esté, se pueden cerrar los endpoints públicos viejos (`/products/all`, `/{id}`, `/search`,
  `/featured`), que muestran el stock exacto.
- **Bugs corregidos en el camino:**
  - CORS del gateway: se cacheaban orígenes no permitidos, así que desde el segundo request pasaba cualquiera.
  - products pedía el tenant con `?schema=` a un endpoint que solo aceptaba `url` (400).
  - `UserController`: 4 endpoints con `{id}` en la ruta leían `userId` y respondían 500.
  - `POST /products` y la subida de imágenes no exigían administrador.
  - Gateway: el rate limit nunca contaba (siempre devolvía 1); un tenant inexistente respondía 503; los errores de
    los servicios se convertían en 503.
  - users: `TenantRepository` declaraba id `UUID` (la entidad usa `Long`); la clave de CORS en Redis se escribía con
    `SETRANGE` y nunca vencía; el reset de contraseña no validaba el token (500 con uno inválido); el `HeaderAuthFilter`
    seguía de largo después de una ruta abierta.

### Detalle de precios, stock y destacados

| Endpoint | Quién | Qué hace |
|---|---|---|
| `GET /api/v1/products/{id}/pricing` | Administrador | Precio, costo, margen y margen % |
| `PUT /api/v1/products/{id}/pricing` | Administrador | Cambia precio y costo |
| `POST /api/v1/products/{id}/stock/movements` | Administrador, empleado | Registra `ENTRADA` / `SALIDA` (cantidad) o `AJUSTE` (stock contado, con motivo obligatorio) |
| `GET /api/v1/products/{id}/stock/movements` | Administrador, empleado | Historial de movimientos, del más nuevo al más viejo |
| `GET /api/v1/products/stock/low` | Administrador, empleado | Productos con stock menor o igual a su mínimo |
| `PUT /api/v1/bundles/{id}/price` | Administrador | Cambia el precio del combo |
| `GET /api/v1/products/featured` | Público | Destacados: primero por `featuredOrder`, los que no tienen orden al final por nombre |
| `PUT /api/v1/products/{id}/featured` | Administrador | Marca o desmarca como destacado: `{"featured": true, "order": 1}` |

- **Cambios de API:** `POST /products` recibe además `minStock`, `price` y `cost` (el stock inicial queda como movimiento de
  `ENTRADA`). `PUT /products/{id}` recibe `minStock` en lugar de `stock`. Los productos devuelven `price` y `featured`;
  los combos `price`, `listPrice` y `available`.
- **Seguridad:** `POST /products` y la subida de imágenes ahora exigen administrador (antes cualquier usuario logueado podía).
- **Gateway:** `GET /api/v1/products/featured` es ruta pública; pricing, stock y marcar destacado piden login.
- **Datos existentes:** los productos actuales quedan sin precio ni costo, con `minStock = 0` y no destacados; su stock
  actual no tiene movimientos previos en el historial.

### Detalle del alta de tenants (decorusers, `/api/v1/platform`)

| Endpoint | Quién | Qué hace |
|---|---|---|
| `POST /api/v1/platform/tenants` | Público (3 por minuto) | Alta: `{"identifier", "ownerName", "ownerEmail", "ownerPassword"}`. Devuelve el tenant y su URL |
| `POST /api/v1/platform/auth/login` | Público (5 por minuto) | Login del superadmin (token con `tenant = public`) |
| `GET /api/v1/platform/admin/tenants` | Superadmin | Lista de tenants (sin los borrados) |
| `GET /api/v1/platform/admin/tenants/{id}` | Superadmin | Detalle de un tenant |
| `POST /api/v1/platform/admin/tenants` | Superadmin | Alta de un tenant (igual que el autoservicio) |
| `PUT /api/v1/platform/admin/tenants/{id}/enabled` | Superadmin | `{"enabled": false}` deshabilita (403 en el gateway) |
| `DELETE /api/v1/platform/admin/tenants/{id}` | Superadmin | Soft delete (404 en el gateway; el identificador queda reservado) |

- **Configuración:** users `platform.scheme`, `platform.domain`, `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`;
  gateway `platform.url` (frontend de la plataforma, permitido por CORS) y `limit.signup`.
- Las rutas `/api/v1/platform/**` no pasan por el filtro de tenant del gateway; users igual verifica que no
  haya tenant en la request.

### Detalle de importar / exportar productos (products, solo administrador)

| Endpoint | Qué hace |
|---|---|
| `GET /api/v1/products/export` | Descarga `productos.csv` con todo el catálogo (sin borrados), ordenado por nombre |
| `GET /api/v1/products/import/template` | Descarga `plantilla-productos.csv` (solo encabezados) |
| `POST /api/v1/products/import` | Sube el CSV (`file`, multipart). 200 `{created, updated, errors: []}`; 400 con `errors: [{row, message}]` y no se importa nada |

- **Columnas:** `nombre;categoria;descripcion;precio;costo;material;color;estilo;alto;ancho;largo;peso`. Solo `nombre`
  es obligatoria; se puede subir un archivo con menos columnas (ej. `nombre;precio`). Los encabezados aceptan acentos
  y mayúsculas.
- **Validaciones por fila:** nombre obligatorio y no repetido en el archivo; números en formato argentino y no
  negativos; precio y costo con hasta 10 enteros y 2 decimales; textos de hasta 255 caracteres; un nombre de un
  producto borrado no se puede reusar (la columna es única aunque esté borrado).
- **Productos nuevos:** stock 0 y stock mínimo 0. Al crear metadatos, `ProductMetaData` pone `unknown` en material,
  color y estilo si no vienen (comportamiento que ya tenía el modelo).
- Los mensajes de error están en inglés, como el resto de la API.

### Detalle del catálogo público (products, sin login)

| Endpoint | Qué hace |
|---|---|
| `GET /api/v1/catalog/products?q&category&sort&page&size` | Página de productos. `q` busca en nombre, descripción y categoría; `sort` = `name` (defecto), `price_asc`, `price_desc`; `size` 24 por defecto, máximo 100. Devuelve `{items, page, size, totalItems, totalPages}` |
| `GET /api/v1/catalog/products/{slug}` | Detalle para el link compartido (imágenes en orden y metadatos) |
| `GET /api/v1/catalog/categories` | Categorías con la cantidad de productos |

- Cada producto devuelve `slug, name, category, description, price, availability, featured, images`. El listado y el
  detalle del panel (`/api/v1/products`) también devuelven `slug`, para armar el botón de compartir.
- **Vistas previas en WhatsApp/Instagram:** las etiquetas Open Graph las genera el frontend (Next.js `generateMetadata`)
  con el detalle por slug.

### Detalle de permisos (decorusers, solo ADMINISTRADOR)

| Endpoint | Qué hace |
|---|---|
| `GET /api/v1/users/roles/permissions` | Máscara de cada rol: `[{"role": "ADMINISTRADOR", "permissions": 31}, {"role": "EMPLEADO", ...}, {"role": "CLIENTE", ...}]` |
| `PUT /api/v1/users/roles/{EMPLEADO\|CLIENTE}/permissions` | `{"permissions": 5}` (PRODUCTOS + STOCK). USUARIOS (16) o bits desconocidos → 400 |

| Área | Bit | Habilita (products) |
|---|---|---|
| PRODUCTOS | 1 | Crear, editar y borrar productos, imágenes, metadatos y destacados |
| PRECIOS | 2 | Ver costo y margen, editar precio y costo (`/products/{id}/pricing`) |
| STOCK | 4 | Ver y registrar movimientos, stock bajo |
| COMBOS | 8 | Crear, editar y borrar combos y cambiar su precio |
| USUARIOS | 16 | Gestionar usuarios (solo ADMINISTRADOR, no se delega) |

- Los permisos viajan en `RoleDto.permissions` (users los calcula) y products los convierte en authorities
  `PERM_<AREA>` (`CustomUserDetails`). Un usuario con varios roles tiene la unión de sus máscaras.
- El login devuelve los roles con su máscara, así el frontend puede mostrar u ocultar opciones.
