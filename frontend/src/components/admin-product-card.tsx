import type { Product } from "@/lib/types"
import { strings } from "@/lib/strings"
import { productImageUrl } from "@/lib/api"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { PackagePlus, Pencil, Star, Trash2 } from "lucide-react"

// Each action is optional: without the handler (missing area permission) the button is hidden or disabled
interface AdminProductCardProps {
  product: Product
  onEdit?: (product: Product) => void
  onDelete?: (product: Product) => void
  // STOCK: movements and history
  onStock?: (product: Product) => void
  // PRODUCTOS: mark or unmark as featured
  onToggleFeatured?: (product: Product) => void
}

export function AdminProductCard({
  product,
  onEdit,
  onDelete,
  onStock,
  onToggleFeatured,
}: AdminProductCardProps) {
  // Same criterion as the backend: there is an alert only if the product has a minimum
  const isLowStock = (product.minStock ?? 0) > 0 && product.stock <= product.minStock

  // On mobile: smaller image and category and stock on their own line, so the name has room
  return (
    <div className="flex items-center gap-3 sm:gap-4 p-3 sm:p-4 border border-border rounded-lg hover:bg-muted/50 transition-colors">
      <div className="relative h-12 w-12 sm:h-16 sm:w-16 rounded-md overflow-hidden bg-muted flex-shrink-0">
        {product.images?.[0] ? (
          <img
            src={productImageUrl(product.images[0])}
            alt={product.name}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : null}
      </div>
      <div className="flex-1 min-w-0">
        <h3 className="font-medium truncate">{product.name}</h3>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-sm text-muted-foreground">
          <span className="truncate">{product.category}</span>
          <span className={cn("whitespace-nowrap", isLowStock && "font-medium text-destructive")}>
            {product.stock} {strings.adminProductCard.inStock}
            {isLowStock ? ` · ${strings.adminProductCard.lowStock}` : null}
          </span>
          {product.featured ? (
            <span className="whitespace-nowrap text-cyan-edge">{strings.adminProductCard.featured}</span>
          ) : null}
        </div>
      </div>
      <div className="flex items-center gap-1 sm:gap-2 shrink-0">
        {onToggleFeatured ? (
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onToggleFeatured(product)}
            aria-pressed={!!product.featured}
            aria-label={
              product.featured ? strings.adminProductCard.unfeatureAria : strings.adminProductCard.featureAria
            }
          >
            <Star className={cn("h-4 w-4", product.featured && "fill-current text-cyan-edge")} />
          </Button>
        ) : null}
        {onStock ? (
          <Button
            variant="ghost"
            size="icon"
            onClick={() => onStock(product)}
            aria-label={strings.adminProductCard.stockAria}
          >
            <PackagePlus className="h-4 w-4" />
          </Button>
        ) : null}
        <Button
          variant="ghost"
          size="icon"
          onClick={() => onEdit?.(product)}
          disabled={!onEdit}
          aria-label={strings.adminProductCard.editAria}
        >
          <Pencil className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="text-destructive hover:text-destructive"
          onClick={() => onDelete?.(product)}
          disabled={!onDelete}
          aria-label={strings.adminProductCard.deleteAria}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
