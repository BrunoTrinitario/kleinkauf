import { useEffect, useState } from "react"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { productImageUrl } from "@/lib/api"
import type { Product } from "@/lib/types"
import { useFeaturedProducts, useReorderFeatured, useSetFeatured } from "@/api/products"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { ArrowDown, ArrowUp, StarOff } from "lucide-react"

interface AdminFeaturedDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// Order of the home page featured products (PRODUCTOS area). Each change is saved right away
export function AdminFeaturedDialog({ open, onOpenChange }: AdminFeaturedDialogProps) {
  const featured = useFeaturedProducts(open)
  const reorder = useReorderFeatured()
  const setFeatured = useSetFeatured()
  // The new order is shown without waiting for the backend; it goes back to what was saved when the list arrives
  const [items, setItems] = useState<Product[]>([])

  useEffect(() => {
    if (featured.data) setItems(featured.data)
  }, [featured.data])

  const move = async (index: number, offset: -1 | 1) => {
    const target = index + offset
    if (target < 0 || target >= items.length) return
    const next = [...items]
    ;[next[index], next[target]] = [next[target], next[index]]
    setItems(next)
    try {
      await reorder.mutateAsync(next.map((item) => item.id))
    } catch (err) {
      notifyError(err, strings.adminFeatured.saveError)
    }
  }

  const remove = async (product: Product) => {
    setItems((prev) => prev.filter((item) => item.id !== product.id))
    try {
      await setFeatured.mutateAsync({ id: product.id, featured: false })
    } catch (err) {
      notifyError(err, strings.adminFeatured.toggleError)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.adminFeatured.title}</DialogTitle>
          <DialogDescription>{strings.adminFeatured.description}</DialogDescription>
        </DialogHeader>

        {featured.isPending ? null : items.length === 0 ? (
          <p className="text-sm text-muted-foreground">{strings.adminFeatured.empty}</p>
        ) : (
          <ol className="space-y-2">
            {items.map((product, index) => (
              <li key={product.id} className="flex items-center gap-3 rounded-lg border border-border p-2">
                <span className="w-5 text-center text-sm tabular-nums text-muted-foreground">{index + 1}</span>
                <div className="h-10 w-10 shrink-0 overflow-hidden rounded-md bg-muted">
                  {product.images?.[0] ? (
                    <img src={productImageUrl(product.images[0])} alt="" className="h-full w-full object-cover" />
                  ) : null}
                </div>
                <span className="flex-1 min-w-0 truncate text-sm font-medium">{product.name}</span>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={strings.adminFeatured.up}
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  <ArrowUp className="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={strings.adminFeatured.down}
                  disabled={index === items.length - 1}
                  onClick={() => move(index, 1)}
                >
                  <ArrowDown className="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={strings.adminFeatured.remove}
                  onClick={() => remove(product)}
                >
                  <StarOff className="h-4 w-4" />
                </Button>
              </li>
            ))}
          </ol>
        )}

        <DialogFooter>
          <Button onClick={() => onOpenChange(false)}>{strings.adminFeatured.close}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
