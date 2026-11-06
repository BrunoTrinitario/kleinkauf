# Getting started

How to run KleinKauf locally, load the demo data and run the tests.

## Requirements

- JDK 21
- Node.js 20 or later and npm
- Docker (for PostgreSQL and Redis, or for the whole backend)

## 1. Database and Redis

```bash
docker compose -f backend/deploy/docker-compose.bd.yml up -d
```

Starts PostgreSQL (`localhost:5432`, user and password `postgres`) and Redis (`localhost:6379`). The
`backend/deploy/init.sql` script creates the `users`, `products`, `sales` and `observability` databases; Hibernate
creates the tables on startup.

`init.sql` only runs the first time, with an empty Postgres volume. If the volume predates one of those databases, the
service that uses it keeps restarting. To create the missing ones without losing data:

```bash
for db in users products sales observability; do
  docker exec postgres-dev psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1 \
    || docker exec postgres-dev psql -U postgres -c "CREATE DATABASE $db"
done
```

(Or remove the volume with `docker compose -f backend/deploy/docker-compose.bd.yml down -v`, which deletes all data.)

## 2. Backend

### With Docker (recommended)

One command starts PostgreSQL, Redis, Eureka, the five services (users, products, sales, observability and gateway)
and loads the development seed:

```bash
docker compose -f backend/deploy/docker-compose.dev.yml up -d
```

The first time takes a few minutes (each service downloads its dependencies and compiles with `gradle bootRun`).
Services become `healthy` once Spring has started; the Eureka dashboard is at `http://localhost:8761`. Give the Docker
VM 6 GB of memory or more: with 4 GB the services compiling at the same time do not fit.

To add Prometheus, Grafana, Tempo and Loki, see [observability.md](features/observability.md#how-to-use-it).

### Without Docker

Each service is an independent Gradle project (with its own `gradlew`) that consumes `kleinkauf-common` as a composite
build. Start the database and Redis first (step 1) and then the services **in this order**, each in its own terminal:

```bash
cd backend/eureka                  && ./gradlew bootRun   # 1. discovery      (8761)
cd backend/kleinkaufusers          && ./gradlew bootRun   # 2. users          (3001)
cd backend/kleinkauf-product       && ./gradlew bootRun   # 3. products       (3002)
cd backend/kleinkauf-sales         && ./gradlew bootRun   # 4. sales          (3003)
cd backend/kleinkauf-observability && ./gradlew bootRun   # 5. observability  (3004)
cd backend/gateway                 && ./gradlew bootRun   # 6. gateway        (3000)
```

(On Windows use `gradlew.bat bootRun`.) `backend/settings.gradle` groups every project so the whole backend can be
imported into an IDE at once.

Configuration lives in each service's `src/main/resources/application.properties`. Before using it for real, change
the secrets (`jwt.secret`, `api.internal.key`, database and mail passwords). To create the first platform superadmin,
set `SUPERADMIN_EMAIL` and `SUPERADMIN_PASSWORD` before starting `kleinkaufusers`.

## 3. Development seed

The `seed` service (`backend/deploy/seed/seed.mjs`) loads everything through the API, using the same flows as the app,
and exits:

- creates the `demo` store (platform self-service);
- loads 48 products in 9 categories with price, cost, stock, metadata, two generic images per product and 6 featured.
  There are all kinds of cases: no price ("ask for price"), out of stock and low stock;
- creates 28 test users: 4 employees and 24 customers.

It can be run as many times as needed: whatever already exists (the store, a product with the same name, a user with
the same email) is skipped. To run it again:

```bash
docker compose -f backend/deploy/docker-compose.dev.yml up seed
```

The data is in `seed/data.json`; the images in `seed/images/` are generated with
`node backend/deploy/seed/generate-images.mjs` (they are already committed). `kleinkaufusers` also creates a
superadmin on startup. Test users (development only):

| User | Email | Password | Where |
|---|---|---|---|
| Owner of the demo store | `admin@demo.local` | `demo1234` | `http://demo.localhost:2999` |
| Demo store employees and customers | `firstname.lastname@demo.local` (e.g. `valentina.rios@demo.local`) | `cliente123` | `http://demo.localhost:2999` |
| Platform superadmin | `superadmin@kleinkauf.local` | `superadmin123` | `http://localhost:2999` |

## 4. Frontend

```bash
cd frontend
npm install
npm run dev        # opens http://demo.localhost:2999 (the seed's demo store)
```

The frontend talks to the gateway at `VITE_API_BASE_PUBLIC` in `frontend/.env` (`http://localhost:3000` by default).

The gateway recognizes the store by the browser's `Origin`, so the tenant URL must match the frontend's
(`http://<store>.localhost:2999`). That is why `platform.domain` in `kleinkaufusers` and `platform.url` in the gateway
use port 2999.

### Stores in development

Each store's URL is `<identifier>.<domain>`. In development browsers resolve `*.localhost` on their own, so a store
called `mystore` is at `http://mystore.localhost:2999`. The platform (superadmin panel) is at `http://localhost:2999`.

## Tests

Each service runs its own with `./gradlew test` (on Windows, `gradlew.bat test`). The ones that start the whole app
(`@SpringBootTest`) use **Testcontainers**: they start Postgres and Redis in Docker on their own, so the database does
not need to be running. Without Docker those tests are skipped and the rest run anyway.

**CI** (`.github/workflows/ci.yml`): on every push to `main` and every PR against `main` it runs each service's tests
(with Testcontainers) and the frontend build (`tsc -b` and `vite build`). It does not deploy.

**End-to-end test** (`backend/deploy/e2e/e2e.mjs`): with everything started by the development compose, it goes
through the API: store sign-up, session (login, refresh, logout), products and images, the catalog, a guest order, a
bug report and the platform panel. It creates a new store and deletes it at the end; it exits with code 1 if anything
fails:

```bash
docker compose -f backend/deploy/docker-compose.dev.yml --profile e2e up e2e
```

Against another environment, run it with Node 22 and the variables `GATEWAY_URL`, `PLATFORM_URL`, `STORE_SCHEME`,
`STORE_DOMAIN` and, to test the platform and delete the store, `SUPERADMIN_EMAIL` and `SUPERADMIN_PASSWORD`.
