import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { ShoppingCart } from "lucide-react"
import type { Availability } from "@/lib/types"
import { strings } from "@/lib/strings"
import { useCart, type NewCartItem } from "@/lib/cart"
import { Button } from "@/components/ui/button"

interface AddToCartButtonProps {
  // price null = "consultar precio": cannot be bought online
  item: Omit<NewCartItem, "price"> & { price: number | null }
  availability: Availability
  className?: string
  // On listing cards: without a price nothing is shown (the card already says "Consultar precio").
  // The button is the same everywhere (blue, normal size)
  compact?: boolean
}

// Out of stock or without a price it is not added (the backend would reject it anyway). The order confirms the real stock
export function AddToCartButton({ item, availability, className, compact = false }: AddToCartButtonProps) {
  const { add } = useCart()
  const navigate = useNavigate()

  if (item.price === null) {
    return compact ? null : <p className="text-sm text-muted-foreground">{strings.cart.askPrice}</p>
  }

  const soldOut = availability === "AGOTADO"
  const price = item.price

  const handleAdd = () => {
    const result = add({ ...item, price })
    if (result === "limit") {
      toast.error(strings.cart.limit, { id: strings.cart.limit })
      return
    }
    toast.success(strings.cart.added(item.name), {
      action: { label: strings.cart.viewCart, onClick: () => navigate("/carrito") },
    })
  }

  return (
    <Button
      onClick={handleAdd}
      disabled={soldOut}
      className={className}
      aria-label={compact ? `${soldOut ? strings.cart.outOfStock : strings.cart.add}: ${item.name}` : undefined}
    >
      <ShoppingCart className="h-4 w-4" />
      {soldOut ? strings.cart.outOfStock : strings.cart.add}
    </Button>
  )
}
