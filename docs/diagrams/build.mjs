// Generates the documentation diagrams as SVG (light and dark, following prefers-color-scheme).
// Edit the diagram below and run: node docs/diagrams/build.mjs
import { writeFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const OUT = dirname(fileURLToPath(import.meta.url))

const CSS = `
svg{--bg:#fafaf9;--card:#ffffff;--border:#d6d3d1;--ink:#1c1917;--muted:#78716c;--line:#a8a29e;--accent:#1677bd;--wash:#e8f2fa;--wash-border:#9cc6e6;--data:#f5f5f4;--paid:#e7f5ec;--paid-border:#8cc9a2;--final:#f1f0ef}
@media (prefers-color-scheme: dark){svg{--bg:#0f1419;--card:#1a2027;--border:#3a434d;--ink:#e6edf3;--muted:#9aa4af;--line:#5f6b77;--accent:#2b7fc4;--wash:#13283a;--wash-border:#2f5f88;--data:#1f262e;--paid:#14291d;--paid-border:#2f6b45;--final:#22282f}}
text{font-family:-apple-system,"Segoe UI",Inter,Helvetica,Arial,sans-serif;fill:var(--ink)}
.bg{fill:var(--bg)}
.box{fill:var(--card);stroke:var(--border);stroke-width:1}
.accent{fill:var(--accent);stroke:none}
.data{fill:var(--data);stroke:var(--border);stroke-width:1}
.client{fill:var(--wash);stroke:var(--wash-border);stroke-width:1}
.note{fill:none;stroke:var(--line);stroke-width:1;stroke-dasharray:4 4}
.paid{fill:var(--paid);stroke:var(--paid-border);stroke-width:1}
.final{fill:var(--final);stroke:var(--muted);stroke-width:1.5}
.head{fill:var(--wash)}
.sep{stroke:var(--border);stroke-width:1}
.t{font-size:14px;font-weight:600}
.s{font-size:12px;fill:var(--muted)}
.m{font-family:ui-monospace,SFMono-Regular,Consolas,Menlo,monospace;font-size:12px}
.mt{font-family:ui-monospace,SFMono-Regular,Consolas,Menlo,monospace;font-size:12.5px;font-weight:600}
.r{font-size:12.5px}
.on-accent{fill:#ffffff}
.on-accent-s{fill:#dbeaf7;font-size:12px}
.lane{font-size:12px;font-weight:600;fill:var(--muted);letter-spacing:.06em;text-transform:uppercase}
.badge{fill:var(--accent)}
.e{fill:none;stroke:var(--line);stroke-width:1.5}
.dash{stroke-dasharray:5 4}
.ah{fill:var(--line)}
.l{font-size:11.5px;fill:var(--muted);paint-order:stroke;stroke:var(--bg);stroke-width:4px;stroke-linejoin:round}
.lp{stroke:var(--card)}
`

const MARKERS = `<marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerUnits="userSpaceOnUse" markerWidth="9" markerHeight="9" orient="auto-start-reverse"><path class="ah" d="M0 0L10 5L0 10z"/></marker>`

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;")
const lines = (v) => (v == null ? [] : Array.isArray(v) ? v : [v])

function doc(name, w, h, title, body) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(title)}">
<title>${esc(title)}</title>
<style>${CSS}</style>
<defs>${MARKERS}</defs>
<rect class="bg" width="${w}" height="${h}" rx="12"/>
${body.flat(Infinity).join("\n")}
</svg>
`
  writeFileSync(join(OUT, `${name}.svg`), svg)
}

function text(x, y, s, cls, anchor = "middle") {
  return `<text x="${x}" y="${y}" class="${cls}" text-anchor="${anchor}">${esc(s)}</text>`
}

// A box with a title and optional sub lines, centered
function box(x, y, w, h, { title, sub, kind = "box", mono = false, rx = 8 }) {
  const subs = lines(sub)
  const total = 17 + subs.length * 15
  const top = y + (h - total) / 2
  const cx = x + w / 2
  const onAccent = kind === "accent"
  const out = [`<rect class="${kind}" x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}"/>`]
  out.push(text(cx, top + 13, title, mono ? `mt${onAccent ? " on-accent" : ""}` : `t${onAccent ? " on-accent" : ""}`))
  subs.forEach((s, i) => out.push(text(cx, top + 30 + i * 15, s, onAccent ? "on-accent-s" : "s")))
  return out
}

// A state pill
function pill(x, y, w, label, kind = "box") {
  return [`<rect class="${kind}" x="${x}" y="${y}" width="${w}" height="36" rx="18"/>`, text(x + w / 2, y + 22, label, "mt")]
}

// A connector through the given points; arrow at the end (or both ends)
function edge(points, { label, at, anchor = "middle", dashed = false, both = false, end = true } = {}) {
  const d = points.map(([px, py], i) => `${i ? "L" : "M"}${px} ${py}`).join(" ")
  const out = [`<path class="e${dashed ? " dash" : ""}" d="${d}"${end ? ' marker-end="url(#a)"' : ""}${both ? ' marker-start="url(#a)"' : ""}/>`]
  for (const [i, l] of lines(label).entries()) out.push(text(at[0], at[1] + i * 14, l, "l", anchor))
  return out
}

// A card with a title strip and rows. A row is a string, or [name, description lines] for a two-column row.
// Returns the drawing and its height.
function panel(x, y, w, { title, rows, mono = false, nameWidth = 0, numbered = false }) {
  const out = []
  const body = []
  let cy = y + 44
  rows.forEach((row, i) => {
    const [name, desc] = Array.isArray(row) ? row : [null, row]
    const ds = lines(desc)
    const n = Math.max(ds.length, 1)
    const rowTop = cy - 15
    if (i > 0 && nameWidth) body.push(`<line class="sep" x1="${x + 16}" x2="${x + w - 16}" y1="${rowTop - 6}" y2="${rowTop - 6}"/>`)
    let tx = x + 18
    if (numbered) {
      body.push(`<circle class="badge" cx="${tx + 9}" cy="${cy - 4}" r="10"/>`, text(tx + 9, cy, String(i + 1), "s on-accent"))
      tx += 30
    }
    if (name != null) body.push(text(tx, cy, name, "mt", "start"))
    ds.forEach((d, j) => body.push(text(name != null ? x + nameWidth : tx, cy + j * 18, d, mono ? "m" : "r", "start")))
    cy += n * 18 + (nameWidth ? 16 : 6)
  })
  const h = cy - y - (nameWidth ? 8 : 0)
  out.push(`<rect class="box" x="${x}" y="${y}" width="${w}" height="${h}" rx="10"/>`)
  out.push(`<path class="head" d="M${x + 1} ${y + 10}a9 9 0 0 1 9-9h${w - 20}a9 9 0 0 1 9 9v16h-${w - 2}z"/>`)
  out.push(`<line class="sep" x1="${x}" x2="${x + w}" y1="${y + 26}" y2="${y + 26}"/>`)
  out.push(text(x + 16, y + 18, title, "mt", "start"))
  return { svg: [...out, ...body], h }
}

// ---------------------------------------------------------------------------------------------------------------
// Architecture (README and docs/architecture.md)
{
  const s = [140, 370, 600, 830] // service centers
  doc("architecture", 960, 500, "KleinKauf architecture", [
    box(40, 40, 160, 56, { title: "Browser", sub: "store or platform", kind: "client" }),
    edge([[200, 68], [330, 68]]),
    box(330, 40, 300, 56, { title: "Frontend", sub: "Vite + React · :2999" }),
    edge([[480, 96], [480, 150]], { label: "HTTP · WebSocket", at: [490, 127], anchor: "start" }),
    box(40, 150, 180, 60, { title: "Eureka", sub: "service discovery · :8761" }),
    edge([[220, 180], [330, 180]], { dashed: true, end: false, label: "lb://", at: [275, 174] }),
    box(330, 150, 300, 60, { title: "Gateway", sub: "Spring Cloud Gateway · :3000", kind: "accent" }),
    edge([[630, 180], [740, 180]], { both: true }),
    box(740, 150, 180, 60, { title: "Redis", sub: "cache · rate limit · events", kind: "data" }),
    edge([[480, 210], [480, 245]], { end: false }),
    edge([[s[0], 245], [s[3], 245]], { end: false }),
    s.map((x) => edge([[x, 245], [x, 280]])),
    box(55, 280, 170, 70, { title: "users", sub: ["kleinkaufusers", ":3001"] }),
    box(285, 280, 170, 70, { title: "products", sub: ["kleinkauf-product", ":3002"] }),
    box(515, 280, 170, 70, { title: "sales", sub: ["kleinkauf-sales", ":3003"] }),
    box(745, 280, 170, 70, { title: "observability", sub: ["kleinkauf-observability", ":3004"] }),
    edge([[285, 322], [225, 322]], { label: "internal", at: [255, 312] }),
    edge([[515, 322], [455, 322]], { label: "internal", at: [485, 312] }),
    s.map((x) => edge([[x, 350], [x, 410]])),
    box(55, 410, 860, 56, { title: "PostgreSQL", sub: "one database per service · one schema per store", kind: "data" }),
  ])
}

// Backend overview (docs/backend/README.md)
{
  doc("backend-overview", 960, 640, "Backend services and how they talk to each other", [
    box(220, 30, 250, 56, { title: "Store frontend", sub: "http://<store>.localhost:2999", kind: "client" }),
    box(490, 30, 250, 56, { title: "Platform frontend", sub: "http://localhost:2999", kind: "client" }),
    edge([[345, 86], [345, 108], [450, 108], [450, 130]]),
    edge([[615, 86], [615, 108], [510, 108], [510, 130]]),
    box(355, 130, 250, 60, { title: "gateway :3000", sub: "single public entry point", kind: "accent" }),
    edge([[605, 160], [720, 160]], { both: true }),
    box(720, 130, 200, 60, { title: "Redis", sub: "cache · rate limit · events", kind: "data" }),
    edge([[480, 190], [480, 235]], { end: false, label: "Eureka (lb://)", at: [490, 218], anchor: "start" }),
    edge([[160, 235], [800, 235]], { end: false }),
    [160, 480, 800].map((x) => edge([[x, 235], [x, 280]])),
    box(60, 280, 200, 64, { title: "kleinkaufusers", sub: "users, auth, roles, tenants · :3001" }),
    box(380, 280, 200, 64, { title: "products", sub: "catalog and stock · :3002" }),
    box(700, 280, 200, 64, { title: "kleinkauf-sales", sub: "orders and payments · :3003" }),
    edge([[380, 312], [260, 312]], { label: "roles · tenants", at: [320, 302] }),
    edge([[700, 312], [580, 312]], { label: "reservations · sold", at: [640, 302] }),
    edge([[760, 344], [760, 372], [200, 372], [200, 344]], { label: "roles · order email", at: [640, 366] }),
    [160, 480, 800].map((x) => edge([[x, 344], [x, 410]])),
    box(60, 410, 200, 56, { title: "Postgres “users”", sub: "schema per store + public", kind: "data" }),
    box(380, 410, 200, 56, { title: "Postgres “products”", sub: "schema per store", kind: "data" }),
    box(700, 410, 200, 56, { title: "Postgres “sales”", sub: "schema per store", kind: "data" }),
    box(40, 500, 400, 72, { title: "kleinkauf-observability :3004", sub: ["health, metrics, events, audit log and WebSocket", "Postgres “observability”"], kind: "note" }),
    box(460, 500, 210, 72, { title: "eureka :8761", sub: "service registry", kind: "note" }),
    box(690, 500, 230, 72, { title: "kleinkauf-common", sub: "shared library (not a service)", kind: "note" }),
    text(480, 610, "Internal calls go to /api/internal/** with a signed internal token, through Eureka, with Resilience4j.", "s"),
  ])
}

// Order states (docs/features/sales.md and docs/backend/sales.md)
{
  const mid = 128 // main row center
  doc("order-states", 1080, 350, "Order states", [
    pill(30, 110, 130, "PENDIENTE_PAGO"),
    edge([[160, mid], [280, mid]], { label: "uploads receipt", at: [220, mid - 8] }),
    pill(280, 110, 145, "PAGO_EN_REVISION"),
    edge([[425, mid], [515, mid]], { label: "validates", at: [470, mid - 8] }),
    pill(515, 110, 75, "PAGADO", "paid"),
    edge([[590, mid], [640, mid]]),
    pill(640, 110, 130, "EN_PREPARACION", "paid"),
    edge([[770, mid], [820, mid]]),
    pill(820, 110, 70, "LISTO", "paid"),
    edge([[890, mid], [940, mid]]),
    pill(940, 110, 105, "ENTREGADO", "final"),
    edge([[95, 110], [95, 70], [552, 70], [552, 110]], { label: "validates without a receipt (paid some other way)", at: [323, 64] }),
    edge([[335, 146], [335, 210]], { label: "rejects (with a reason)", at: [327, 182], anchor: "end" }),
    edge([[370, 210], [370, 146]], { label: "uploads again", at: [378, 182], anchor: "start" }),
    pill(290, 210, 125, "RECHAZADO"),
    edge([[415, 228], [552, 228], [552, 146]], { label: "validates", at: [484, 222] }),
    edge([[95, 146], [95, 210]], { label: "deadline passes", at: [103, 182], anchor: "start" }),
    edge([[290, 228], [150, 228]], { label: "deadline passes", at: [220, 222] }),
    pill(40, 210, 110, "VENCIDO", "final"),
    pill(640, 210, 120, "CANCELADO", "final"),
    text(775, 224, "from any state before ENTREGADO, with a reason", "s", "start"),
    text(775, 240, "(an in-store sale can also be voided once ENTREGADO)", "s", "start"),
    `<rect class="box" x="30" y="290" width="14" height="14" rx="4"/>`, text(52, 302, "waiting for payment · stock reserved", "s", "start"),
    `<rect class="paid" x="300" y="290" width="14" height="14" rx="4"/>`, text(322, 302, "paid · stock deducted, counts as a sale", "s", "start"),
    `<rect class="final" x="590" y="290" width="14" height="14" rx="4"/>`, text(612, 302, "final", "s", "start"),
    text(30, 330, "In-store sales start as PAGADO or ENTREGADO.", "s", "start"),
  ])
}

// Sales request flow (docs/features/sales.md and docs/backend/sales.md)
{
  const gw = panel(160, 110, 640, {
    title: "gateway :3000",
    rows: [
      "public routes: GET /checkout, POST /orders, POST /orders/lookup,",
      "and POST /orders/{number}/receipt, POST /orders/{number}/cancel",
      "POST /orders: a valid JWT adds X-User-Id; an invalid one → 401",
      "rate limit by IP: 5 orders/min, 10 lookups or cancellations/min, 5 receipts/min",
      "adds X-Forwarded-Host (the store's schema)",
    ],
  })
  const salesY = 110 + gw.h + 44
  const sales = panel(160, salesY, 640, {
    title: "kleinkauf-sales :3003",
    nameWidth: 180,
    rows: [
      ["TenantFilter", "X-Forwarded-Host → schema (without it, 404)"],
      ["HeaderAuthFilter", "X-User-Id → roles (cache 1 min → Redis → users)"],
      ["ExpirationJob", "every hour, per store: expires orders and returns their stock"],
    ],
  })
  const tY = salesY + sales.h + 70
  const products = panel(40, tY, 430, {
    title: "products :3002",
    mono: true,
    rows: [
      "POST /api/internal/products/reservations",
      "POST …/reservations/{number}/release",
      "POST …/reservations/{number}/sold",
      "POST …/reservations/{number}/unsold",
    ],
  })
  const users = panel(500, tY, 420, {
    title: "users :3001",
    mono: true,
    rows: ["GET /api/internal/users/roles/{id}", "POST /api/internal/mail/order-confirmation", "(order email: in the background, no retries)"],
  })
  const bottom = tY + Math.max(products.h, users.h)
  const sb = salesY + sales.h
  doc("sales-flow", 960, bottom + 74, "How a sales request flows", [
    box(330, 30, 300, 50, { title: "Store frontend", sub: "Origin: http://my-store.localhost:2999", kind: "client" }),
    edge([[480, 80], [480, 110]]),
    gw.svg,
    edge([[480, 110 + gw.h], [480, salesY]]),
    sales.svg,
    edge([[330, sb], [330, sb + 35], [255, sb + 35], [255, tY]]),
    edge([[630, sb], [630, sb + 35], [710, sb + 35], [710, tY]]),
    products.svg,
    users.svg,
    text(480, bottom + 32, "Internal calls carry X-Internal-Token and the store's X-Forwarded-Host, go through Eureka with 2 s / 3 s timeouts,", "s"),
    text(480, bottom + 50, "and the ones to products use Resilience4j “products” (circuit breaker and retries).", "s"),
  ])
}

// Deployment (docs/deployment.md)
{
  doc("deployment", 960, 520, "How the server is laid out", [
    box(380, 30, 200, 44, { title: "Internet", kind: "client" }),
    edge([[480, 74], [480, 120]], { label: "80 / 443", at: [490, 101], anchor: "start" }),
    box(320, 120, 320, 60, { title: "Caddy", sub: "HTTPS (Let's Encrypt) · routing by host", kind: "accent" }),
    edge([[420, 180], [420, 220], [280, 220], [280, 260]], { label: "<domain> · <store>.<domain>", at: [270, 244], anchor: "end" }),
    edge([[540, 180], [540, 220], [680, 220], [680, 260]], { label: "api.<domain>", at: [690, 244], anchor: "start" }),
    box(150, 260, 260, 60, { title: "frontend", sub: "nginx · static app" }),
    box(550, 260, 260, 60, { title: "gateway", sub: ":3000" }),
    edge([[680, 320], [680, 370]]),
    `<rect class="note" x="450" y="370" width="460" height="120" rx="10"/>`,
    text(470, 392, "internal Docker network", "lane", "start"),
    [["users", 470], ["products", 575], ["sales", 680], ["observability", 785]].map(([n, x]) => box(x, 406, n === "observability" ? 110 : 95, 32, { title: n, rx: 6 })),
    [["Eureka", 470], ["PostgreSQL", 600], ["Redis", 730]].map(([n, x]) => box(x, 448, 120, 32, { title: n, kind: n === "Eureka" ? "box" : "data", rx: 6 })),
  ])
}

// Audit log (docs/features/audit.md)
{
  doc("audit-flow", 960, 480, "How audit events travel", [
    text(40, 36, "products · users · sales", "lane", "start"),
    text(460, 36, "Redis", "lane", "middle"),
    text(640, 36, "kleinkauf-observability", "lane", "start"),
    `<line class="sep" x1="40" x2="300" y1="46" y2="46"/>`,
    `<line class="sep" x1="380" x2="540" y1="46" y2="46"/>`,
    `<line class="sep" x1="640" x2="920" y1="46" y2="46"/>`,
    box(40, 70, 260, 60, { title: "change + outbox row", sub: ["public.outbox_events", "same transaction"] }),
    edge([[170, 130], [170, 180]]),
    box(40, 180, 260, 60, { title: "OutboxPublisher", sub: ["every 2 s", "marks the row as sent"] }),
    edge([[300, 210], [380, 210]]),
    box(380, 180, 160, 60, { title: "Redis Streams", sub: "kleinkauf:audit", kind: "data" }),
    edge([[540, 210], [640, 210]]),
    box(640, 180, 280, 60, { title: "AuditConsumer", sub: "group observability-audit" }),
    edge([[780, 240], [780, 290]]),
    box(640, 290, 280, 56, { title: "<schema>.audit_events", kind: "data", mono: true }),
    edge([[780, 346], [780, 396]]),
    box(640, 396, 280, 56, { title: "WebSocket", sub: "store.audit · platform.audit", kind: "client" }),
  ])
}

// Permissions (docs/features/permissions.md)
{
  doc("permissions-flow", 960, 560, "How permissions reach each service", [
    text(30, 36, "users", "lane", "start"),
    text(930, 36, "products and sales", "lane", "end"),
    `<line class="sep" x1="30" x2="330" y1="46" y2="46"/>`,
    `<line class="sep" x1="630" x2="930" y1="46" y2="46"/>`,
    box(30, 66, 300, 50, { title: "role_permissions", sub: "per schema", kind: "data", mono: true }),
    edge([[180, 116], [180, 170]], { label: "RolePermissionService.maskFor(role)", at: [190, 147], anchor: "start" }),
    box(30, 170, 300, 50, { title: "RoleDto { type, permissions }", mono: true }),
    edge([[330, 195], [380, 195]]),
    box(380, 170, 200, 50, { title: "Redis", sub: "user:<id>:<tenant>", kind: "data" }),
    edge([[580, 195], [630, 195]]),
    box(630, 170, 300, 50, { title: "local cache", sub: "1 minute" }),
    edge([[180, 220], [180, 270]]),
    edge([[780, 220], [780, 270]]),
    box(30, 270, 300, 50, { title: "HeaderAuthFilter", mono: true }),
    box(630, 270, 300, 50, { title: "HeaderAuthFilter", mono: true }),
    edge([[180, 320], [180, 395], [255, 395]]),
    edge([[780, 320], [780, 395], [705, 395]]),
    box(255, 365, 450, 60, { title: "CustomUserDetails", sub: ["kleinkauf-common", "ROLE_<role> + PERM_<area>"], mono: true }),
    edge([[480, 425], [480, 470]]),
    box(255, 470, 450, 56, { title: "SecurityConfig", sub: "hasRole(...) · hasAuthority(\"PERM_STOCK\")", mono: true }),
  ])
}

// Tenant resolution (docs/features/tenants.md)
{
  const gw = panel(40, 110, 880, {
    title: "gateway",
    nameWidth: 300,
    numbered: true,
    rows: [
      ["ClientHeadersFilter", ["removes the internal headers sent by the client", "(X-User-Id, X-Forwarded-Host, X-Open-Path, X-Tenant-Name)"]],
      ["CorsConfig", ["CORS for store origins, platform subdomains and the platform frontend.", "Runs before the filters that reject requests, so their", "401 / 404 / 403 / 429 responses carry the CORS headers"]],
      ["RequestFilter", "rate limit by IP and origin (1-minute windows)"],
      ["MsFilter", "/api/internal/**: validates the internal token"],
      ["TenantGatewayFilter", ["looks up the tenant by Origin:", "local cache (1 min) → Redis tenant:url:<url> → users", "missing or deleted → 404 · disabled → 403", "adds X-Forwarded-Host: <schema> (skipped on /api/v1/platform/**)"]],
      ["JwtFilter", ["non-public routes: validates the JWT and that its \"tenant\" claim matches", "the request's schema (\"public\" on /api/v1/platform/**); adds X-User-Id.", "On POST /api/v1/sales/orders the token is optional, but validated if present"]],
    ],
  })
  const sy = 110 + gw.h + 44
  const svc = panel(40, sy, 880, {
    title: "users · products · sales",
    nameWidth: 300,
    rows: [
      ["TenantFilter", ["kleinkauf-common: reads X-Forwarded-Host and stores it in TenantContext"]],
      ["Hibernate multi-tenancy", ["TenantIdentifierResolver → schema from TenantContext", "DatabaseMultiTenantConnectionProvider → connection on that schema"]],
    ],
  })
  doc("tenant-resolution", 960, sy + svc.h + 30, "How the tenant is recognized on each request", [
    box(310, 30, 340, 50, { title: "Browser", sub: "Origin: http://my-store.localhost:2999", kind: "client" }),
    edge([[480, 80], [480, 110]]),
    gw.svg,
    edge([[480, 110 + gw.h], [480, sy]]),
    svc.svg,
  ])
}
