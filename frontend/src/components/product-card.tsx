import type { CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { formatPrice } from "@/lib/utils"

interface ProductCardProps {
  product: CatalogProduct
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
      className="group text-left bg-card rounded-lg border border-border overflow-hidden transition-shadow duration-200 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-ring"
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
          <p
            className={`text-xs whitespace-nowrap ${
              product.availability === "AGOTADO" ? "text-destructive" : "text-muted-foreground"
            }`}
          >
            {strings.availability[product.availability]}
          </p>
        </div>

        <p className="text-sm font-medium text-foreground">
          {formatPrice(product.price, strings.price.ask)}
        </p>

        <p className="text-sm text-muted-foreground line-clamp-3">
          {product.description}
        </p>
      </div>
    </button>
  )
}
