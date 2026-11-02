import type { Product } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Button } from "@/components/ui/button"
import { Pencil, Trash2 } from "lucide-react"

interface AdminProductCardProps {
  product: Product
  apiBasePublic: string
  onEdit?: (product: Product) => void
  onDelete?: (product: Product) => void
}

export function AdminProductCard({
  product,
  apiBasePublic,
  onEdit,
  onDelete,
}: AdminProductCardProps) {
  return (
    <div className="flex items-center gap-4 p-4 border border-border rounded-lg hover:bg-muted/50 transition-colors">
      <div className="relative h-16 w-16 rounded-md overflow-hidden bg-muted flex-shrink-0">
        {product.images?.[0] ? (
          <img
            src={`${apiBasePublic}/products/images/${product.images[0]}`}
            alt={product.name}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : null}
      </div>
      <div className="flex-1 min-w-0">
        <h3 className="font-medium truncate">{product.name}</h3>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span>{product.category}</span>
          <span className={product.stock <= 10 ? "font-medium text-foreground" : ""}>
            {product.stock} {strings.adminProductCard.inStock}
          </span>
        </div>
      </div>
      <div className="flex items-center gap-2">
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
