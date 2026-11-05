import { useEffect, useRef, useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { ArrowRight, ChevronLeft, ChevronRight, ImageIcon } from "lucide-react"
import type { CatalogBundle, CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { productImageUrl } from "@/lib/api"
import { formatPrice } from "@/lib/utils"
import { CATALOG_PATH } from "@/lib/catalog-url"
import { useCatalogBundles, useCatalogFeatured, useCatalogProducts } from "@/api/catalog"
import { ProductCard } from "./product-card"
import { Button } from "./ui/button"
import { ProductModal } from "./product-modal"
import { AddToCartButton } from "./add-to-cart-button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "./ui/dialog"

// Home page strip: on phones it scrolls sideways; from sm, a grid. Not shown without items
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

// A single row at every width: on phones each card takes almost the full width (so it shows there is more to the
// right), 2 fit from sm and 4 on desktop; the rest is reached by swiping or with the arrows
const stripRow = "-mx-4 flex snap-x snap-mandatory scroll-px-4 gap-4 overflow-x-auto px-4 pb-2 sm:gap-6 [scrollbar-width:none]"
const stripItem = "grid w-[75%] shrink-0 snap-start sm:w-[calc((100%-1.5rem)/2)] lg:w-[calc((100%-4.5rem)/4)]"

// Row with arrows that move one screen at a time (4 cards on desktop). The last screen ends at the last item even
// if fewer than 4 are left; from there the right arrow goes back to the start (and the left one, from the start,
// goes to the end). The arrows only show up when there are more items than fit
function Carousel({ children }: { children: ReactNode }) {
  const rowRef = useRef<HTMLDivElement>(null)
  const [overflows, setOverflows] = useState(false)

  useEffect(() => {
    const row = rowRef.current
    if (!row) return
    const update = () => setOverflows(row.scrollWidth > row.clientWidth + 1)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(row)
    return () => observer.disconnect()
  }, [children])

  const move = (direction: 1 | -1) => {
    const row = rowRef.current
    const first = row?.firstElementChild as HTMLElement | null
    if (!row || !first) return
    const style = getComputedStyle(row)
    const gap = parseFloat(style.columnGap) || 0
    const visible = row.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight)
    const itemStep = first.offsetWidth + gap
    const perPage = Math.max(1, Math.round((visible + gap) / itemStep))
    const maxScroll = row.scrollWidth - row.clientWidth
    const atStart = row.scrollLeft <= 1
    const atEnd = row.scrollLeft >= maxScroll - 1

    if (direction === 1 && atEnd) row.scrollTo({ left: 0, behavior: "smooth" })
    else if (direction === -1 && atStart) row.scrollTo({ left: maxScroll, behavior: "smooth" })
    else row.scrollBy({ left: direction * perPage * itemStep, behavior: "smooth" })
  }

  const arrow = "absolute top-1/2 z-10 hidden -translate-y-1/2 rounded-full shadow-md sm:inline-flex"

  return (
    <div className="relative">
      <div ref={rowRef} className={stripRow}>
        {children}
      </div>
      {overflows ? (
        <>
          <Button
            variant="outline"
            size="icon"
            className={`${arrow} -left-3`}
            onClick={() => move(-1)}
            aria-label={strings.home.previous}
          >
            <ChevronLeft className="h-5 w-5" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            className={`${arrow} -right-3`}
            onClick={() => move(1)}
            aria-label={strings.home.next}
          >
            <ChevronRight className="h-5 w-5" />
          </Button>
        </>
      ) : null}
    </div>
  )
}

// Home page featured products, in the order the store chose
export function FeaturedStrip() {
  const featured = useCatalogFeatured().data ?? []
  const [selected, setSelected] = useState<CatalogProduct | null>(null)

  if (featured.length === 0) return null

  return (
    <Strip title={strings.home.featuredTitle}>
      <Carousel>
        {featured.map((product) => (
          <div key={product.slug} className={stripItem}>
            <ProductCard product={product} onClick={() => setSelected(product)} />
          </div>
        ))}
      </Carousel>
      <ProductModal product={selected} open={selected !== null} onOpenChange={(open) => !open && setSelected(null)} />
    </Strip>
  )
}

// Home page bundles with the same card as /combos, in a row like the featured products
export function BundlesStrip() {
  const bundles = useCatalogBundles().data ?? []

  if (bundles.length === 0) return null

  return (
    <Strip
      title={strings.home.bundlesTitle}
      action={<StripLink to="/combos">{strings.home.allBundles}</StripLink>}
    >
      <Carousel>
        {bundles.map((bundle) => (
          <div key={bundle.id} className={stripItem}>
            <BundleCard bundle={bundle} />
          </div>
        ))}
      </Carousel>
    </Strip>
  )
}

// First catalog page (default order) in a grid, as in /catalogo; at the end, the link to the full catalog
export function CatalogStrip() {
  const products = useCatalogProducts({ page: 0 }).data?.items ?? []
  const [selected, setSelected] = useState<CatalogProduct | null>(null)

  if (products.length === 0) return null

  return (
    <Strip title={strings.home.catalogTitle}>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {products.map((product) => (
          <ProductCard key={product.slug} product={product} onClick={() => setSelected(product)} />
        ))}
      </div>
      <div className="flex justify-center pt-2">
        <Button asChild variant="outline">
          <Link to={CATALOG_PATH}>
            {strings.home.allProducts}
            <ArrowRight className="h-4 w-4" />
          </Link>
        </Button>
      </div>
      <ProductModal product={selected} open={selected !== null} onOpenChange={(open) => !open && setSelected(null)} />
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

// Bundle photo: with several products with an image, a mosaic of up to 4; otherwise, the bundle image
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

// Full bundle card (/combos): savings versus buying separately, what it includes and add to cart.
// Tapping the photo opens the detail: the products with their photo, description and standalone price
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

// Bundle detail: each product with its photo, category, description, quantity and standalone price, and at the end the
// sum of what it costs separately against the bundle price
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
