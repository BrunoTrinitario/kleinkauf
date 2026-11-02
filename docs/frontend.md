# Frontend (plan)

Decisión de stack y checklist de lo que tiene que cubrir el frontend nuevo, armada a partir de la documentación del
backend ([endpoints](endpoints.md), [tenants](tenants.md), [permisos](permisos.md), [ventas](ventas.md),
[panel-admin](panel-admin.md), [auditoría](auditoria.md), [observabilidad](observabilidad.md)) y del frontend actual.

Leyenda: **[listo]** el backend ya lo expone · **[plan]** está planificado y todavía no existe en el backend ·
☑ ya existe en el frontend actual (Next.js) y se reescribe.

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
- **Puertos en desarrollo:** la documentación pone el frontend de las tiendas en `http://<tienda>.localhost:3000`,
  pero ese puerto es el del gateway, y el frontend actual corre en 2999. Hay que elegir el puerto del frontend (por
  ejemplo 5173 de Vite) y ajustar `platform.domain` (users) y `platform.url` (gateway). Los tenants ya creados
  guardan su `url` con el puerto viejo.
- **Un solo build para la plataforma y las tiendas:** según el host, la app muestra la parte de plataforma
  (`localhost`) o la de tienda (`<tienda>.localhost`).
- **Token:** viene en el login y tiene el claim `tenant`; si se usa en otro tenant el gateway responde 401. Guardarlo
  por origen alcanza (el `localStorage` ya es por origen).

---

## Checklist

### 0. Base técnica
- [ ] Proyecto Vite + TypeScript + Tailwind + shadcn/ui, en `frontend/`
- [ ] Cliente HTTP único (baseURL del gateway, `Authorization`, manejo central de 401 / 403 / 404 / 429)
- [ ] Detección de contexto: plataforma (sin subdominio) vs. tienda (con subdominio)
- [ ] Contexto de sesión: usuario, roles y **máscara de permisos** (bits 1/2/4/8/16), con helper `can('STOCK')`
- [ ] Rutas protegidas por rol y por área de permiso (la UI oculta lo que no se puede usar; el backend igual valida)
- [ ] Layouts: público, panel de la tienda, panel de la plataforma
- [ ] Textos en español centralizados (hoy en `lib/strings.ts`), moneda en pesos, fechas locales
- [ ] Tema claro/oscuro, responsive (uso en tablet y celular en el local)
- [ ] Estados vacíos, de carga y de error reutilizables; aviso de "demasiados intentos" para el 429
- [ ] Páginas de tienda inexistente (404) y deshabilitada (403)
- [ ] Variables de entorno (URL del gateway) y build para producción

### 1. Plataforma (sin tenant) **[listo]**
- [ ] Landing con explicación del producto
- [ ] **Alta de tienda** (`POST /platform/tenants`): identificador con la validación del backend (3–30 caracteres,
  `a-z 0-9 -`, reservados), datos del dueño, errores 400 / 409 / 429, y redirección a la URL de la tienda al terminar
- [ ] Login del superadmin (`/platform/auth/login`)
- [ ] Panel de tenants: lista, detalle, alta, habilitar/deshabilitar, borrar (soft delete); aviso de que el cambio tarda hasta 1 min
- [ ] **[plan]** Dashboard de la plataforma: tenants (total / habilitados / deshabilitados / borrados) y altas por período
- [ ] **[plan]** Uso por tenant (usuarios, activos, última actividad, productos, combos) con orden elegible
- [ ] **[plan]** Gestión de superadmins: lista, alta con contraseña temporal, desactivar/reactivar

### 2. Cuenta y sesión en la tienda **[listo]**
- [ ] Login ☑ y registro como `CLIENTE` ☑
- [ ] Recupero de contraseña: pedir el mail y pantalla `/reset/:token` (el token vale 15 min)
- [ ] Perfil: cambiar nombre y contraseña ☑, borrar la propia cuenta con confirmación
- [ ] Sesión vencida: volver al login conservando la página de destino
- [ ] Menú de usuario con los roles y logout

### 3. Catálogo público **[listo]**
- [ ] Home con **productos destacados** (`/products/featured`, en su orden)
- [ ] Listado paginado (24 por página) con búsqueda `q`, filtro por categoría, orden (nombre / precio ↑ / precio ↓)
- [ ] Categorías con su cantidad de productos
- [ ] Detalle por **slug** (URL compartible), con imágenes, metadatos (material, color, estilo, medidas) ☑
- [ ] Disponibilidad: `DISPONIBLE`, `POCAS_UNIDADES`, `AGOTADO` (el catálogo nunca muestra stock exacto ni costo)
- [ ] Producto sin precio → "Consultar"
- [ ] **Combos**: listado y detalle con precio, precio de lista (ahorro) y disponibilidad
  (`/bundles`; en el catálogo propiamente dicho llegan con ventas)
- [ ] Botón para compartir el link (WhatsApp, copiar) y vista previa Open Graph (ver Stack)
- [ ] Imágenes con carga diferida y tamaño controlado

### 4. Compra online **[plan: ventas, etapa 1–2]**
- [ ] Carrito en el frontend (productos y combos, cantidades, persistido en el navegador)
- [ ] Aviso si cambió un precio entre el carrito y el pedido
- [ ] Checkout: datos del comprador (invitado o logueado), retiro o envío con dirección, notas
- [ ] Pantalla del pedido: número (`K7QM-4X2P`), total, **datos para transferir** (alias, CBU, titular, banco) y vencimiento con cuenta regresiva
- [ ] Subir comprobante (JPG / PNG / PDF, hasta 5 MB), con reintento si se rechaza
- [ ] Consulta de pedido de invitado (email + número, por POST)
- [ ] Cancelar el pedido mientras no esté validado el pago
- [ ] "Mis pedidos" del cliente logueado, con paginación
- [ ] Estados del pedido legibles (pendiente, en revisión, rechazado con motivo, pagado, en preparación, listo, entregado, cancelado, vencido)

### 5. Panel de la tienda: productos **[listo]**
Visible según el permiso de cada área; el `ADMINISTRADOR` ve todo.
- [ ] Lista de productos con búsqueda y filtros ☑ (con stock exacto)
- [ ] Alta y edición: nombre, categoría, descripción, stock mínimo ☑ (el stock inicial queda como movimiento de entrada)
- [ ] Borrar producto con confirmación ☑
- [ ] Imágenes: subir varias (hasta 1 MB cada una, con validación en el cliente), ver y borrar
- [ ] Metadatos: material, color, estilo, alto, ancho, largo, peso
- [ ] Destacados: marcar/desmarcar y elegir el orden
- [ ] **Precios** (`PRECIOS`): precio, costo, margen y margen %; sin el permiso, esos campos no se muestran
- [ ] **Stock** (`STOCK`): registrar entrada / salida / ajuste con motivo, historial por producto, error 409 por stock insuficiente
- [ ] Alerta de **stock bajo** (listado y contador en el menú)
- [ ] **Importar CSV** (`PRODUCTOS` + `PRECIOS`): descargar plantilla, subir archivo, mostrar `{created, updated}` o la **tabla de errores por fila**; límites de 5.000 filas / 5 MB
- [ ] **Exportar CSV**
- [ ] **Combos** (`COMBOS`): crear, habilitar/deshabilitar, cambiar precio, agregar / quitar productos (también en lote), borrar

### 6. Panel de la tienda: usuarios y permisos **[listo]** (solo `ADMINISTRADOR`)
- [ ] Lista de usuarios con sus roles ☑
- [ ] Crear usuario con roles ☑, editar nombre o contraseña ☑, borrar ☑
- [ ] Asignar y quitar roles (`SUPERADMIN` no se ofrece)
- [ ] **Matriz de permisos por rol**: casillas de `PRODUCTOS`, `PRECIOS`, `STOCK`, `COMBOS` para `EMPLEADO` y `CLIENTE`; `USUARIOS` bloqueado; `ADMINISTRADOR` solo lectura; aviso de que products tarda hasta 1 min en aplicarlo

### 7. Panel de la tienda: resumen **[plan: panel-admin]**
- [ ] Dashboard de productos: total, por categoría, sin precio, sin imágenes, destacados
- [ ] Dashboard de stock: stock bajo, agotados, valor del inventario a costo y a precio (con los excluidos), últimos 10 movimientos
- [ ] Dashboard de usuarios: total, activos (30 días), por rol, altas por período (`from`, `to`, `groupBy`)
- [ ] Cada bloque carga por separado: si un servicio falla, el resto del panel sigue
- [ ] Gráficos con períodos en cero incluidos

### 8. Panel de la tienda: ventas **[plan: ventas, etapas 1–3]** (solo `ADMINISTRADOR`)
- [ ] Lista de pedidos con filtros (estado, canal, medio de pago, "stock para revisar", fechas) y búsqueda por número o email
- [ ] Detalle del pedido: ítems, historial de estados y comprobantes (descarga)
- [ ] Cambios de estado según las transiciones válidas: validar / rechazar con motivo, preparar, listo, entregar, cancelar con motivo
- [ ] **Venta del local (mostrador)**: buscar productos, cambiar precio de un ítem, descuento, medio de pago, estado inicial, comprador opcional
- [ ] Avisos de venta sin stock suficiente (ajuste automático)
- [ ] Configuración de ventas: alias, CBU, titular, banco y plazo de vencimiento
- [ ] Reportes: totales por período, ranking de productos, ganancia, desglose por canal y medio de pago
- [ ] Contador de vendidos en productos y orden "más vendidos" en el catálogo

### 9. Auditoría y observabilidad **[plan]**
- [ ] Panel de la plataforma: salud de los servicios y métricas en tiempo real
- [ ] Panel de la tienda: feed de auditoría en vivo (quién hizo qué)
- [ ] Cliente WebSocket: pedir el ticket (`POST /observability/ws-ticket`, un solo uso, 30 s), conectar, reconectar con ticket nuevo si se corta
- [ ] Historial por REST al abrir el panel; el WS solo trae lo nuevo

### 10. Calidad
- [ ] Tipos de TypeScript de todas las respuestas del backend (activar el chequeo de tipos en el build; hoy está desactivado)
- [ ] Tests de las reglas de permisos del cliente y de los flujos principales (login, alta de tienda, carga de producto)
- [ ] Accesibilidad básica: foco, etiquetas, contraste
- [ ] Pipeline de build y despliegue de los archivos estáticos

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
