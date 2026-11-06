# Pendientes

_Solo lo que falta. Lo hecho se borró; la documentación (en inglés) describe el estado actual. Este archivo se va a
borrar cuando se decida qué hacer con cada punto._

## B. Frontend

### B8. Historial puntual
- Historial de una cosa dentro de su pantalla (la página del pedido, la edición de un producto o de un usuario) con
  `GET /observability/store/audit/{entity}/{id}`. El endpoint ya existe.

### B10. Faltantes chicos
- **Margen en precios**: mostrar `margin` y `marginPercent` (ya vienen en `GET /products/{id}/pricing`) junto al
  precio y el costo, solo con `PRECIOS`.
- **Más vendidos**: mostrar `soldCount` en el panel y sumar el orden `best_sellers` al catálogo (el backend ya lo
  acepta).
- **Estados vacíos y de carga comunes**: hoy cada página arma los suyos (hay `empty`, `skeleton` y `spinner` en
  `components/ui` casi sin usar).
- **Plataforma**: landing y alta pública de tienda (hoy `/` redirige a `/dashboard` y la tienda se crea desde el
  panel del superadmin; el endpoint de autoservicio ya existe).

### B9. Calidad
- Tipos de todas las respuestas (las fichas de `docs/backend/` tienen los ejemplos).
- Tests de permisos y de los flujos principales en el frontend.
- Accesibilidad básica: foco, etiquetas, contraste.

### B11. Publicación
- Medir la performance de las páginas (Lighthouse) y cargar el sitemap de cada tienda en Search Console.
- Que un abogado revise los textos de `/privacidad` y `/terminos` (`lib/strings.ts`, `legal`).

### B12. Configuración por tienda
- Textos de la tienda configurables (nombre, tema, ícono y redes ya están).
- Que cada visitante elija claro u oscuro (hoy lo decide la tienda con el tema "Noche").
- Mails configurables por tienda.
- Foto de perfil para los superadmins.

## C. Gestión presencial

Nada tiene decisión tomada. Cada punto se planifica con preguntas antes de implementarlo.

### C1. Próxima etapa de ventas
1. **P1 – área `VENTAS` (bit 32)**: que un `EMPLEADO` pueda usar el panel de ventas. users suma el bit; sales pasa
   de solo `ADMINISTRADOR` a `PERM_VENTAS`; el frontend lo suma a la matriz de permisos.
2. **P2 – código de producto o de barras**: campo `code` en products, único por tienda; búsqueda por código; columna
   en el CSV; buscador del mostrador que acepte un escáner.
3. **P5 – stock reservado visible**: "3 en stock, 1 reservado por pedido online", en el panel y en la venta del local.
4. **P6 – pagar al retirar**: medio de pago al validar un pedido online y paso a `PAGADO` sin comprobante.

### C2. Caja y precio según medio de pago
- **P3 – Caja**: apertura y cierre por turno, arqueo (esperado contra real), ingresos y egresos que no son ventas.
- **P4 – Precio según medio de pago**: descuento por efectivo o transferencia, recargo por crédito o cuotas.

### C3. Después
- **P7–P9**: ficha de cliente única, señas y saldo, presupuestos y pedidos a medida.
- **P10–P11**: proveedores y compras; gastos y resultado del mes.
- **P12–P15**: factura electrónica (ARCA), avisos por WhatsApp, uso en el local (tablet, ticket, sin internet),
  varias sucursales.

### C4. "Para después" de ventas
- Mercado Pago con webhook.
- Avisos por email cuando cambia el estado del pedido (la confirmación al crearlo ya está).
- Comprobante imprimible (no fiscal) de la venta del local.
- Exportar los reportes a CSV.
- Zona horaria por tienda (hoy es una para todo el servicio).
- Costo de envío y logística.
- Stock y vendidos por eventos con outbox (resuelve las vueltas atrás que hoy quedan solo en el log).

### Ideas sueltas de los paneles
- "Tareas pendientes" accionables en `/admin`: productos sin precio o sin imagen, combos que no se pueden armar.
- "Activos" por última request (hoy es el último login en 30 días).

## D. Deuda técnica

- **CHECK de los enums**: las columnas `@Enumerated(EnumType.STRING)` tienen un `CHECK` que Hibernate crea con los
  valores de ese momento y `hbm2ddl update` no actualiza: al sumar un valor nuevo, guardarlo falla en los schemas que
  ya existían. Antes de agregar un valor hay que borrar ese `CHECK` en cada schema (o guardar la columna como texto,
  como `store_settings.theme`).
- **Detección de imágenes repetida**: `FileManager.imageExtension` de products hace lo mismo que `ImageFiles` de
  kleinkauf-common. Pasar products a la de common.
- **Configuración de ventas duplicada**: `sales_settings` es "una fila por schema" pero con id autoincremental; si los
  dos primeros guardados de una tienda llegan juntos, quedan dos filas. `store_settings` lo resuelve con id fijo y un
  reintento.
- **Revisiones**: performance del código (consultas lentas, N+1, caché) y del server (CPU, memoria, heap, latencia).
- **CSP**: pasar de Report-Only a `Content-Security-Policy` cuando no aparezcan avisos en la consola.
- **Backups**: el servicio está armado pero `deploy.sh` (en `deploy/backend`) todavía no lo levanta. Confirmar si se
  activó a mano en el server.
