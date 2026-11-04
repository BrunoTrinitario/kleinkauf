/*
Pedidos hechos o consultados desde este navegador, con el email del pedido: la consulta, el comprobante y la
cancelacion piden numero + email (docs/backend/sales.md). Asi la pantalla del pedido abre sin volver a pedir el email.
Vive en localStorage de la tienda (cada subdominio tiene el suyo) y guarda los ultimos MAX_KNOWN.
 */

const STORAGE_KEY = "kleinkauf.orders"
const MAX_KNOWN = 20

export interface KnownOrder {
  number: string
  email: string
}

export const normalizeOrderNumber = (number: string) => number.trim().toUpperCase()

function readKnownOrders(): KnownOrder[] {
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]")
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (order): order is KnownOrder => !!order && typeof order.number === "string" && typeof order.email === "string"
    )
  } catch {
    return []
  }
}

export function rememberOrder(number: string, email: string) {
  const order = { number: normalizeOrderNumber(number), email: email.trim().toLowerCase() }
  const others = readKnownOrders().filter((known) => known.number !== order.number)
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify([order, ...others].slice(0, MAX_KNOWN)))
  } catch {
    // Sin localStorage la pantalla del pedido vuelve a pedir el email
  }
}

export function knownOrderEmail(number: string): string | null {
  const target = normalizeOrderNumber(number)
  return readKnownOrders().find((known) => known.number === target)?.email ?? null
}
