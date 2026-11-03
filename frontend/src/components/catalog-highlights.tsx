import { useState } from "react"
import { Link } from "react-router-dom"
import type { CatalogBundle, CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { productImageUrl } from "@/lib/api"
import { formatPrice } from "@/lib/utils"
import { useCatalogBundles, useCatalogFeatured } from "@/api/catalog"
import { ProductCard } from "./product-card"
import { ProductModal } from "./product-modal"

// Destacados de la home, en el orden que eligio la tienda. Sin destacados no se muestra
export function CatalogFeatured() {
  const featured = useCatalogFeatured().data ?? []
  const [selected, setSelected] = useState<CatalogProduct | null>(null)

  if (featured.length === 0) return null

  return (
    <section className="space-y-4">
      <h2 className="text-subheading">{strings.home.featuredTitle}</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {featured.map((product) => (
          <ProductCard key={product.slug} product={product} onClick={() => setSelected(product)} />
        ))}
      </div>
      <ProductModal product={selected} open={selected !== null} onOpenChange={(open) => !open && setSelected(null)} />
    </section>
  )
}

// Combos que se pueden comprar, con el ahorro frente a comprar los productos por separado. Sin combos no se muestra
export function CatalogBundles() {
  const bundles = useCatalogBundles().data ?? []

  if (bundles.length === 0) return null

  return (
    <section className="space-y-4">
      <div>
        <h2 className="text-subheading">{strings.home.bundlesTitle}</h2>
        <p className="text-sm text-muted-foreground mt-1">{strings.home.bundlesDescription}</p>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {bundles.map((bundle) => (
          <BundleCard key={bundle.id} bundle={bundle} />
        ))}
      </div>
    </section>
  )
}

function BundleCard({ bundle }: { bundle: CatalogBundle }) {
  const saving = bundle.listPrice != null && bundle.listPrice > bundle.price ? bundle.listPrice - bundle.price : null

  return (
    <article className="bg-card rounded-lg border border-border overflow-hidden">
      {bundle.image ? (
        <div className="relative w-full aspect-[4/3] overflow-hidden bg-muted">
          <img src={productImageUrl(bundle.image)} alt={bundle.name} className="h-full w-full object-cover" loading="lazy" />
        </div>
      ) : null}
      <div className="p-4 space-y-3">
        <div className="flex items-start justify-between gap-2">
          <h3 className="font-medium text-foreground leading-tight">{bundle.name}</h3>
          <p
            className={`text-xs whitespace-nowrap ${
              bundle.availability === "AGOTADO" ? "text-destructive" : "text-muted-foreground"
            }`}
          >
            {strings.availability[bundle.availability]}
          </p>
        </div>
        <div className="space-y-0.5">
          <p className="text-sm font-medium text-foreground">{formatPrice(bundle.price, strings.price.ask)}</p>
          {saving != null && bundle.listPrice != null ? (
            <>
              <p className="text-xs text-muted-foreground line-through">
                {strings.bundleCard.listPrice(formatPrice(bundle.listPrice, ""))}
              </p>
              <p className="text-xs text-cyan-edge">{strings.bundleCard.saving(formatPrice(saving, ""))}</p>
            </>
          ) : null}
        </div>
        <div className="space-y-1">
          <p className="text-xs text-muted-foreground uppercase tracking-wider">{strings.bundleCard.includes}</p>
          <ul className="text-sm space-y-0.5">
            {bundle.products.map((item) => (
              <li key={item.slug}>
                <Link to={`/productos/${item.slug}`} className="hover:underline underline-offset-4">
                  {item.quantity > 1 ? `${item.quantity} x ` : ""}
                  {item.name}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </article>
  )
}
