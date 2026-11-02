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
  // Mismo criterio que "Pocas unidades" en el catalogo: stock igual o menor al minimo
  const isLowStock = product.stock <= (product.minStock ?? 0)

  // En mobile: imagen mas chica y categoria y stock en su propia linea, para que el nombre tenga lugar
  return (
    <div className="flex items-center gap-3 sm:gap-4 p-3 sm:p-4 border border-border rounded-lg hover:bg-muted/50 transition-colors">
      <div className="relative h-12 w-12 sm:h-16 sm:w-16 rounded-md overflow-hidden bg-muted flex-shrink-0">
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
        <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-sm text-muted-foreground">
          <span className="truncate">{product.category}</span>
          <span className={`whitespace-nowrap ${isLowStock ? "font-medium text-foreground" : ""}`}>
            {product.stock} {strings.adminProductCard.inStock}
          </span>
        </div>
      </div>
      <div className="flex items-center gap-1 sm:gap-2 shrink-0">
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
