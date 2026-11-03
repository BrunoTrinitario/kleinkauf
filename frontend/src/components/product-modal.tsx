
import { useEffect, useMemo, useState } from "react"
import { productImageUrl } from "@/lib/api"
import type { CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { useCatalogProduct } from "@/api/catalog"
import { formatPrice } from "@/lib/utils"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Package, Check, ChevronLeft, ChevronRight } from "lucide-react"

interface ProductModalProps {
  product: CatalogProduct | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

const imageObjectUrlCache = new Map<string, string>()

const preloadImageAsObjectUrl = async (url: string) => {
  const cached = imageObjectUrlCache.get(url)
  if (cached) return cached
  const response = await fetch(url)
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  imageObjectUrlCache.set(url, objectUrl)
  return objectUrl
}

export function ProductModal({ product, open, onOpenChange }: ProductModalProps) {
  if (!product) return null
  return <ProductModalContent product={product} open={open} onOpenChange={onOpenChange} />
}

function ProductModalContent({
  product,
  open,
  onOpenChange,
}: ProductModalProps & { product: CatalogProduct }) {
  const imageUrls = useMemo(
    () => (product.images ?? []).map((imageName) => productImageUrl(imageName)),
    [product.images]
  )

  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const [resolvedImageUrls, setResolvedImageUrls] = useState<string[]>([])

  useEffect(() => {
    if (!open) return
    setCurrentImageIndex(0)
  }, [open, imageUrls.length])

  useEffect(() => {
    if (!open) return
    let isActive = true

    const preloadAll = async () => {
      try {
        const resolved = await Promise.all(
          imageUrls.map((url) => preloadImageAsObjectUrl(url))
        )
        if (isActive) {
          setResolvedImageUrls(resolved)
        }
      } catch {
        if (isActive) {
          setResolvedImageUrls(imageUrls)
        }
      }
    }

    preloadAll()

    return () => {
      isActive = false
    }
  }, [open, imageUrls])

  // Los metadatos vienen en el detalle publico por slug; null = el producto no los tiene cargados
  const detail = useCatalogProduct(product.slug, open)
  const metadata = detail.data?.metadata ?? null
  const metadataError = detail.isError ? getErrorMessage(detail.error, strings.productModal.metadataError) : null

  const metadataRows: [string, string][] = []
  if (metadata) {
    if (metadata.material?.trim()) metadataRows.push([strings.productModal.material, metadata.material])
    if (metadata.color?.trim()) metadataRows.push([strings.productModal.color, metadata.color])
    if (metadata.style?.trim()) metadataRows.push([strings.productModal.style, metadata.style])
    if (metadata.height || metadata.width || metadata.length) {
      metadataRows.push([
        strings.productModal.dimensions,
        `${metadata.height} x ${metadata.width} x ${metadata.length}`,
      ])
    }
    if (metadata.weight) metadataRows.push([strings.productModal.weight, String(metadata.weight)])
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* max-h + scroll: en pantallas bajas el detalle no entra entero */}
      <DialogContent className="grid-cols-1 sm:max-w-3xl p-0 gap-0 max-h-[90vh] overflow-x-hidden overflow-y-auto">
        {/* grid-cols-1 (minmax(0, 1fr)) aca y en el DialogContent, y min-w-0 en las columnas: sin eso,
            segun el navegador la columna crece al ancho minimo del contenido y se sale del dialogo
            en mobile (la imagen, la flecha de "siguiente" y el texto quedaban cortados a la derecha) */}
        <div className="grid grid-cols-1 md:grid-cols-2 min-w-0">
          {/* Product Image */}
          <div className="relative min-w-0 aspect-square md:aspect-auto md:h-full bg-muted">
            {imageUrls.length > 0 ? (
              <div className="relative h-full w-full">
                <img
                  src={(resolvedImageUrls.length ? resolvedImageUrls : imageUrls)[currentImageIndex]}
                  alt={product.name}
                  className="h-full w-full object-cover"
                  loading="lazy"
                />
                {imageUrls.length > 1 ? (
                  <div className="absolute inset-x-0 bottom-3 flex items-center justify-between px-3">
                    <button
                      type="button"
                      onClick={() =>
                        setCurrentImageIndex((prev) =>
                          prev === 0 ? imageUrls.length - 1 : prev - 1
                        )
                      }
                      className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-background/80 text-foreground shadow-sm ring-1 ring-border/60 transition hover:bg-background"
                      aria-label={strings.productModal.prevImage}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        setCurrentImageIndex((prev) =>
                          prev === imageUrls.length - 1 ? 0 : prev + 1
                        )
                      }
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

          {/* Product Details */}
          <div className="min-w-0 p-6 flex flex-col">
            <DialogHeader className="text-left space-y-1">
              <p className="text-xs text-muted-foreground uppercase tracking-wider">
                {product.category}
              </p>
              <DialogTitle className="text-subheading">
                {product.name}
              </DialogTitle>
            </DialogHeader>

            <div className="mt-4 flex-1 space-y-6">
              <DialogDescription className="text-muted-foreground leading-relaxed">
                {product.description}
              </DialogDescription>

              <p className="text-lg font-medium text-foreground">
                {formatPrice(product.price, strings.price.ask)}
              </p>

              {/* Disponibilidad: el catalogo no muestra el stock exacto */}
              <div className="flex items-center gap-2 text-sm">
                {product.availability === "DISPONIBLE" ? (
                  <Check className="h-4 w-4 text-primary" />
                ) : product.availability === "POCAS_UNIDADES" ? (
                  <Package className="h-4 w-4 text-foreground" />
                ) : null}
                <span
                  className={
                    product.availability === "AGOTADO" ? "text-destructive" : "text-muted-foreground"
                  }
                >
                  {strings.availability[product.availability]}
                </span>
              </div>

              {/* Metadata: solo los datos cargados (texto vacio o medida en 0 = sin dato) */}
              {metadataRows.length > 0 ? (
                <div className="space-y-2 text-sm text-muted-foreground">
                  {metadataRows.map(([label, value]) => (
                    <p key={label}>{label} {value}</p>
                  ))}
                </div>
              ) : metadataError ? (
                <p className="text-sm text-destructive">{metadataError}</p>
              ) : null}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
