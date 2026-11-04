# KleinKauf

Software libre de administración para pequeños emprendimientos (tiendas de muebles y decoración, por ejemplo):
catálogo de productos, usuarios y roles, y —en camino— ventas, caja y gestión del local físico.

El proyecto tiene dos objetivos:

- **Probar tecnologías y arquitecturas modernas**: microservicios, caché distribuida y multi-tenant.
- **Construir una herramienta real y gratuita** para negocios chicos que hoy llevan todo en planillas.

Cada negocio es un **tenant**: tiene su propia tienda, sus usuarios, sus productos y su propio schema en la base de datos.

> El estado del producto, las funcionalidades hechas y las planificadas están en [MVP.md](MVP.md).

---

## Arquitectura

```
                         ┌────────────┐
   Navegador ──────────▶ │  Frontend  │  Vite + React (puerto 2999)
                         └─────┬──────┘
                               │ HTTP
                         ┌─────▼──────┐        ┌──────────┐
                         │  Gateway   │◀──────▶│  Redis   │  caché + rate limit
                         │ (puerto    │        └──────────┘
                         │   3000)    │
                         └──┬──────┬──┘
              lb://users    │      │    lb://products
            ┌───────────────▼┐    ┌▼───────────┐
            │ kleinkaufusers │◀──▶│  product   │   comunicación interna
            │     :3001      │    │   :3002    │   (/api/internal/**, token firmado)
            └───────────┬────┘    └─────┬──────┘
                        │                │
                  ┌─────▼────────────────▼─────┐        ┌──────────────┐
                  │         PostgreSQL         │        │    Eureka    │
                  │  DB `users` y DB `products`│        │    :8761     │
                  │  (un schema por tenant)    │        │  (discovery) │
                  └────────────────────────────┘        └──────────────┘
```

### Piezas

| Pieza | Qué hace | Puerto |
|---|---|---|
| **Frontend** (`frontend/`) | Interfaz web en Vite + React + TypeScript + Tailwind + shadcn/ui: login, registro, panel de administración y catálogo | 2999 |
| **Gateway** (`backend/gateway`) | Único punto de entrada. Valida el JWT, resuelve el tenant, aplica CORS y rate limit. Spring Cloud Gateway (reactivo) | 3000 |
| **kleinkaufusers** (`backend/kleinkaufusers`) | Autenticación (login, registro, refresh, recupero de contraseña), usuarios, roles, permisos y alta de tenants | 3001 |
| **product** (`backend/kleinkauf-product`) | Productos con imágenes, combos, precios y costos, movimientos de stock, destacados, catálogo público e importación/exportación CSV | 3002 |
| **kleinkauf-sales** (`backend/kleinkauf-sales`) | Pedidos online y del local, pagos por transferencia y reportes | 3003 |
| **kleinkauf-observability** (`backend/kleinkauf-observability`) | Salud y métricas de los servicios, auditoría y paneles en tiempo real por WebSocket | 3004 |
| **kleinkauf-common** (`backend/kleinkauf-common`) | Librería compartida: DTOs, headers, Redis, tokens internos, multi-tenant de Hibernate, filtros, configuración de observabilidad y captura de auditoría | — |
| **Eureka** (`backend/eureka`) | Registro y descubrimiento de servicios | 8761 |

### Decisiones de diseño

- **Multi-tenant por schema** en PostgreSQL: cada tienda tiene su propio schema, resuelto por la request.
- **Caché en dos niveles**: Caffeine (local) + Redis (distribuida).
- **Comunicación entre servicios** por `/api/internal/**` con un token interno firmado, protegida con
  **Resilience4j** (circuit breaker, reintentos y timeouts).
- **Rutas**: públicas bajo `/api/v1/**`, internas bajo `/api/internal/**`, y las imágenes en `/{recurso}/images/{archivo}`.
- **Permisos por área**, configurables por rol en cada tienda (`ADMINISTRADOR`, `EMPLEADO`, `CLIENTE`).
- **Observabilidad**: Actuator en un puerto de gestión interno, trazas con OpenTelemetry, logs JSON y, en desarrollo,
  Prometheus, Grafana, Tempo y Loki. **Auditoría** con outbox: ningún cambio se pierde aunque se caiga un servicio.

### Stack

- **Backend**: Java 21, Spring Boot 3.5, Spring Cloud (Gateway, Eureka), Spring Security + JWT, JPA/Hibernate, Resilience4j, Gradle.
- **Datos**: PostgreSQL 17, Redis 8.
- **Frontend**: Vite, React 19, React Router, TypeScript, Tailwind CSS, Radix UI / shadcn/ui, axios.

---

## Cómo correrlo

### Requisitos

- JDK 21
- Node.js 20 o superior y npm
- Docker (para PostgreSQL y Redis)

### 1. Base de datos y Redis

```bash
docker compose -f backend/deploy/docker-compose.bd.yml up -d
```

Levanta PostgreSQL (`localhost:5432`, usuario y contraseña `postgres`) y Redis (`localhost:6379`).
El script `backend/deploy/init.sql` crea las bases `users`, `products`, `sales` y `observability`; las tablas las genera
Hibernate al arrancar.

`init.sql` solo corre la primera vez, con el volumen de Postgres vacío. Si el volumen es de antes de que existiera
alguna de esas bases (por ejemplo, de antes de ventas u observabilidad), el servicio que la usa queda reiniciándose.
Para crear las que falten sin perder datos:

```bash
for db in users products sales observability; do
  docker exec postgres-dev psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1 \
    || docker exec postgres-dev psql -U postgres -c "CREATE DATABASE $db"
done
```

(O borrar el volumen con `docker compose -f backend/deploy/docker-compose.bd.yml down -v`, que borra todos los datos.)

### 2. Backend

**Con Docker (recomendado):** un solo comando levanta PostgreSQL, Redis, Eureka, los cinco servicios (users, products,
sales, observability y gateway) y carga el seed de desarrollo:

```bash
docker compose -f backend/deploy/docker-compose.dev.yml up -d
```

La primera vez tarda unos minutos (cada servicio baja sus dependencias y compila con `gradle bootRun`). Los servicios
quedan `healthy` cuando Spring ya arrancó; el panel de Eureka está en `http://localhost:8761`.

**Seed de desarrollo:** el servicio `seed` (`backend/deploy/seed/seed.mjs`) carga todo por la API, con los mismos
flujos que usa la app, y termina:

- da de alta la tienda `demo` (autoservicio de la plataforma);
- carga 48 productos en 9 categorías con precio, costo, stock, metadatos, dos imágenes genéricas por producto y 6
  destacados. Hay casos de todo tipo: sin precio ("Consultar precio"), agotados y con pocas unidades;
- crea 28 usuarios de prueba: 4 empleados y 24 clientes.

Se puede correr las veces que haga falta: lo que ya existe (la tienda, un producto con el mismo nombre, un usuario con
el mismo email) se saltea. Para volver a correrlo: `docker compose -f backend/deploy/docker-compose.dev.yml up seed`.
Los datos están en `seed/data.json`; las imágenes en `seed/images/` se generan con
`node backend/deploy/seed/generate-images.mjs` (ya están commiteadas). Además `kleinkaufusers` crea un superadmin al
arrancar. Usuarios de prueba (solo para desarrollo):

| Usuario | Email | Contraseña | Dónde |
|---|---|---|---|
| Dueño de la tienda demo | `admin@demo.local` | `demo1234` | `http://demo.localhost:2999` |
| Empleados y clientes de la tienda demo | `nombre.apellido@demo.local` (ej. `valentina.rios@demo.local`) | `cliente123` | `http://demo.localhost:2999` |
| Superadmin de la plataforma | `superadmin@kleinkauf.local` | `superadmin123` | `http://localhost:2999` |

**Sin Docker:** cada servicio es un proyecto Gradle independiente (con su propio `gradlew`) que consume `kleinkauf-common`
como build compuesto. Levantá primero la base y Redis (`docker compose -f backend/deploy/docker-compose.bd.yml up -d`)
y después los servicios **en este orden**, cada uno en su terminal:

```bash
cd backend/eureka                  && ./gradlew bootRun   # 1. descubrimiento  (8761)
cd backend/kleinkaufusers          && ./gradlew bootRun   # 2. usuarios        (3001)
cd backend/kleinkauf-product       && ./gradlew bootRun   # 3. productos       (3002)
cd backend/kleinkauf-sales         && ./gradlew bootRun   # 4. ventas          (3003)
cd backend/kleinkauf-observability && ./gradlew bootRun   # 5. observabilidad  (3004)
cd backend/gateway                 && ./gradlew bootRun   # 6. gateway         (3000)
```

(En Windows usá `gradlew.bat bootRun`.)

La configuración está en `src/main/resources/application.properties` de cada servicio. Antes de usarlo en serio,
cambiá los secretos (`jwt.secret`, `api.internal.key`, contraseñas de base de datos y del mailer).

Para crear el primer superadmin de la plataforma, definí las variables de entorno `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD`
(ver `platform.superadmin.*` en `kleinkaufusers`) antes de arrancar `kleinkaufusers`.

### Tests

Cada servicio corre los suyos con `./gradlew test` (en Windows, `gradlew.bat test`). Los que levantan la app entera
(`@SpringBootTest`) usan **Testcontainers**: arrancan Postgres y Redis en Docker solos, así que no hace falta tener la
base levantada. Sin Docker esos tests se saltean y el resto corre igual.

**CI** (`.github/workflows/ci.yml`): en cada push a `main` y en cada PR contra `main` corre los tests de cada servicio
(con Testcontainers) y el build del frontend (`tsc -b` y `vite build`). No despliega.

**Prueba de punta a punta** (`backend/deploy/e2e/e2e.mjs`): con todo levantado por el compose de desarrollo, recorre
por la API el alta de una tienda, la sesión (login, refresh, logout), productos e imágenes, el catálogo, un pedido como
invitado, un reporte de problema y el panel de la plataforma. Crea una tienda nueva y la borra al terminar; sale con
código 1 si algo falla:

```bash
docker compose -f backend/deploy/docker-compose.dev.yml --profile e2e up e2e
```

Contra otro entorno se corre con Node 22 y las variables `GATEWAY_URL`, `PLATFORM_URL`, `STORE_SCHEME`,
`STORE_DOMAIN` y, para probar la plataforma y borrar la tienda, `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev        # abre http://demo.localhost:2999 (la tienda demo del seed)
```

El frontend le habla al gateway según `VITE_API_BASE_PUBLIC` en `frontend/.env` (por defecto `http://localhost:3000`).

El gateway reconoce la tienda por el `Origin` del navegador, así que la URL del tenant tiene que coincidir con la del frontend
(`http://<tienda>.localhost:2999`). Por eso `platform.domain` en `kleinkaufusers` y `platform.url` en el gateway usan el puerto 2999.

### Tenants en desarrollo

La URL de cada tienda es `<identificador>.<dominio>`. En desarrollo los navegadores resuelven `*.localhost` solos,
así que una tienda llamada `mitienda` se ve en `http://mitienda.localhost:2999`.

---

## Cómo está organizado

```
kleinkauf/
├── backend/                      Microservicios Java (cada carpeta es un proyecto Gradle)
│   ├── gateway/                  API Gateway
│   ├── kleinkaufusers/           Usuarios, auth, roles y tenants
│   ├── kleinkauf-product/        Productos, stock y catálogo
│   ├── kleinkauf-sales/          Ventas: pedidos, pagos y reportes
│   ├── kleinkauf-observability/  Salud, métricas, auditoría y WebSocket
│   ├── kleinkauf-common/         Librería común
│   ├── eureka/                   Servidor de descubrimiento
│   └── deploy/                   Docker Compose (base, servicios y stack de observabilidad) e init.sql
├── frontend/                     Aplicación web (Vite + React)
│   ├── src/pages/                Páginas: catálogo, login, registro, ajustes y panel de administración
│   ├── src/components/           Componentes de la aplicación y de UI (shadcn/ui)
│   ├── src/lib/ src/hooks/       Contexto de auth, tipos, textos y hooks
│   └── public/                   Recursos estáticos
├── docs/                         Documentación y planes por módulo
│   ├── backend/                  Referencia del backend: un archivo por servicio, con todos los endpoints
│   ├── tenants.md                Cómo funcionan los tenants
│   ├── permisos.md               Permisos por área y rol
│   ├── ventas.md                 Módulo de ventas
│   ├── panel-admin.md            Panel de administración
│   ├── auditoria.md              Auditoría
│   ├── observabilidad.md         Observabilidad: salud, métricas, trazas, logs y WebSocket
│   └── pendientes.md             Lo que queda por hacer, con el flujo de cada tarea
├── MVP.md                        Estado actual y propuesta de funcionalidades
├── TODO                          Pendientes y cosas a tener en cuenta
└── .gitignore                    Reglas de ignore comunes a todo el repo
```

### Ramas

- `main`: el proyecto completo (backend + frontend). Se trabaja acá.
- `deploy/backend` y `deploy/frontend`: lo que está en el server. Un push a cualquiera de las dos lo despliega
  (ver [deploy.md](deploy.md)); se actualizan mergeando `main`.
- `develop` y `frontend`: ramas de trabajo previas, ya integradas en `main`.

---

## Documentación

- [MVP.md](MVP.md): análisis del proyecto y hoja de ruta.
- [docs/pendientes.md](docs/pendientes.md): lista de pendientes y cómo encarar cada uno.
- [docs/backend/](docs/backend/README.md): referencia del backend, un archivo por servicio con todos los
  endpoints, ejemplos, errores y cómo se comunican.
- [docs/tenants.md](docs/tenants.md) y [docs/permisos.md](docs/permisos.md): multi-tenant y permisos.
- [docs/ventas.md](docs/ventas.md) y [docs/panel-admin.md](docs/panel-admin.md): ventas y paneles de administración.
- [docs/observabilidad.md](docs/observabilidad.md) y [docs/auditoria.md](docs/auditoria.md): salud, métricas, trazas,
  logs, auditoría y paneles en tiempo real (incluye cómo levantar Grafana y Prometheus).
