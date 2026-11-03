import { useEffect, useState, type ElementType, type ReactNode } from "react"
import type { CatalogProduct, ProductMetadata } from "@/lib/types"
import { strings } from "@/lib/strings"
import { formatPrice } from "@/lib/utils"
import { ShareButtons } from "@/components/share-buttons"
import { Package, Check, ChevronLeft, ChevronRight } from "lucide-react"

interface ProductDetailProps {
  product: CatalogProduct
  metadata: ProductMetadata | null
  metadataError: string | null
  // URLs de las imagenes en orden (el modal las precarga; la pagina usa las del gateway)
  imageSrcs: string[]
  // En el modal son DialogTitle y DialogDescription (accesibilidad del dialogo); en la pagina, h1 y p
  titleAs?: ElementType
  descriptionAs?: ElementType
  // Debajo de la disponibilidad (ej. el link a la pagina del producto desde el modal)
  extra?: ReactNode
}

// Solo los datos cargados: texto vacio o medida en 0 = sin dato
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

// Detalle de un producto del catalogo: imagenes, datos, disponibilidad, metadatos y compartir.
// Lo usan el modal de la home y la pagina del producto (a donde lleva el link compartido)
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
    // grid-cols-1 (minmax(0, 1fr)) y min-w-0 en las columnas: sin eso, segun el navegador la columna crece al ancho
    // minimo del contenido y se sale del contenedor en mobile (la imagen y la flecha quedaban cortadas)
    <div className="grid grid-cols-1 md:grid-cols-2 min-w-0">
      <div className="relative min-w-0 aspect-square md:aspect-auto md:h-full bg-muted">
        {count > 0 ? (
          <div className="relative h-full w-full">
            <img
              src={imageSrcs[Math.min(currentImageIndex, count - 1)]}
              alt={product.name}
              className="h-full w-full object-cover"
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

          {/* Disponibilidad: el catalogo no muestra el stock exacto */}
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
