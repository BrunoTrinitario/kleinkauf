/*
Orders placed or looked up from this browser, with the order email: the lookup, the receipt and the
cancellation require number + email (docs/backend/sales.md). This way the order screen opens without asking for the email again.
Lives in the store's localStorage (each subdomain has its own) and keeps the last MAX_KNOWN.
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
    // Without localStorage the order screen asks for the email again
  }
}

export function knownOrderEmail(number: string): string | null {
  const target = normalizeOrderNumber(number)
  return readKnownOrders().find((known) => known.number === target)?.email ?? null
}
