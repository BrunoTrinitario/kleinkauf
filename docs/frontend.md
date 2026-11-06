# Frontend

A single-page app in `frontend/` that serves both the platform (superadmin) and every store. It talks only to the
gateway. The backend is described in the [API reference](backend/README.md).

## Stack

**Vite + React + TypeScript**, as an SPA.

| Piece | Choice | Why |
|---|---|---|
| Build | Vite | It compiles to static files: no Node server next to the Java services |
| Routing | React Router (library mode) | Standard, enough for nested and protected routes |
| Data | TanStack Query + axios | Caching, retries and loading/error states for the REST endpoints |
| UI | Tailwind + shadcn/ui (Radix) | Components in `components/ui`; the look follows [style-reference.md](style-reference.md) |
| Forms | react-hook-form + zod | |

**Why not Next.js:** the backend already does the server work (auth, data, caching). Next adds a Node runtime to
deploy that brings nothing to an admin panel.

**The one thing an SPA does not solve is link previews** (WhatsApp, Instagram): crawlers do not run JavaScript. It is
solved in the backend: products serves `GET /share/{store}/products/{slug}`, a tiny HTML page with the Open Graph tags
that redirects to the product page (see [backend/products.md](backend/products.md#share-link)). For search engines,
each public page sets its own title, description, Open Graph and canonical tags (`lib/page-meta.ts`), private pages
are `noindex`, and each store has its own `robots.txt` and `sitemap.xml` (see [deployment.md](deployment.md)).

## Key ideas

- **One build for the platform and the stores.** Depending on the host, the app shows the platform (`localhost`) or a
  store (`<store>.localhost`). `lib/platform.ts` decides by comparing the hostname with `VITE_PLATFORM_HOSTNAME`
  (`localhost` by default); `App.tsx` builds a different route tree for each.
- **The tenant is recognized by the `Origin` header**, which the browser sends on its own. That is why the frontend
  and the store URL must match: in development the frontend runs on 2999 and the gateway on 3000, `platform.domain`
  (users) is `localhost:2999` and `platform.url` (gateway) is `http://localhost:2999`, so stores open at
  `http://<store>.localhost:2999`.
- **Single HTTP client** (`lib/api.ts`): base URL from `VITE_API_BASE_PUBLIC`, the `Authorization` header and central
  handling of 401 / 403 / 404 / 429. A 401 tries the refresh token once and otherwise logs out and sends the user to
  `/login?next=<page>`. If the gateway says the store does not exist (404) or is disabled (403), the app shows a page
  instead of the routes. A burst of 429s shows a single "too many attempts" notice.
- **Data hooks per domain** (`src/api/*.ts`: catalog, products, bundles, users, account, sales, store, dashboard,
  observability, platform, bug-reports). Mutations invalidate what they affect (a new product shows up in the panel and
  in the catalog) and the cache is cleared on login and logout.
- **Session**: `lib/auth-context.tsx` holds the user, roles and permission mask, with `can('STOCK')`-style helpers
  (`lib/permissions.ts`). A user with several roles can switch the **active role**; route guards and the menu follow
  it. The superadmin has no `/auth/me`, so the platform keeps the user in `localStorage` (`platform_user`).
- **Route guards** (`components/route-guards.tsx`): `RequireAuth`, `RequireRole`, `RequireArea` and
  `RequireSuperadmin`. The UI hides what the user cannot use; the backend validates anyway.
- **Store branding**: name, one of 7 color themes (6 light and the dark "Noche"), icon and social links come from
  `GET /api/v1/catalog/store`. `lib/store-themes.ts` overrides the CSS variables of `index.css` and remembers the theme
  in the browser so the page does not start with other colors.
- **Texts** are Spanish and centralized in `lib/strings.ts`; currency in pesos, local dates.
- **Real time**: `hooks/use-live-channels.ts` is the WebSocket client (single-use ticket, ping every 30 s, reconnect
  with growing back-off, no retry after a 403 or a 1008 close). History is loaded over REST when a panel opens and the
  WS only brings what is new. See [observability.md](features/observability.md#websocket).

## Pages

### Platform (`localhost:2999`)

| Path | What it is |
|---|---|
| `/login` | Superadmin login (`/` redirects to `/dashboard`) |
| `/dashboard` | Summary: tenants by state, sign-ups per period and usage per tenant |
| `/tenants` | Tenant list with search and state filter; create, enable/disable and delete |
| `/superadmins` | Superadmin list; create with a temporary password, deactivate and reactivate |
| `/bug-reports` | Problems reported from the stores, to mark as resolved |
| `/estado` | Service health (instances, components, circuit breakers), traffic and resources per service, live chart, connected users per store |
| `/auditoria` | Platform audit log |

### Store (`<store>.localhost:2999`)

| Path | Who | What it is |
|---|---|---|
| `/` | Everyone | Storefront: featured products and bundles in carousels, plus the first page of the catalog |
| `/catalogo` | Everyone | Full catalog with search, category, sort and page in the URL (`?q=&categoria=&orden=&pagina=`) |
| `/combos` | Everyone | Bundles with their products, savings and a breakdown of each product |
| `/productos/:slug` | Everyone | Product page (shareable), with share buttons |
| `/carrito`, `/checkout` | Everyone | Cart (kept in `localStorage`) and checkout as guest or logged in, pick-up or shipping |
| `/pedido/:number`, `/pedido` | Everyone | Order page (transfer details, countdown, receipt upload, cancel, history) and guest lookup by number and email |
| `/login`, `/register`, `/recuperar`, `/reset-password` | Everyone | Session, sign-up as `CLIENTE` and password recovery |
| `/privacidad`, `/terminos` | Everyone | Privacy policy and terms |
| `/user/settings`, `/mis-pedidos` | Logged in | Account (name, password, profile photo, delete account) and "my orders" |
| `/admin` | `ADMINISTRADOR` | Store summary: sales, products, stock and users |
| `/admin/orders`, `/admin/orders/:number`, `/admin/orders/new` | `ADMINISTRADOR` | Orders with filters, order detail with status changes, and in-store sale |
| `/admin/reports` | `ADMINISTRADOR` | Sales reports by period, channel and payment method, with product ranking |
| `/admin/actividad` | `ADMINISTRADOR` | Live activity, today's usage and connected users |
| `/admin/auditoria` | `ADMINISTRADOR` | Store audit log with filters and CSV export |
| `/admin/store` | `ADMINISTRADOR` | Store settings: name, theme, icon and social links |
| `/admin/products`, `/admin/bundles` | Any panel area | Products (stock movements, featured, CSV import/export, prices with `PRECIOS`) and bundles |
| `/admin/users` | `USUARIOS` | Users, their roles and the permission matrix per role |

The footer of every store page has the social links, a "report a problem" link and the legal pages.

## Running and building

```bash
cd frontend
npm install
npm run dev        # http://demo.localhost:2999 (the seed's demo store)
npm run build      # tsc -b && vite build
```

`frontend/.env` sets `VITE_API_BASE_PUBLIC` (the gateway, `http://localhost:3000` by default). In production a push to
`deploy/frontend` builds the image (served by nginx) and deploys it; see [deployment.md](deployment.md).
