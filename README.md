# Decor

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
                  ┌─────────▼┐    ┌▼───────────┐
                  │ decorusers│◀──▶│  product   │   comunicación interna
                  │   :3001   │    │   :3002    │   (/api/internal/**, token firmado)
                  └─────┬─────┘    └─────┬──────┘
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
| **decorusers** (`backend/decorusers`) | Autenticación (login, registro, refresh, recupero de contraseña), usuarios, roles, permisos y alta de tenants | 3001 |
| **product** (`backend/com.decor.product`) | Productos con imágenes, combos, precios y costos, movimientos de stock, destacados, catálogo público e importación/exportación CSV | 3002 |
| **decor-common** (`backend/decor-common`) | Librería compartida: DTOs, headers, Redis, tokens internos, multi-tenant de Hibernate y filtros | — |
| **Eureka** (`backend/eureka`) | Registro y descubrimiento de servicios | 8761 |

### Decisiones de diseño

- **Multi-tenant por schema** en PostgreSQL: cada tienda tiene su propio schema, resuelto por la request.
- **Caché en dos niveles**: Caffeine (local) + Redis (distribuida).
- **Comunicación entre servicios** por `/api/internal/**` con un token interno firmado, protegida con
  **Resilience4j** (circuit breaker, reintentos y timeouts).
- **Rutas**: públicas bajo `/api/v1/**`, internas bajo `/api/internal/**`, y las imágenes en `/{recurso}/images/{archivo}`.
- **Permisos por área**, configurables por rol en cada tienda (`ADMINISTRADOR`, `EMPLEADO`, `CLIENTE`).

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
El script `backend/deploy/init.sql` crea las bases `users` y `products`; las tablas las genera Hibernate al arrancar.

### 2. Backend

**Con Docker (recomendado):** un solo comando levanta PostgreSQL, Redis, Eureka, los tres servicios y carga el seed
de desarrollo:

```bash
docker compose -f backend/deploy/docker-compose.dev.yml up -d
```

La primera vez tarda unos minutos (cada servicio baja sus dependencias y compila con `gradle bootRun`). Los servicios
quedan `healthy` cuando Spring ya arrancó; el panel de Eureka está en `http://localhost:8761`.

**Seed de desarrollo:** el servicio `seed` da de alta la tienda `demo` por la API (mismo flujo que el autoservicio) y
termina. Se puede correr las veces que haga falta: si la tienda ya existe no hace nada. Además `decorusers` crea un
superadmin al arrancar. Usuarios de prueba (solo para desarrollo, definidos en `docker-compose.dev.yml`):

| Usuario | Email | Contraseña | Dónde |
|---|---|---|---|
| Dueño de la tienda demo | `admin@demo.local` | `demo123` | `http://demo.localhost:2999` |
| Superadmin de la plataforma | `superadmin@decor.local` | `superadmin123` | `http://localhost:2999` |

**Sin Docker:** cada servicio es un proyecto Gradle independiente (con su propio `gradlew`) que consume `decor-common`
como build compuesto. Levantá primero la base y Redis (`docker compose -f backend/deploy/docker-compose.bd.yml up -d`)
y después los servicios **en este orden**, cada uno en su terminal:

```bash
cd backend/eureka          && ./gradlew bootRun   # 1. descubrimiento  (8761)
cd backend/decorusers      && ./gradlew bootRun   # 2. usuarios        (3001)
cd backend/com.decor.product && ./gradlew bootRun # 3. productos       (3002)
cd backend/gateway         && ./gradlew bootRun   # 4. gateway         (3000)
```

(En Windows usá `gradlew.bat bootRun`.)

La configuración está en `src/main/resources/application.properties` de cada servicio. Antes de usarlo en serio,
cambiá los secretos (`jwt.secret`, `api.internal.key`, contraseñas de base de datos y del mailer).

Para crear el primer superadmin de la plataforma, definí las variables de entorno `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD`
(ver `platform.superadmin.*` en `decorusers`) antes de arrancar `decorusers`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev        # abre http://demo.localhost:2999 (la tienda demo del seed)
```

El frontend le habla al gateway según `VITE_API_BASE_PUBLIC` en `frontend/.env` (por defecto `http://localhost:3000`).

El gateway reconoce la tienda por el `Origin` del navegador, así que la URL del tenant tiene que coincidir con la del frontend
(`http://<tienda>.localhost:2999`). Por eso `platform.domain` en `decorusers` y `platform.url` en el gateway usan el puerto 2999.

### Tenants en desarrollo

La URL de cada tienda es `<identificador>.<dominio>`. En desarrollo los navegadores resuelven `*.localhost` solos,
así que una tienda llamada `mitienda` se ve en `http://mitienda.localhost:2999`.

---

## Cómo está organizado

```
decor/
├── backend/                  Microservicios Java (cada carpeta es un proyecto Gradle)
│   ├── gateway/              API Gateway
│   ├── decorusers/           Usuarios, auth, roles y tenants
│   ├── com.decor.product/    Productos, stock y catálogo
│   ├── decor-common/         Librería común
│   ├── eureka/               Servidor de descubrimiento
│   └── deploy/               Docker Compose (PostgreSQL, Redis) e init.sql
├── frontend/                 Aplicación web (Vite + React)
│   ├── src/pages/            Páginas: catálogo, login, registro, ajustes y panel de administración
│   ├── src/components/       Componentes de la aplicación y de UI (shadcn/ui)
│   ├── src/lib/ src/hooks/   Contexto de auth, tipos, textos y hooks
│   └── public/               Recursos estáticos
├── docs/                     Documentación y planes por módulo
│   ├── endpoints.md          Endpoints de la API
│   ├── tenants.md            Cómo funcionan los tenants
│   ├── permisos.md           Permisos por área y rol
│   ├── ventas.md             Plan del módulo de ventas
│   ├── panel-admin.md        Plan del panel de administración
│   ├── auditoria.md          Plan de auditoría
│   └── observabilidad.md     Plan de observabilidad
├── MVP.md                    Estado actual y propuesta de funcionalidades
├── TODO                      Pendientes y cosas a tener en cuenta
└── .gitignore                Reglas de ignore comunes a todo el repo
```

### Ramas

- `main`: el proyecto completo (backend + frontend).
- `develop` y `frontend`: ramas de trabajo previas, ya integradas en `main`.

---

## Documentación

- [MVP.md](MVP.md): análisis del proyecto y hoja de ruta.
- [docs/endpoints.md](docs/endpoints.md): referencia de la API.
- [docs/tenants.md](docs/tenants.md) y [docs/permisos.md](docs/permisos.md): multi-tenant y permisos.
- [docs/ventas.md](docs/ventas.md), [docs/panel-admin.md](docs/panel-admin.md), [docs/auditoria.md](docs/auditoria.md),
  [docs/observabilidad.md](docs/observabilidad.md): planes de los próximos módulos.
