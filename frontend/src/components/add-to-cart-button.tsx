import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { ShoppingCart } from "lucide-react"
import type { Availability } from "@/lib/types"
import { strings } from "@/lib/strings"
import { useCart, type NewCartItem } from "@/lib/cart"
import { Button } from "@/components/ui/button"

interface AddToCartButtonProps {
  // price null = "consultar precio": no se puede comprar online
  item: Omit<NewCartItem, "price"> & { price: number | null }
  availability: Availability
  className?: string
  // En las tarjetas del listado: boton chico y, sin precio, nada (la tarjeta ya dice "Consultar precio")
  compact?: boolean
}

// Agotado o sin precio no se agrega (el backend igual lo rechazaria). El stock real lo confirma el pedido
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
      size={compact ? "sm" : "default"}
      variant={compact ? "outline" : "default"}
      className={className}
      aria-label={compact ? `${soldOut ? strings.cart.outOfStock : strings.cart.add}: ${item.name}` : undefined}
    >
      <ShoppingCart className="h-4 w-4" />
      {soldOut ? strings.cart.outOfStock : strings.cart.add}
    </Button>
  )
}
