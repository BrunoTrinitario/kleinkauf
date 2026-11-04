import { useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { ArrowRight } from "lucide-react"
import type { CatalogBundle, CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { productImageUrl } from "@/lib/api"
import { formatPrice } from "@/lib/utils"
import { useCatalogBundles, useCatalogFeatured } from "@/api/catalog"
import { ProductCard } from "./product-card"
import { ProductModal } from "./product-modal"
import { AddToCartButton } from "./add-to-cart-button"

// Cuantos destacados y combos entran en las franjas de la home (el resto, en /catalogo y /combos)
const FEATURED_IN_HOME = 8
const BUNDLES_IN_HOME = 3

// Franja de la home: en el celular se desliza de costado; desde sm, grilla. Sin items no se muestra
function Strip({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="space-y-4">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className="text-subheading">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  )
}

function StripLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="inline-flex shrink-0 items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      {children}
      <ArrowRight className="h-4 w-4" />
    </Link>
  )
}

// Las tarjetas ocupan casi todo el ancho en el celular (se ve que hay mas a la derecha) y se alinean al deslizar
const scrollRow =
  "-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:gap-6 sm:overflow-visible sm:px-0 sm:pb-0"
const scrollItem = "w-[75%] shrink-0 snap-start sm:w-auto"

// Destacados de la home, en el orden que eligio la tienda
export function FeaturedStrip() {
  const featured = (useCatalogFeatured().data ?? []).slice(0, FEATURED_IN_HOME)
  const [selected, setSelected] = useState<CatalogProduct | null>(null)

  if (featured.length === 0) return null

  return (
    <Strip title={strings.home.featuredTitle}>
      {/* Una sola fila en todos los anchos: entran 4 en escritorio y el resto se ve deslizando */}
      <div className="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-2 sm:gap-6">
        {featured.map((product) => (
          <div
            key={product.slug}
            className="grid w-[75%] shrink-0 snap-start sm:w-[calc((100%-1.5rem)/2)] lg:w-[calc((100%-4.5rem)/4)]"
          >
            <ProductCard product={product} onClick={() => setSelected(product)} />
          </div>
        ))}
      </div>
      <ProductModal product={selected} open={selected !== null} onOpenChange={(open) => !open && setSelected(null)} />
    </Strip>
  )
}

// Unos pocos combos en la home, compactos; el detalle y el boton de compra estan en /combos
export function BundlesStrip() {
  const bundles = useCatalogBundles().data ?? []

  if (bundles.length === 0) return null

  return (
    <Strip
      title={strings.home.bundlesTitle}
      action={<StripLink to="/combos">{strings.home.allBundles(bundles.length)}</StripLink>}
    >
      <div className={`${scrollRow} sm:grid-cols-3`}>
        {bundles.slice(0, BUNDLES_IN_HOME).map((bundle) => (
          <div key={bundle.id} className={scrollItem}>
            <CompactBundleCard bundle={bundle} />
          </div>
        ))}
      </div>
    </Strip>
  )
}

const savingOf = (bundle: CatalogBundle) =>
  bundle.listPrice != null && bundle.listPrice > bundle.price ? bundle.listPrice - bundle.price : null

function CompactBundleCard({ bundle }: { bundle: CatalogBundle }) {
  const saving = savingOf(bundle)
  return (
    <Link
      to={`/combos#combo-${bundle.id}`}
      className="flex h-full items-center gap-4 rounded-lg border border-border bg-card p-3 transition-shadow hover:shadow-md focus:outline-none focus:ring-2 focus:ring-ring"
    >
      <div className="h-16 w-16 shrink-0 overflow-hidden rounded-md bg-muted">
        {bundle.image ? (
          <img src={productImageUrl(bundle.image)} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : null}
      </div>
      <div className="min-w-0 space-y-0.5">
        <p className="truncate font-medium leading-tight">{bundle.name}</p>
        <p className="text-sm">{formatPrice(bundle.price, strings.price.ask)}</p>
        {saving != null ? (
          <p className="text-xs text-cyan-edge">{strings.bundleCard.saving(formatPrice(saving, ""))}</p>
        ) : (
          <p className="text-xs text-muted-foreground">{strings.bundleCard.productsCount(bundle.products.length)}</p>
        )}
      </div>
    </Link>
  )
}

// Tarjeta completa del combo (/combos): ahorro frente a comprar por separado, que incluye y agregar al carrito
export function BundleCard({ bundle }: { bundle: CatalogBundle }) {
  const saving = savingOf(bundle)

  return (
    <article id={`combo-${bundle.id}`} className="scroll-mt-24 bg-card rounded-lg border border-border overflow-hidden">
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
        <AddToCartButton
          item={{ type: "BUNDLE", id: bundle.id, name: bundle.name, price: bundle.price, image: bundle.image, slug: null }}
          availability={bundle.availability}
          className="w-full"
        />
      </div>
    </article>
  )
}
