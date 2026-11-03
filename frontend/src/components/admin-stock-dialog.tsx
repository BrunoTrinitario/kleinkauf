import { useEffect, useState } from "react"
import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { cn, formatServerDateTime } from "@/lib/utils"
import { useRegisterMovement, useStockMovements, type StockMovementType } from "@/api/products"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"

// Producto al que se le carga el movimiento (sirve el del listado o el de stock bajo)
export interface StockTarget {
  id: string
  name: string
  stock: number
}

interface AdminStockDialogProps {
  product: StockTarget | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

const TYPES: StockMovementType[] = ["ENTRADA", "SALIDA", "AJUSTE"]

// Registrar un movimiento y ver el historial (area STOCK). El stock solo cambia asi (ver docs/backend/products.md)
export function AdminStockDialog({ product, open, onOpenChange }: AdminStockDialogProps) {
  const [type, setType] = useState<StockMovementType>("ENTRADA")
  const [quantity, setQuantity] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState<string | null>(null)
  // El stock del listado no se actualiza hasta que vuelve la lista: se usa el que devuelve el movimiento
  const [currentStock, setCurrentStock] = useState<number | null>(null)
  const history = useStockMovements(product?.id, open)
  const register = useRegisterMovement(product?.id)

  useEffect(() => {
    if (!open) return
    setType("ENTRADA")
    setQuantity("")
    setReason("")
    setError(null)
    setCurrentStock(null)
  }, [open, product?.id])

  const isAdjustment = type === "AJUSTE"

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    const value = Number(quantity)
    if (quantity.trim() === "" || !Number.isInteger(value) || (isAdjustment ? value < 0 : value <= 0)) {
      setError(isAdjustment ? strings.adminStock.invalidCount : strings.adminStock.invalidQuantity)
      return
    }
    if (isAdjustment && reason.trim() === "") {
      setError(strings.adminStock.reasonRequired)
      return
    }
    setError(null)
    try {
      const movement = await register.mutateAsync({ type, quantity: value, reason: reason.trim() || undefined })
      setCurrentStock(movement.stockAfter)
      setQuantity("")
      setReason("")
      toast.success(strings.adminStock.registered)
    } catch (err) {
      setError(getErrorMessage(err, strings.adminStock.saveError, { conflict: strings.adminStock.notEnoughStock }))
    }
  }

  if (!product) return null
  const stock = currentStock ?? product.stock

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.adminStock.title}</DialogTitle>
          <DialogDescription>{strings.adminStock.description(product.name, stock)}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <span className="text-sm font-medium">{strings.adminStock.typeLabel}</span>
            <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label={strings.adminStock.typeLabel}>
              {TYPES.map((option) => (
                <Button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={type === option}
                  variant={type === option ? "default" : "outline"}
                  onClick={() => setType(option)}
                >
                  {strings.adminStock.types[option]}
                </Button>
              ))}
            </div>
            <p className="text-xs text-muted-foreground">{strings.adminStock.typeHints[type]}</p>
          </div>

          <div className="grid gap-2">
            <label htmlFor="stock-quantity" className="text-sm font-medium">
              {isAdjustment ? strings.adminStock.countedLabel : strings.adminStock.quantityLabel}
            </label>
            <Input
              id="stock-quantity"
              type="number"
              inputMode="numeric"
              min={isAdjustment ? 0 : 1}
              step={1}
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
            />
          </div>

          <div className="grid gap-2">
            <label htmlFor="stock-reason" className="text-sm font-medium">
              {strings.adminStock.reasonLabel}
            </label>
            <Input
              id="stock-reason"
              value={reason}
              placeholder={isAdjustment ? undefined : strings.adminStock.reasonOptional}
              maxLength={255}
              onChange={(event) => setReason(event.target.value)}
            />
          </div>

          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          <Button type="submit" className="w-full" disabled={register.isPending}>
            {register.isPending ? strings.adminStock.registering : strings.adminStock.register}
          </Button>
        </form>

        <div className="space-y-3 border-t border-border pt-4">
          <h3 className="text-sm font-medium">{strings.adminStock.historyTitle}</h3>
          {history.isPending ? (
            <p className="text-sm text-muted-foreground">{strings.adminStock.historyLoading}</p>
          ) : (history.data ?? []).length === 0 ? (
            <p className="text-sm text-muted-foreground">{strings.adminStock.historyEmpty}</p>
          ) : (
            <ul className="space-y-2">
              {(history.data ?? []).map((movement) => (
                <li key={movement.id} className="flex items-start justify-between gap-3 text-sm">
                  <div className="min-w-0">
                    <p className="font-medium">
                      {strings.adminStock.types[movement.type]}
                      <span
                        className={cn(
                          "ml-2 tabular-nums",
                          movement.quantity < 0 ? "text-destructive" : "text-cyan-edge"
                        )}
                      >
                        {movement.quantity > 0 ? `+${movement.quantity}` : movement.quantity}
                      </span>
                    </p>
                    {movement.reason ? <p className="text-muted-foreground truncate">{movement.reason}</p> : null}
                  </div>
                  <div className="text-right text-xs text-muted-foreground shrink-0">
                    <p>{formatServerDateTime(movement.createdAt)}</p>
                    <p>{strings.adminStock.after(movement.stockAfter)}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
