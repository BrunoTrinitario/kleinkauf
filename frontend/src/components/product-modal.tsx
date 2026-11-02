
import axios from "axios"
import { useEffect, useMemo, useState } from "react"
import type { CatalogProduct, CatalogProductDetail, ProductMetadata } from "@/lib/types"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { formatPrice } from "@/lib/utils"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Package, Check, ChevronLeft, ChevronRight } from "lucide-react"

interface ProductModalProps {
  product: CatalogProduct | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

const imageObjectUrlCache = new Map<string, string>()
const metadataCache = new Map<string, ProductMetadata | null>()

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
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  const imageUrls = useMemo(
    () =>
      (product.images ?? []).map(
        (imageName) => `${apiBasePublic}/products/images/${imageName}`
      ),
    [apiBasePublic, product.images]
  )

  const [currentImageIndex, setCurrentImageIndex] = useState(0)
  const [resolvedImageUrls, setResolvedImageUrls] = useState<string[]>([])

  const [metadata, setMetadata] = useState<ProductMetadata | null>(null)
  const [metadataError, setMetadataError] = useState<string | null>(null)

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

  useEffect(() => {
    if (!open) return
    let isActive = true

    const fetchMetadata = async () => {
      try {
        setMetadataError(null)
        // Los metadatos vienen en el detalle publico por slug; null = el producto no los tiene cargados
        const cacheKey = `${apiBasePublic}/api/v1/catalog/products/${product.slug}`
        if (metadataCache.has(cacheKey)) {
          if (isActive) setMetadata(metadataCache.get(cacheKey) ?? null)
          return
        }
        const response = await axios.get<CatalogProductDetail>(cacheKey)
        if (isActive) {
          const resolved = response.data.metadata ?? null
          metadataCache.set(cacheKey, resolved)
          setMetadata(resolved)
        }
      } catch (err) {
        if (isActive) {
          setMetadataError(getErrorMessage(err, strings.productModal.metadataError))
        }
      }
    }

    fetchMetadata()

    return () => {
      isActive = false
    }
  }, [apiBasePublic, open, product.slug])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl p-0 gap-0 overflow-hidden">
        <div className="grid md:grid-cols-2">
          {/* Product Image */}
          <div className="relative aspect-square md:aspect-auto md:h-full bg-muted">
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
          <div className="p-6 flex flex-col">
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

              {/* Metadata */}
              {metadata ? (
                <div className="space-y-2 text-sm text-muted-foreground">
                  <p>{strings.productModal.material} {metadata.material}</p>
                  <p>{strings.productModal.color} {metadata.color}</p>
                  <p>{strings.productModal.style} {metadata.style}</p>
                  <p>
                    {strings.productModal.dimensions} {metadata.height} x {metadata.width} x {metadata.length}
                  </p>
                  <p>{strings.productModal.weight} {metadata.weight}</p>
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
