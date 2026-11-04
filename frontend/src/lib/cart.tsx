import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react"
import type { OrderItemType, PriceChange } from "./types"

/*
Store cart, stored in localStorage. Each store is a subdomain (another origin), so each one has its
own. The price is the one the buyer saw: the backend sets the real one and responds 409 if it changed (see docs/backend/sales.md).
The limits are those of the online order (GET /sales/checkout confirms them): 30 lines and 20 units per line.
 */

export const MAX_LINES = 30
export const MAX_QUANTITY = 20

export interface CartItem {
  type: OrderItemType
  id: number
  name: string
  price: number
  // First image (file name) and product slug, to show it in the cart
  image: string | null
  slug: string | null
  quantity: number
}

export type NewCartItem = Omit<CartItem, "quantity">

// Result of adding: "limit" = the unit or line limit was reached
export type AddResult = "added" | "limit"

interface CartContextType {
  items: CartItem[]
  units: number
  total: number
  add: (item: NewCartItem, quantity?: number) => AddResult
  setQuantity: (type: OrderItemType, id: number, quantity: number) => void
  remove: (type: OrderItemType, id: number) => void
  clear: () => void
  // Applies the new prices from a 409; an item that no longer has a price is removed from the cart
  applyPriceChanges: (changes: PriceChange[]) => void
}

const STORAGE_KEY = "kleinkauf.cart"

const CartContext = createContext<CartContextType | undefined>(undefined)

const sameItem = (item: CartItem, type: OrderItemType, id: number) => item.type === type && item.id === id

const clampQuantity = (quantity: number) => Math.min(MAX_QUANTITY, Math.max(1, Math.floor(quantity)))

// localStorage may be missing (private mode, blocked) or contain garbage: the cart starts empty
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
    // Without localStorage the cart keeps working while the page stays open
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>(readStoredCart)

  useEffect(() => {
    writeStoredCart(items)
  }, [items])

  // Another tab of the same store changed the cart
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
        // Name, price and image are updated with the latest the buyer saw
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
