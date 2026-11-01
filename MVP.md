# Decor — Análisis y propuesta de MVP

Objetivos del proyecto:
- Probar tecnologías y arquitecturas modernas: microservicios, caché distribuida, multi-tenant.
- Construir un software libre de administración para pequeños emprendimientos.

---

## 1. Estado actual

### Arquitectura
- **Gateway** (Spring Cloud Gateway): punto de entrada. Valida JWT, tenant, CORS y rate limit.
- **decorusers**: auth (login, registro, refresh, recupero de contraseña), usuarios, roles
  (`ADMINISTRADOR` / `EMPLEADO` / `CLIENTE`) y estadísticas de usuarios.
- **product**: productos con imágenes y metadatos (material, color, medidas), combos (bundles) y búsqueda.
- **Multi-tenant por schema** en Postgres.
- **Caché en dos niveles**: Caffeine (local) + Redis (distribuido).
- **Comunicación entre servicios** con token interno firmado aparte (`/api/internal/**`).

### Faltantes detectados
1. **Los productos no tienen precio.** `Product` tiene `stock`, pero no tiene precio ni costo. Los combos tampoco.
2. **El stock es un número que se sobreescribe** con `PUT`. No queda registro de quién lo cambió, cuándo ni por qué.
3. **No hay alta de tenants.** Solo existen endpoints internos de lectura, así que un emprendimiento no puede registrarse solo.
4. **El campo `permission` de `Role` no se usa** en el servicio de productos. Hoy los permisos dependen solo del rol.

---

## 2. Funcionalidades propuestas para el MVP

Ordenadas por prioridad sugerida. Marcar la decisión en cada una.

| # | Funcionalidad | Qué agrega | Tecnología a probar | Decisión |
|---|---|---|---|---|
| 1 | **Precio y costo** en productos y combos | Calcula el margen. Sin esto no sirve para vender | — | ☐ Sí ☐ No ☐ Después |
| 2 | **Movimientos de stock** (entrada / salida / ajuste, con motivo y usuario) + **alerta de stock mínimo** | Trazabilidad del inventario | Eventos (Redis Streams / RabbitMQ) | ☐ Sí ☐ No ☐ Después |
| 3 | **Ventas simples** (caja básica): elegir productos o combos, cobrar y descontar stock | Uso diario del negocio | **MS de ventas** que publica `VentaRegistrada` y productos descuenta stock (outbox + mensajería) | ☐ Sí ☐ No ☐ Después |
| 4 | **Panel de administración**: ventas del día y del mes, más vendidos, stock bajo, usuarios | Vista del dueño | Agregación entre servicios, caché de reportes | ☐ Sí ☐ No ☐ Después |
| 5 | **Alta de tenant (onboarding)**: registrarse y que se cree el schema solo | Autoservicio real | Flyway / Liquibase por schema | ☐ Sí ☐ No ☐ Después |
| 6 | **Catálogo público** con link para compartir (WhatsApp / Instagram) | Muy útil para emprendimientos chicos. Las imágenes ya están | Endpoint público cacheable | ☐ Sí ☐ No ☐ Después |
| 7 | **Auditoría** (quién creó o modificó cada cosa) | Control sobre los empleados | Hibernate Envers o `@CreatedBy` | ☐ Sí ☐ No ☐ Después |
| 8 | **Importar y exportar productos** (CSV / Excel) | Carga inicial rápida | — | ☐ Sí ☐ No ☐ Después |

> **Sobre ventas:** el `TODO` marca "MS ventas y logística" como **no entra**. Se recomienda incluir al menos
> una versión mínima de ventas (punto 3). Sin ventas el sistema queda como un catálogo, y además es lo que
> mejor muestra el valor de los microservicios: eventos, consistencia eventual y desacoplamiento.
> La logística sí puede quedar afuera.

---

## 3. Mejoras técnicas

| Mejora | Para qué | Decisión |
|---|---|---|
| **Resilience4j** (circuit breaker + reintentos) | Proteger las llamadas de productos a usuarios, que hoy usan `RestTemplate` sin protección | ☐ Sí ☐ No ☐ Después |
| **Observabilidad**: Micrometer + OpenTelemetry + Grafana / Tempo | Seguir una request a través de los servicios | ☐ Sí ☐ No ☐ Después |
| **Testcontainers** | Tests de integración con Postgres y Redis reales | ☐ Sí ☐ No ☐ Después |
| **Librería común** | Centralizar lo repetido en los tres servicios: `JwtUtil`, `CacheUtil`, filtros, DTOs de tenant y rol | ☐ Sí ☐ No ☐ Después |
| **Definir el uso de `permission`** en roles | Permisos más finos que el rol (por ejemplo, un empleado que puede ver pero no editar precios) | ☐ Sí ☐ No ☐ Después |

---

## 4. Orden sugerido

1. **Precios** (punto 1): cambio chico, solo en el servicio de productos.
2. **Movimientos de stock** (punto 2): prepara el terreno para ventas.
3. **Ventas mínimas** (punto 3) con mensajería entre servicios.
4. **Panel de administración** (punto 4) con los datos de ventas y stock.
5. **Onboarding de tenants** (punto 5) para abrirlo a otros emprendimientos.

---

## 5. Decisiones tomadas

<!-- Completar a medida que se decida -->

| Fecha | Decisión | Motivo |
|---|---|---|
| | | |
