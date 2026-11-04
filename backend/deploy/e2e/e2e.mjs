// Prueba de punta a punta: todo junto (gateway, users, products, sales, Postgres y Redis reales), por la API y con
// los mismos flujos que usa la app. Crea una tienda nueva para cada corrida y, si tiene el superadmin, la borra al
// terminar. Sale con codigo 1 si algun paso falla.
//   docker compose -f backend/deploy/docker-compose.dev.yml --profile e2e up e2e
// Variables: GATEWAY_URL, PLATFORM_URL, STORE_SCHEME, STORE_DOMAIN y, opcionales, SUPERADMIN_EMAIL y SUPERADMIN_PASSWORD
// (sin ellas no se prueba el panel de la plataforma ni se borra la tienda).

const env = (name, optional = false) => {
  const value = process.env[name]
  if (!value && !optional) throw new Error(`Falta la variable ${name}`)
  return value
}
const GATEWAY = env("GATEWAY_URL")
const PLATFORM = env("PLATFORM_URL")
const SUPERADMIN_EMAIL = env("SUPERADMIN_EMAIL", true)
const SUPERADMIN_PASSWORD = env("SUPERADMIN_PASSWORD", true)

const RUN = Date.now().toString(36)
const IDENTIFIER = `e2e-${RUN}`
const STORE = `${env("STORE_SCHEME")}://${IDENTIFIER}.${env("STORE_DOMAIN")}`
const OWNER = { name: "Dueño E2E", email: `dueno-${RUN}@e2e.local`, password: "e2eclave123" }

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// Una llamada al gateway. Devuelve status y cuerpo (JSON si se puede); no tira por el status: lo mira cada paso
async function call(method, path, { origin = STORE, token, json, form, headers: extra = {} } = {}) {
  const headers = { Origin: origin, ...extra }
  if (token) headers.Authorization = `Bearer ${token}`
  let body
  if (json !== undefined) {
    headers["Content-Type"] = "application/json"
    body = JSON.stringify(json)
  } else if (form) {
    body = form
  }
  const response = await fetch(GATEWAY + path, { method, headers, body })
  const text = await response.text()
  let parsed = text || null
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {}
  return { status: response.status, body: parsed }
}

// Espera a que el gateway vea a users (recien levantado, Eureka tarda en enterarse de los servicios)
async function waitForGateway() {
  for (let attempt = 1; attempt <= 60; attempt++) {
    try {
      const { status } = await call("POST", "/api/v1/platform/auth/login", { origin: PLATFORM, json: {} })
      if (status < 500) return
    } catch {}
    await sleep(5000)
  }
  throw new Error("El gateway no respondio a tiempo")
}

let failures = 0
let step = 0

function check(description, condition, detail) {
  step++
  if (condition) {
    console.log(`  ok ${step} - ${description}`)
  } else {
    failures++
    console.log(`  FALLA ${step} - ${description}${detail === undefined ? "" : `: ${JSON.stringify(detail)}`}`)
  }
}

// Un paso del que dependen los siguientes: si falla, no tiene sentido seguir
function mustPass(description, condition, detail) {
  check(description, condition, detail)
  if (!condition) throw new Error(`No se puede seguir sin: ${description}`)
}

// PNG de 1x1 y un archivo que no es imagen aunque se llame .png
const PNG = Buffer.from(
  "89504e470d0a1a0a0000000d4948445200000001000000010806000000" +
    "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082",
  "hex"
)

function imageForm(bytes, name) {
  const form = new FormData()
  form.append("files", new Blob([bytes], { type: "image/png" }), name)
  return form
}

async function main() {
  console.log(`Prueba de punta a punta contra ${GATEWAY} (tienda ${IDENTIFIER})`)
  await waitForGateway()

  console.log("Gateway")
  check("una ruta que no existe responde 404", (await call("GET", "/api/v1/inventado", { origin: PLATFORM })).status === 404)
  check("/api/internal no se rutea desde afuera", (await call("GET", "/api/internal/tenants/all", { origin: PLATFORM })).status === 404)

  console.log("Alta de la tienda")
  const signup = await call("POST", "/api/v1/platform/tenants", {
    origin: PLATFORM,
    json: { identifier: IDENTIFIER, ownerName: OWNER.name, ownerEmail: OWNER.email, ownerPassword: OWNER.password },
  })
  mustPass("alta por autoservicio (201)", signup.status === 201, signup)
  const tenantId = signup.body.id

  console.log("Sesion")
  const login = await call("POST", "/api/v1/auth/login", {
    json: { email: OWNER.email.toUpperCase(), password: OWNER.password },
  })
  mustPass("login con el email en mayusculas", login.status === 200, login)
  const { token, refreshToken } = login.body
  check("el login devuelve el refresh token", typeof refreshToken === "string")
  check("GET /auth/me con el token", (await call("GET", "/api/v1/auth/me", { token })).status === 200)
  check("el refresh token no sirve para entrar", (await call("GET", "/api/v1/auth/me", { token: refreshToken })).status === 401)
  const refreshed = await call("POST", "/api/v1/auth/refresh", { json: { refreshToken } })
  check("renovar la sesion con el refresh", refreshed.status === 200 && !!refreshed.body?.token, refreshed)
  check("el token renovado sirve", (await call("GET", "/api/v1/auth/me", { token: refreshed.body?.token })).status === 200)
  // Cerrar sesion corta el refresh de esa sesion (esta en Redis)
  const toClose = (await call("POST", "/api/v1/auth/refresh", { json: { refreshToken: refreshed.body?.refreshToken } })).body?.refreshToken
  const logout = await call("POST", "/api/v1/auth/logout", { json: { refreshToken: toClose } })
  check("cerrar sesion corta el refresh", logout.status === 204
    && (await call("POST", "/api/v1/auth/refresh", { json: { refreshToken: toClose } })).status === 401, logout)
  check("un token de sesion no cambia la contrasena",
    (await call("POST", `/api/v1/auth/forgot-password/${token}`, { json: { newPassword: "otraclave123" } })).status === 401)

  console.log("Productos")
  const product = await call("POST", "/api/v1/products", {
    token,
    json: { name: "Mesa E2E", category: "mesas", description: "Mesa de prueba", stock: 5, minStock: 1, price: 1000, cost: 600 },
  })
  mustPass("crear un producto (201)", product.status === 201, product)
  const productId = product.body.id
  check("un nombre repetido responde 409", (await call("POST", "/api/v1/products", {
    token, json: { name: "Mesa E2E", stock: 0, minStock: 0 },
  })).status === 409)
  const partial = await call("PUT", `/api/v1/products/${productId}`, { token, json: { category: "comedor" } })
  check("PUT parcial: cambia la categoria y deja el nombre",
    partial.status === 200 && partial.body?.name === "Mesa E2E" && partial.body?.category === "comedor", partial)
  check("un archivo que no es imagen responde 415", (await call("POST", `/api/v1/products/${productId}/images`, {
    token, form: imageForm(Buffer.from("<svg onload=alert(1)>"), "falsa.png"),
  })).status === 415)
  check("subir una imagen PNG", (await call("POST", `/api/v1/products/${productId}/images`, {
    token, form: imageForm(PNG, "mesa.png"),
  })).status === 200)
  check("un combo que no existe responde 404", (await call("GET", "/api/v1/bundles/999999", { token })).status === 404)

  console.log("Catalogo y compra")
  const catalog = await call("GET", "/api/v1/catalog/products?q=mesa")
  const listed = catalog.body?.items?.find((item) => item.id === productId)
  check("el producto aparece en el catalogo publico", !!listed, catalog.body)
  const settings = await call("PUT", "/api/v1/sales/admin/settings", {
    token, json: { alias: "tienda.e2e", cbu: "0000003100000000000001", holder: "Tienda E2E", bank: "Banco", expirationHours: 48 },
  })
  check("cargar los datos de cobro", settings.status === 200, settings)
  const buyer = { name: "Comprador E2E", email: `comprador-${RUN}@e2e.local`, phone: "1155550000" }
  const order = await call("POST", "/api/v1/sales/orders", {
    json: {
      items: [{ type: "PRODUCT", id: productId, quantity: 2, price: listed?.price ?? 1000 }],
      buyer,
      deliveryType: "RETIRO",
    },
  })
  mustPass("pedido como invitado (201)", order.status === 201, order)
  const lookup = await call("POST", "/api/v1/sales/orders/lookup", { json: { email: buyer.email, number: order.body.number } })
  check("consultar el pedido con numero y email", lookup.status === 200 && lookup.body?.number === order.body.number, lookup)
  const stock = await call("GET", `/api/v1/products/${productId}`, { token })
  check("el pedido reserva el stock (5 - 2)", stock.body?.stock === 3, stock.body)

  console.log("Reportar un problema")
  const description = `Prueba de punta a punta ${RUN}`
  check("mandar un reporte desde la tienda (201)",
    (await call("POST", "/api/v1/bug-reports", { json: { description, pageUrl: `${STORE}/carrito` } })).status === 201)

  if (!SUPERADMIN_EMAIL || !SUPERADMIN_PASSWORD) {
    console.log("Sin SUPERADMIN_EMAIL y SUPERADMIN_PASSWORD: no se prueba la plataforma y la tienda queda creada")
    return
  }

  console.log("Panel de la plataforma")
  const admin = await call("POST", "/api/v1/platform/auth/login", {
    origin: PLATFORM, json: { email: SUPERADMIN_EMAIL, password: SUPERADMIN_PASSWORD },
  })
  mustPass("login del superadmin", admin.status === 200, admin)
  const adminToken = admin.body.token
  const reports = await call("GET", "/api/v1/platform/admin/bug-reports?status=pending&size=100", { origin: PLATFORM, token: adminToken })
  const report = reports.body?.items?.find((item) => item.description === description)
  check("el reporte le llega al superadmin, con su tienda", report?.store === IDENTIFIER, report)
  if (report) {
    const resolved = await call("PUT", `/api/v1/platform/admin/bug-reports/${report.id}/resolved`, {
      origin: PLATFORM, token: adminToken, json: { resolved: true },
    })
    check("marcar el reporte como resuelto", resolved.status === 200 && !!resolved.body?.resolvedAt, resolved)
  }
  check("el dueno de una tienda no entra al panel de la plataforma",
    (await call("GET", "/api/v1/platform/admin/tenants", { origin: PLATFORM, token })).status === 401)

  const deleted = await call("DELETE", `/api/v1/platform/admin/tenants/${tenantId}`, { origin: PLATFORM, token: adminToken })
  check("borrar la tienda de prueba", deleted.status === 204, deleted)
}

main()
  .catch((error) => {
    failures++
    console.log(`  CORTADA: ${error.message}`)
  })
  .finally(() => {
    console.log(failures === 0 ? `Todo bien (${step} pasos)` : `${failures} paso(s) con problemas de ${step}`)
    process.exitCode = failures === 0 ? 0 : 1
  })
