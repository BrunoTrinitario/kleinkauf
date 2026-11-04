import { useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { ArrowRight, ImageIcon } from "lucide-react"
import type { CatalogBundle, CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { productImageUrl } from "@/lib/api"
import { formatPrice } from "@/lib/utils"
import { useCatalogBundles, useCatalogFeatured } from "@/api/catalog"
import { ProductCard } from "./product-card"
import { ProductModal } from "./product-modal"
import { AddToCartButton } from "./add-to-cart-button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "./ui/dialog"

// Cuantos destacados y combos entran en las franjas de la home (el resto, en /catalogo y /combos)
const FEATURED_IN_HOME = 8
const BUNDLES_IN_HOME = 8

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

// Una sola fila en todos los anchos: en el celular cada tarjeta ocupa casi todo el ancho (se ve que hay mas a la
// derecha), entran 2 desde sm y 4 en escritorio; el resto se ve deslizando
const stripRow = "-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-2 sm:gap-6"
const stripItem = "grid w-[75%] shrink-0 snap-start sm:w-[calc((100%-1.5rem)/2)] lg:w-[calc((100%-4.5rem)/4)]"

// Destacados de la home, en el orden que eligio la tienda
export function FeaturedStrip() {
  const featured = (useCatalogFeatured().data ?? []).slice(0, FEATURED_IN_HOME)
  const [selected, setSelected] = useState<CatalogProduct | null>(null)

  if (featured.length === 0) return null

  return (
    <Strip title={strings.home.featuredTitle}>
      <div className={stripRow}>
        {featured.map((product) => (
          <div key={product.slug} className={stripItem}>
            <ProductCard product={product} onClick={() => setSelected(product)} />
          </div>
        ))}
      </div>
      <ProductModal product={selected} open={selected !== null} onOpenChange={(open) => !open && setSelected(null)} />
    </Strip>
  )
}

// Combos de la home con la misma tarjeta que /combos, en una fila como los destacados
export function BundlesStrip() {
  const bundles = useCatalogBundles().data ?? []

  if (bundles.length === 0) return null

  return (
    <Strip
      title={strings.home.bundlesTitle}
      action={<StripLink to="/combos">{strings.home.allBundles}</StripLink>}
    >
      <div className={stripRow}>
        {bundles.slice(0, BUNDLES_IN_HOME).map((bundle) => (
          <div key={bundle.id} className={stripItem}>
            <BundleCard bundle={bundle} />
          </div>
        ))}
      </div>
    </Strip>
  )
}

const savingOf = (bundle: CatalogBundle) =>
  bundle.listPrice != null && bundle.listPrice > bundle.price ? bundle.listPrice - bundle.price : null

function ProductThumb({ image, className }: { image?: string | null; className: string }) {
  return (
    <div className={`flex shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted ${className}`}>
      {image ? (
        <img src={productImageUrl(image)} alt="" className="h-full w-full object-cover" loading="lazy" />
      ) : (
        <ImageIcon className="h-4 w-4 text-muted-foreground" />
      )}
    </div>
  )
}

// Foto del combo: con varios productos con imagen, un mosaico de hasta 4; si no, la imagen del combo
function BundleCover({ bundle }: { bundle: CatalogBundle }) {
  const images = bundle.products.map((item) => item.image).filter((image): image is string => !!image).slice(0, 4)
  if (images.length < 2) {
    return bundle.image ? (
      <div className="relative w-full aspect-[4/3] overflow-hidden bg-muted">
        <img src={productImageUrl(bundle.image)} alt={bundle.name} className="h-full w-full object-cover" loading="lazy" />
      </div>
    ) : null
  }
  return (
    <div className={`grid w-full aspect-[4/3] gap-0.5 bg-border ${images.length === 2 ? "grid-cols-2" : "grid-cols-2 grid-rows-2"}`}>
      {images.map((image, index) => (
        <div
          key={`${image}-${index}`}
          className={`overflow-hidden bg-muted ${images.length === 3 && index === 0 ? "row-span-2" : ""}`}
        >
          <img src={productImageUrl(image)} alt="" className="h-full w-full object-cover" loading="lazy" />
        </div>
      ))}
    </div>
  )
}

// Tarjeta completa del combo (/combos): ahorro frente a comprar por separado, que incluye y agregar al carrito.
// Tocar la foto abre el detalle: los productos con su foto, descripcion y precio suelto
export function BundleCard({ bundle }: { bundle: CatalogBundle }) {
  const saving = savingOf(bundle)
  const [detailOpen, setDetailOpen] = useState(false)

  return (
    <article
      id={`combo-${bundle.id}`}
      className="scroll-mt-24 flex flex-col bg-card rounded-lg border border-border overflow-hidden"
    >
      <button
        type="button"
        className="block w-full focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label={`${strings.bundleCard.viewDetail}: ${bundle.name}`}
        onClick={() => setDetailOpen(true)}
      >
        <BundleCover bundle={bundle} />
      </button>
      <div className="flex flex-1 flex-col p-4 space-y-3">
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
        <div className="flex-1 space-y-2">
          <p className="text-xs text-muted-foreground uppercase tracking-wider">{strings.bundleCard.includes}</p>
          <ul className="space-y-2">
            {bundle.products.map((item) => (
              <li key={item.slug}>
                <Link
                  to={`/productos/${item.slug}`}
                  className="group flex items-center gap-3 rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <ProductThumb image={item.image} className="h-10 w-10" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm group-hover:underline underline-offset-4">{item.name}</span>
                    {item.price != null ? (
                      <span className="block text-xs text-muted-foreground tabular-nums">
                        {strings.bundleCard.unitPrice(formatPrice(item.price, ""))}
                      </span>
                    ) : null}
                  </span>
                  {item.quantity > 1 ? (
                    <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs tabular-nums">x{item.quantity}</span>
                  ) : null}
                </Link>
              </li>
            ))}
          </ul>
        </div>
        <BundleCartButton bundle={bundle} className="w-full" />
      </div>
      <BundleDetailDialog bundle={bundle} open={detailOpen} onOpenChange={setDetailOpen} />
    </article>
  )
}

function BundleCartButton({ bundle, className }: { bundle: CatalogBundle; className?: string }) {
  return (
    <AddToCartButton
      item={{ type: "BUNDLE", id: bundle.id, name: bundle.name, price: bundle.price, image: bundle.image, slug: null }}
      availability={bundle.availability}
      className={className}
    />
  )
}

// Detalle del combo: cada producto con su foto, categoria, descripcion, cantidad y precio suelto, y al final la
// cuenta de lo que sale por separado contra el precio del combo
function BundleDetailDialog({
  bundle,
  open,
  onOpenChange,
}: {
  bundle: CatalogBundle
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const saving = savingOf(bundle)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="grid-cols-1 sm:max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{bundle.name}</DialogTitle>
          <DialogDescription>{strings.bundleCard.detailDescription}</DialogDescription>
        </DialogHeader>

        <ul className="min-w-0 space-y-3">
          {bundle.products.map((item) => (
            <li key={item.slug} className="flex gap-4 rounded-lg border border-border p-3">
              <Link
                to={`/productos/${item.slug}`}
                className="shrink-0 rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label={`${strings.bundleCard.viewProduct}: ${item.name}`}
              >
                <ProductThumb image={item.image} className="h-20 w-20 sm:h-24 sm:w-24" />
              </Link>
              <div className="min-w-0 flex-1 space-y-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="min-w-0 font-medium leading-tight">{item.name}</p>
                  <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs tabular-nums">x{item.quantity}</span>
                </div>
                {item.category ? <p className="text-xs text-muted-foreground">{item.category}</p> : null}
                {item.description ? (
                  <p className="text-sm text-muted-foreground line-clamp-2">{item.description}</p>
                ) : null}
                <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 pt-1">
                  <p className="text-sm tabular-nums">
                    {item.price != null
                      ? item.quantity > 1
                        ? strings.bundleCard.lineTotal(
                            item.quantity,
                            formatPrice(item.price, ""),
                            formatPrice(item.price * item.quantity, "")
                          )
                        : formatPrice(item.price, "")
                      : strings.price.ask}
                  </p>
                  <Link
                    to={`/productos/${item.slug}`}
                    className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
                  >
                    {strings.bundleCard.viewProduct}
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                </div>
              </div>
            </li>
          ))}
        </ul>

        <div className="space-y-1 rounded-lg bg-muted/50 p-3 text-sm">
          {bundle.listPrice != null ? (
            <div className="flex justify-between gap-2 text-muted-foreground">
              <span>{strings.bundleCard.separately}</span>
              <span className={`tabular-nums ${saving != null ? "line-through" : ""}`}>
                {formatPrice(bundle.listPrice, "")}
              </span>
            </div>
          ) : null}
          <div className="flex justify-between gap-2 font-medium">
            <span>{strings.bundleCard.bundlePrice}</span>
            <span className="tabular-nums">{formatPrice(bundle.price, strings.price.ask)}</span>
          </div>
          {saving != null ? (
            <p className="text-right text-cyan-edge">{strings.bundleCard.saving(formatPrice(saving, ""))}</p>
          ) : null}
        </div>

        <div className="space-y-2">
          <p
            className={`text-xs ${bundle.availability === "AGOTADO" ? "text-destructive" : "text-muted-foreground"}`}
          >
            {strings.availability[bundle.availability]}
          </p>
          <BundleCartButton bundle={bundle} className="w-full" />
        </div>
      </DialogContent>
    </Dialog>
  )
}
