import type { OrderStatus } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Badge } from "@/components/ui/badge"

// What awaits the buyer goes in blue, paid in neutral, rejected in red and closed without a sale dimmed
const VARIANT: Record<OrderStatus, "default" | "secondary" | "destructive" | "outline"> = {
  PENDIENTE_PAGO: "default",
  PAGO_EN_REVISION: "outline",
  RECHAZADO: "destructive",
  PAGADO: "secondary",
  EN_PREPARACION: "secondary",
  LISTO: "default",
  ENTREGADO: "secondary",
  CANCELADO: "outline",
  VENCIDO: "outline",
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const closed = status === "CANCELADO" || status === "VENCIDO"
  return (
    <Badge variant={VARIANT[status]} className={closed ? "text-muted-foreground" : undefined}>
      {strings.order.status[status]}
    </Badge>
  )
}
