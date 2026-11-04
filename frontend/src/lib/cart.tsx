import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react"
import type { OrderItemType, PriceChange } from "./types"

/*
Carrito de la tienda, guardado en localStorage. Cada tienda es un subdominio (otro origen), asi que cada una tiene el
suyo. El precio es el que vio el comprador: el backend pone el real y responde 409 si cambio (ver docs/backend/sales.md).
Los topes son los del pedido online (GET /sales/checkout los confirma): 30 lineas y 20 unidades por linea.
 */

export const MAX_LINES = 30
export const MAX_QUANTITY = 20

export interface CartItem {
  type: OrderItemType
  id: number
  name: string
  price: number
  // Primera imagen (nombre de archivo) y slug del producto, para mostrarlo en el carrito
  image: string | null
  slug: string | null
  quantity: number
}

export type NewCartItem = Omit<CartItem, "quantity">

// Resultado de agregar: "limit" = se llego al tope de unidades o de lineas
export type AddResult = "added" | "limit"

interface CartContextType {
  items: CartItem[]
  units: number
  total: number
  add: (item: NewCartItem, quantity?: number) => AddResult
  setQuantity: (type: OrderItemType, id: number, quantity: number) => void
  remove: (type: OrderItemType, id: number) => void
  clear: () => void
  // Aplica los precios nuevos de un 409; un item que ya no tiene precio se saca del carrito
  applyPriceChanges: (changes: PriceChange[]) => void
}

const STORAGE_KEY = "kleinkauf.cart"

const CartContext = createContext<CartContextType | undefined>(undefined)

const sameItem = (item: CartItem, type: OrderItemType, id: number) => item.type === type && item.id === id

const clampQuantity = (quantity: number) => Math.min(MAX_QUANTITY, Math.max(1, Math.floor(quantity)))

// localStorage puede no estar (modo privado, bloqueado) o tener basura: el carrito arranca vacio
function readStoredCart(): CartItem[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (item): item is CartItem =>
          !!item &&
          (item.type === "PRODUCT" || item.type === "BUNDLE") &&
          typeof item.id === "number" &&
          typeof item.name === "string" &&
          typeof item.price === "number" &&
          typeof item.quantity === "number"
      )
      .map((item) => ({ ...item, quantity: clampQuantity(item.quantity) }))
      .slice(0, MAX_LINES)
  } catch {
    return []
  }
}

function writeStoredCart(items: CartItem[]) {
  try {
    if (items.length === 0) {
      window.localStorage.removeItem(STORAGE_KEY)
    } else {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
    }
  } catch {
    // Sin localStorage el carrito sigue andando mientras la pagina este abierta
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>(readStoredCart)

  useEffect(() => {
    writeStoredCart(items)
  }, [items])

  // Otra pestana de la misma tienda cambio el carrito
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) setItems(readStoredCart())
    }
    window.addEventListener("storage", onStorage)
    return () => window.removeEventListener("storage", onStorage)
  }, [])

  const add = useCallback(
    (item: NewCartItem, quantity = 1): AddResult => {
      const current = items.find((existing) => sameItem(existing, item.type, item.id))
      if (current) {
        if (current.quantity >= MAX_QUANTITY) return "limit"
        const next = Math.min(MAX_QUANTITY, current.quantity + quantity)
        // Se actualizan nombre, precio e imagen con lo ultimo que vio el comprador
        setItems((prev) => prev.map((existing) => (sameItem(existing, item.type, item.id) ? { ...item, quantity: next } : existing)))
        return next - current.quantity < quantity ? "limit" : "added"
      }
      if (items.length >= MAX_LINES) return "limit"
      setItems((prev) => [...prev, { ...item, quantity: clampQuantity(quantity) }])
      return "added"
    },
    [items]
  )

  const setQuantity = useCallback((type: OrderItemType, id: number, quantity: number) => {
    setItems((prev) => prev.map((item) => (sameItem(item, type, id) ? { ...item, quantity: clampQuantity(quantity) } : item)))
  }, [])

  const remove = useCallback((type: OrderItemType, id: number) => {
    setItems((prev) => prev.filter((item) => !sameItem(item, type, id)))
  }, [])

  const clear = useCallback(() => setItems([]), [])

  const applyPriceChanges = useCallback((changes: PriceChange[]) => {
    setItems((prev) =>
      prev.flatMap((item) => {
        const change = changes.find((candidate) => candidate.type === item.type && candidate.id === item.id)
        if (!change) return [item]
        return change.currentPrice == null ? [] : [{ ...item, price: change.currentPrice }]
      })
    )
  }, [])

  const value = useMemo<CartContextType>(() => {
    const units = items.reduce((sum, item) => sum + item.quantity, 0)
    const total = items.reduce((sum, item) => sum + item.price * item.quantity, 0)
    return { items, units, total, add, setQuantity, remove, clear, applyPriceChanges }
  }, [items, add, setQuantity, remove, clear, applyPriceChanges])

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

export function useCart() {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error("useCart debe usarse dentro de CartProvider")
  }
  return context
}
