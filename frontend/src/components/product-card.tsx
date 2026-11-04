import type { CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { formatPrice } from "@/lib/utils"
import { productImageUrl } from "@/lib/api"
import { AddToCartButton } from "./add-to-cart-button"

interface ProductCardProps {
  product: CatalogProduct
  onClick: () => void
}

// Tarjeta del listado: la imagen y los datos abren el detalle; abajo, agregar al carrito sin abrirlo.
// Son dos controles separados (un boton no puede ir dentro de otro)
export function ProductCard({ product, onClick }: ProductCardProps) {
  const primaryImage = product.images?.[0]
  const imageUrl = primaryImage ? productImageUrl(primaryImage) : null

  return (
    <article className="group flex flex-col bg-card rounded-lg border border-border overflow-hidden transition-shadow duration-200 hover:shadow-md">
      <button
        type="button"
        onClick={onClick}
        className="flex flex-1 flex-col text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
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
        <div className="p-4 pb-3 space-y-2">
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
      <div className="px-4 pb-4 empty:hidden">
        <AddToCartButton
          compact
          className="w-full"
          item={{
            type: "PRODUCT",
            id: product.id,
            name: product.name,
            price: product.price,
            image: primaryImage ?? null,
            slug: product.slug,
          }}
          availability={product.availability}
        />
      </div>
    </article>
  )
}
