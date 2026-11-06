# Next features

What is still to be built. The rest of the documentation describes what already exists; server tasks and technical
debt are in [server-tasks.md](server-tasks.md).

## B. Frontend

### B8. Per-item history
- The history of one thing inside its own screen (the order page, editing a product or a user) with
  `GET /observability/store/audit/{entity}/{id}`. The endpoint already exists.

### B10. Small gaps
- **Margin on prices**: show `margin` and `marginPercent` (already in `GET /products/{id}/pricing`) next to the price
  and the cost, only with `PRECIOS`.
- **Best sellers**: show `soldCount` in the panel and add the `best_sellers` sort to the catalog (the backend already
  accepts it).
- **Shared empty and loading states**: today each page builds its own (`empty`, `skeleton` and `spinner` exist in
  `components/ui` but are barely used).
- **Platform**: landing page and public store sign-up (today `/` redirects to `/dashboard` and stores are created from
  the superadmin panel; the self-service endpoint already exists).

### B9. Quality
- Types for every response (the files in `docs/backend/` have the examples).
- Frontend tests for permissions and the main flows.
- Basic accessibility: focus, labels, contrast.

### B11. Publishing
- Measure page performance (Lighthouse) and submit each store's sitemap to Search Console.
- Have a lawyer review the `/privacidad` and `/terminos` texts (`lib/strings.ts`, `legal`).

### B12. Per-store settings
- Configurable store texts (name, theme, icon and social links are already done).
- Let each visitor choose light or dark (today the store decides it with the "Noche" theme).
- Configurable emails per store.
- Profile pictures for superadmins.

## C. In-store management

Nothing here has been decided. Each item is planned with questions before implementing it.

### C1. Next sales stage
1. **P1 – `VENTAS` area (bit 32)**: let an `EMPLEADO` use the sales panel. users adds the bit; sales goes from
   `ADMINISTRADOR` only to `PERM_VENTAS`; the frontend adds it to the permission matrix.
2. **P2 – product code or barcode**: a `code` field in products, unique per store; search by code; a CSV column; a
   counter search box that accepts a scanner.
3. **P5 – visible reserved stock**: "3 in stock, 1 reserved by an online order", in the panel and in the in-store
   sale.
4. **P6 – pay on pick-up**: payment method when validating an online order and the move to `PAGADO` without a
   receipt.

### C2. Cash register and price by payment method
- **P3 – Cash register**: opening and closing per shift, cash count (expected against actual), income and expenses
  that are not sales.
- **P4 – Price by payment method**: discount for cash or transfer, surcharge for credit or installments.

### C3. Later
- **P7–P9**: a single customer record, deposits and balance, quotes and custom orders.
- **P10–P11**: suppliers and purchases; expenses and monthly result.
- **P12–P15**: electronic invoicing (ARCA), WhatsApp notifications, in-store use (tablet, ticket, offline), several
  branches.

### C4. Sales "for later"
- Mercado Pago with a webhook.
- Email notifications when an order changes status (the confirmation on creation is already done).
- Printable (non-fiscal) receipt for in-store sales.
- Export reports to CSV.
- Time zone per store (today there is one for the whole service).
- Shipping cost and logistics.
- Stock and sold counts through outbox events (fixes the rollbacks that today are only logged).

### Loose ideas from the panels
- Actionable "pending tasks" in `/admin`: products without a price or image, bundles that cannot be assembled.
- "Active" users by last request (today it is the last login within 30 days).

## F. Future

### F1. Packaged distribution
Run KleinKauf without installing everything by hand (Java, Node, Postgres, Redis and each service separately), for
example for a store that wants it on its own machine or to try it out quickly. Options to evaluate:

- **Published images** (for example on GHCR) and a single `docker-compose` with everything, so Docker and one command
  are enough.
- **An installer or executable** that bundles the runtime and dependencies, without Docker.

To decide: the scope (a single store or the whole platform), the first-time setup (domain, superadmin, email) and how
it gets updated.
