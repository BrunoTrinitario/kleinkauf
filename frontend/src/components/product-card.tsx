
import type { Product } from "@/lib/types"
import { strings } from "@/lib/strings"

interface ProductCardProps {
  product: Product
  onClick: () => void
}

export function ProductCard({ product, onClick }: ProductCardProps) {
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"
  const primaryImage = product.images?.[0]
  const imageUrl = primaryImage
    ? `${apiBasePublic}/products/images/${primaryImage}`
    : null

  return (
    <button
      onClick={onClick}
      className="group text-left bg-card rounded-xl border border-border/50 overflow-hidden transition-all duration-300 hover:shadow-lg hover:border-border hover:-translate-y-1 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
    >
      {imageUrl ? (
        <div className="relative w-full aspect-[4/3] overflow-hidden bg-muted">
          <img
            src={imageUrl}
            alt={product.name}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.03]"
            loading="lazy"
          />
        </div>
      ) : null}
      <div className="p-4 space-y-2">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-xs text-muted-foreground uppercase tracking-wider">
              {product.category}
            </p>
            <h3 className="font-medium text-foreground leading-tight mt-1">
              {product.name}
            </h3>
          </div>
          <p className="text-xs text-muted-foreground whitespace-nowrap">
            {product.stock > 0
              ? `${product.stock} ${strings.productCard.inStock}`
              : strings.productCard.outOfStock}
          </p>
        </div>

        <p className="text-sm text-muted-foreground line-clamp-3">
          {product.description}
        </p>
      </div>
    </button>
  )
}
