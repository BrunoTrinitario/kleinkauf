// Seed de desarrollo. Todo por la API (los mismos flujos que usa la app):
// 1. Da de alta la tienda demo (autoservicio de la plataforma).
// 2. Con el dueno: carga los productos de data.json con precio, costo, stock, metadatos,
//    imagenes genericas por categoria y destacados.
// 3. Crea clientes y empleados de prueba.
// Se puede correr muchas veces: lo que ya existe (tienda, producto por nombre, usuario por email) se saltea.
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const HERE = dirname(fileURLToPath(import.meta.url))
const env = (name) => {
  const value = process.env[name]
  if (!value) throw new Error(`Falta la variable ${name}`)
  return value
}
const GATEWAY = env("GATEWAY_URL")
const PLATFORM = env("PLATFORM_URL")
const STORE = `${env("STORE_SCHEME")}://${env("DEMO_IDENTIFIER")}.${env("STORE_DOMAIN")}`
const data = JSON.parse(readFileSync(join(HERE, "data.json"), "utf8"))

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/*
Llamada al gateway. El Origin dice a que tienda va (o a la plataforma).
Reintenta mientras el gateway todavia no ve a los servicios (5xx o conexion rechazada)
y espera si pega en el limite de pedidos por minuto (429).
 */
async function call(method, path, { origin = STORE, token, json, form, ok = [200, 201] } = {}) {
  const headers = { Origin: origin }
  if (token) headers.Authorization = `Bearer ${token}`
  let body
  if (json !== undefined) {
    headers["Content-Type"] = "application/json"
    body = JSON.stringify(json)
  } else if (form) {
    body = form
  }

  for (let attempt = 1; attempt <= 40; attempt++) {
    let response
    try {
      response = await fetch(GATEWAY + path, { method, headers, body })
    } catch {
      await sleep(5000)
      continue
    }
    if (ok.includes(response.status)) {
      // Los errores esperados (409, 401) vienen como texto, no JSON
      const text = await response.text()
      let parsed = text || null
      try {
        parsed = text ? JSON.parse(text) : null
      } catch {}
      return { status: response.status, body: parsed }
    }
    if (response.status === 429) {
      console.log(`  limite de pedidos alcanzado, espero 20s (${method} ${path})`)
      await sleep(20000)
      continue
    }
    if (response.status >= 500) {
      await sleep(5000)
      continue
    }
    throw new Error(`${method} ${path} respondio ${response.status}: ${await response.text()}`)
  }
  throw new Error(`${method} ${path}: el gateway no respondio a tiempo`)
}

// Recorre todas las paginas de un listado del panel
async function listAll(path, token) {
  const items = []
  for (let page = 0; ; page++) {
    const separator = path.includes("?") ? "&" : "?"
    const { body } = await call("GET", `${path}${separator}size=100&page=${page}`, { token })
    items.push(...body.items)
    if (page >= body.totalPages - 1) return items
  }
}

// "Valentina Ríos" -> "valentina.rios@demo.local"
const emailFor = (name) =>
  name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/\s+/g, ".") + "@demo.local"

const descriptionFor = (p) =>
  `${p.name}: ${p.metadata.material.toLowerCase()}, estilo ${p.metadata.style.toLowerCase()}. ` +
  `Pieza de la categoría ${p.category.toLowerCase()} pensada para sumar calidez al ambiente.`

async function createStore() {
  const { status } = await call("POST", "/api/v1/platform/tenants", {
    origin: PLATFORM,
    json: {
      identifier: env("DEMO_IDENTIFIER"),
      ownerName: env("DEMO_OWNER_NAME"),
      ownerEmail: env("DEMO_OWNER_EMAIL"),
      ownerPassword: env("DEMO_OWNER_PASSWORD"),
    },
    ok: [201, 409],
  })
  console.log(status === 201 ? `Tienda creada: ${STORE}` : `La tienda ya existe: ${STORE}`)
}

async function login() {
  const { status, body } = await call("POST", "/api/v1/auth/login", {
    json: { email: env("DEMO_OWNER_EMAIL"), password: env("DEMO_OWNER_PASSWORD") },
    ok: [200, 401],
  })
  if (status === 401) {
    throw new Error(
      "El dueño de la tienda demo no pudo iniciar sesión con DEMO_OWNER_PASSWORD. " +
        "Si la base es de antes de cambiar la contraseña del seed, actualizala desde la configuración de la cuenta."
    )
  }
  return body.token
}

async function seedProducts(token) {
  const existing = new Set((await listAll("/api/v1/products", token)).map((p) => p.name))
  let created = 0
  for (const [index, p] of data.products.entries()) {
    if (existing.has(p.name)) continue

    const { body: product } = await call("POST", "/api/v1/products", {
      token,
      json: {
        name: p.name,
        category: p.category,
        description: descriptionFor(p),
        stock: p.stock,
        minStock: p.minStock,
        price: p.price,
        cost: p.cost,
      },
    })
    await call("PUT", `/api/v1/products/${product.id}/metadata`, { token, json: p.metadata })

    // Dos variantes de color de la imagen de su categoria, rotando para que no se repitan todas
    const form = new FormData()
    for (const offset of [0, 1]) {
      const variant = ((index + offset) % 3) + 1
      const file = readFileSync(join(HERE, "images", `${p.image}-${variant}.png`))
      form.append("files", new Blob([file], { type: "image/png" }), `${p.image}-${variant}.png`)
    }
    await call("POST", `/api/v1/products/${product.id}/images`, { token, form })

    if (p.featured) {
      await call("PUT", `/api/v1/products/${product.id}/featured`, {
        token,
        json: { featured: true, order: p.featured },
      })
    }
    created++
  }
  console.log(`Productos: ${created} creados, ${data.products.length - created} ya existían`)
}

async function seedUsers(token) {
  const existing = new Set((await listAll("/api/v1/users", token)).map((u) => u.email))
  const users = [
    ...data.users.employees.map((name) => ({ name, roles: ["EMPLEADO"] })),
    ...data.users.customers.map((name) => ({ name, roles: ["CLIENTE"] })),
  ]
  let created = 0
  for (const user of users) {
    const email = emailFor(user.name)
    if (existing.has(email)) continue
    await call("POST", "/api/v1/users", {
      token,
      json: { name: user.name, email, password: data.users.password, roles: user.roles },
    })
    created++
  }
  console.log(`Usuarios: ${created} creados, ${users.length - created} ya existían`)
}

try {
  await createStore()
  const token = await login()
  await seedProducts(token)
  await seedUsers(token)
  console.log("Seed terminado")
} catch (error) {
  console.error(`Seed fallido: ${error.message}`)
  process.exit(1)
}
