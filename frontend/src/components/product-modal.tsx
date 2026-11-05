
import { useEffect, useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { productImageUrl } from "@/lib/api"
import type { CatalogProduct } from "@/lib/types"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { useCatalogProduct } from "@/api/catalog"
import { ProductDetail } from "@/components/product-detail"
import { Dialog, DialogContent, DialogTitle, DialogDescription } from "@/components/ui/dialog"

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

  // Preloaded as object URLs: switching from one image to another does not flicker
  const [resolvedImageUrls, setResolvedImageUrls] = useState<string[]>([])

  useEffect(() => {
    if (!open) return
    let isActive = true

    const preloadAll = async () => {
      try {
        const resolved = await Promise.all(imageUrls.map((url) => preloadImageAsObjectUrl(url)))
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

  // Metadata comes in the public detail by slug; null = the product does not have it set
  const detail = useCatalogProduct(product.slug, open)
  const metadata = detail.data?.metadata ?? null
  const metadataError = detail.isError ? getErrorMessage(detail.error, strings.productModal.metadataError) : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {/* max-h + scroll: on short screens the detail does not fit entirely */}
      <DialogContent className="grid-cols-1 sm:max-w-3xl p-0 gap-0 max-h-[90vh] overflow-x-hidden overflow-y-auto">
        <ProductDetail
          product={product}
          metadata={metadata}
          metadataError={metadataError}
          imageSrcs={resolvedImageUrls.length ? resolvedImageUrls : imageUrls}
          titleAs={DialogTitle}
          descriptionAs={DialogDescription}
          extra={
            <Link
              to={`/productos/${product.slug}`}
              className="inline-block text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
            >
              {strings.productPage.viewPage}
            </Link>
          }
        />
      </DialogContent>
    </Dialog>
  )
}
