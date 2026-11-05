import { useEffect, useState, type ElementType, type ReactNode } from "react"
import type { CatalogProduct, ProductMetadata } from "@/lib/types"
import { strings } from "@/lib/strings"
import { formatPrice } from "@/lib/utils"
import { ShareButtons } from "@/components/share-buttons"
import { AddToCartButton } from "@/components/add-to-cart-button"
import { Package, Check, ChevronLeft, ChevronRight } from "lucide-react"

interface ProductDetailProps {
  product: CatalogProduct
  metadata: ProductMetadata | null
  metadataError: string | null
  // Image URLs in order (the modal preloads them; the page uses the gateway's)
  imageSrcs: string[]
  // In the modal they are DialogTitle and DialogDescription (dialog accessibility); on the page, h1 and p
  titleAs?: ElementType
  descriptionAs?: ElementType
  // Below the availability (e.g. the link to the product page from the modal)
  extra?: ReactNode
}

// Only the data that was entered: empty text or a 0 measurement = no value
function metadataRowsOf(metadata: ProductMetadata | null): [string, string][] {
  const rows: [string, string][] = []
  if (!metadata) return rows
  if (metadata.material?.trim()) rows.push([strings.productModal.material, metadata.material])
  if (metadata.color?.trim()) rows.push([strings.productModal.color, metadata.color])
  if (metadata.style?.trim()) rows.push([strings.productModal.style, metadata.style])
  if (metadata.height || metadata.width || metadata.length) {
    rows.push([strings.productModal.dimensions, `${metadata.height} x ${metadata.width} x ${metadata.length}`])
  }
  if (metadata.weight) rows.push([strings.productModal.weight, String(metadata.weight)])
  return rows
}

// Detail of a catalog product: images, data, availability, metadata and sharing.
// Used by the home page modal and the product page (where the shared link leads)
export function ProductDetail({
  product,
  metadata,
  metadataError,
  imageSrcs,
  titleAs: Title = "h1",
  descriptionAs: Description = "p",
  extra,
}: ProductDetailProps) {
  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const metadataRows = metadataRowsOf(metadata)
  const count = imageSrcs.length

  useEffect(() => {
    setCurrentImageIndex(0)
  }, [product.slug, count])

  return (
    // grid-cols-1 (minmax(0, 1fr)) and min-w-0 on the columns: without them, depending on the browser the column grows to the
    // content's minimum width and overflows the container on mobile (the image and the arrow got cut off)
    <div className="grid grid-cols-1 md:grid-cols-2 min-w-0">
      {/* contain and not cover: the column follows the text height and images can have any aspect ratio;
          with cover a good part got cropped (e.g. a 4:3 photo in a 2:3 column). The background fills the rest */}
      <div className="relative min-w-0 aspect-square md:aspect-auto md:h-full md:min-h-[24rem] bg-muted">
        {count > 0 ? (
          <div className="relative h-full w-full">
            <img
              src={imageSrcs[Math.min(currentImageIndex, count - 1)]}
              alt={product.name}
              className="absolute inset-0 h-full w-full object-contain"
              loading="lazy"
            />
            {count > 1 ? (
              <div className="absolute inset-x-0 bottom-3 flex items-center justify-between px-3">
                <button
                  type="button"
                  onClick={() => setCurrentImageIndex((prev) => (prev === 0 ? count - 1 : prev - 1))}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-background/80 text-foreground shadow-sm ring-1 ring-border/60 transition hover:bg-background"
                  aria-label={strings.productModal.prevImage}
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setCurrentImageIndex((prev) => (prev === count - 1 ? 0 : prev + 1))}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-background/80 text-foreground shadow-sm ring-1 ring-border/60 transition hover:bg-background"
                  aria-label={strings.productModal.nextImage}
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="h-full w-full flex items-center justify-center text-sm text-muted-foreground">
            {strings.productModal.noImages}
          </div>
        )}
      </div>

      <div className="min-w-0 p-6 flex flex-col">
        <div className="text-left space-y-1">
          <p className="text-xs text-muted-foreground uppercase tracking-wider">{product.category}</p>
          <Title className="text-subheading">{product.name}</Title>
        </div>

        <div className="mt-4 flex-1 space-y-6">
          <Description className="text-muted-foreground leading-relaxed">{product.description}</Description>

          <p className="text-lg font-medium text-foreground">{formatPrice(product.price, strings.price.ask)}</p>

          <AddToCartButton
            item={{
              type: "PRODUCT",
              id: product.id,
              name: product.name,
              price: product.price,
              image: product.images?.[0] ?? null,
              slug: product.slug,
            }}
            availability={product.availability}
          />

          {/* Availability: the catalog does not show the exact stock */}
          <div className="flex items-center gap-2 text-sm">
            {product.availability === "DISPONIBLE" ? (
              <Check className="h-4 w-4 text-primary" />
            ) : product.availability === "POCAS_UNIDADES" ? (
              <Package className="h-4 w-4 text-foreground" />
            ) : null}
            <span className={product.availability === "AGOTADO" ? "text-destructive" : "text-muted-foreground"}>
              {strings.availability[product.availability]}
            </span>
          </div>

          {metadataRows.length > 0 ? (
            <div className="space-y-2 text-sm text-muted-foreground">
              {metadataRows.map(([label, value]) => (
                <p key={label}>
                  {label} {value}
                </p>
              ))}
            </div>
          ) : metadataError ? (
            <p className="text-sm text-destructive">{metadataError}</p>
          ) : null}

          <div className="space-y-2">
            <ShareButtons slug={product.slug} name={product.name} />
            {extra}
          </div>
        </div>
      </div>
    </div>
  )
}
