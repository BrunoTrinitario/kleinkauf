
import axios from "axios"
import { useEffect, useMemo, useState } from "react"
import type { Product, ProductMetadata } from "@/lib/types"
import { defaultProductMetadata } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

interface AdminProductEditModalProps {
  product: Product | null
  open: boolean
  onOpenChange: (open: boolean) => void
  token: string | null
  onSave?: (product: Product, metadata: ProductMetadata) => void
}

interface ImageDto {
  id: number
  path: string
  order: number
}

export function AdminProductEditModal({
  product,
  open,
  onOpenChange,
  token,
  onSave,
}: AdminProductEditModalProps) {
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [category, setCategory] = useState("")
  const [stock, setStock] = useState(0)
  const [images, setImages] = useState<string[]>([])
  const [imageDtos, setImageDtos] = useState<ImageDto[]>([])
  const [pendingFiles, setPendingFiles] = useState<File[]>([])
  const [metadata, setMetadata] = useState<ProductMetadata>(defaultProductMetadata)

  const canSave = useMemo(() => !!onSave && !!product, [onSave, product])

  useEffect(() => {
    if (!product || !open) return

    setName(product.name ?? "")
    setDescription(product.description ?? "")
    setCategory(product.category ?? "")
    setStock(product.stock ?? 0)
    setImages(product.images ?? [])
    setImageDtos([])
    setPendingFiles([])
  }, [product, open])

  useEffect(() => {
    if (!product || !open || !token) return
    let isActive = true

    const loadMetadata = async () => {
      try {
        const response = await axios.get(
          `${apiBasePublic}/api/v1/products/${product.id}/metadata`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        )
        const data = response.data == "" ? defaultProductMetadata : response.data
        if (isActive) {
          setMetadata(data ?? defaultProductMetadata)
        }
      } catch {
        if (isActive) {
          setMetadata(defaultProductMetadata)
        }
      }
    }

    loadMetadata()

    return () => {
      isActive = false
    }
  }, [apiBasePublic, open, product, token])

  useEffect(() => {
    if (!product || !open || !token) return
    let isActive = true

    const loadImages = async () => {
      try {
        const response = await axios.get(
          `${apiBasePublic}/api/v1/products/${product.id}/images`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
            },
          }
        )
        const data = response.data as ImageDto[]
        if (isActive) {
          setImageDtos(data ?? [])
          setImages((data ?? []).map((img) => img.path))
        }
      } catch {
        if (isActive) {
          setImageDtos([])
        }
      }
    }

    loadImages()

    return () => {
      isActive = false
    }
  }, [apiBasePublic, open, product, token])

  if (!product) return null

  const handleFileSelection = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    if (files.length === 0) return
    setPendingFiles(files)
  }

  const refreshImages = async () => {
    if (!product || !token) return
    const response = await axios.get(
      `${apiBasePublic}/api/v1/products/${product.id}/images`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    )
    const data = response.data as ImageDto[]
    setImageDtos(data ?? [])
    setImages((data ?? []).map((img) => img.path))
  }

  const handleUploadImages = async () => {
    if (!product || !token || pendingFiles.length === 0) return
    const formData = new FormData()
    pendingFiles.forEach((file) => formData.append("files", file))

    await axios.post(
      `${apiBasePublic}/api/v1/products/${product.id}/images`,
      formData,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "multipart/form-data",
        },
      }
    )

    setPendingFiles([])
    await refreshImages()
  }

  const handleRemoveImage = async (image: ImageDto) => {
    if (!token) return
    await axios.delete(`${apiBasePublic}/api/v1/products/images/${image.id}`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
    await refreshImages()
  }

  const handleSave = async () => {
    if (!product || !token) return

    await axios.put(
      `${apiBasePublic}/api/v1/products/${product.id}`,
      {
        name,
        category,
        description,
        stock,
      },
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    )

    await axios.put(
      `${apiBasePublic}/api/v1/products/${product.id}/metadata`,
      {
        material: metadata.material,
        color: metadata.color,
        style: metadata.style,
        height: metadata.height,
        width: metadata.width,
        length: metadata.length,
        weight: metadata.weight,
      },
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    )

    onSave?.(
      {
        ...product,
        name,
        description,
        category,
        stock,
        images,
      },
      metadata
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.adminProductEdit.title}</DialogTitle>
          <DialogDescription>
            {strings.adminProductEdit.description}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.images}</h3>
            <div className="flex flex-wrap gap-2">
              {imageDtos.length === 0 ? (
                <p className="text-sm text-muted-foreground">{strings.adminProductEdit.noImages}</p>
              ) : (
                imageDtos.map((image) => (
                  <div
                    key={image.id}
                    className="flex items-center gap-2 rounded-md border border-border px-2 py-1 text-sm"
                  >
                    <span className="truncate max-w-[180px]">{image.path}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveImage(image)}
                      className="text-muted-foreground hover:text-foreground"
                      aria-label={strings.adminProductEdit.removeImage}
                    >
                      &times;
                    </button>
                  </div>
                ))
              )}
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <input
                type="file"
                multiple
                accept="image/png"
                onChange={handleFileSelection}
                className="block w-full text-sm text-muted-foreground file:mr-4 file:rounded-md file:border-0 file:bg-muted file:px-4 file:py-2 file:text-sm file:font-medium file:text-foreground hover:file:bg-muted/80"
              />
              <Button
                type="button"
                variant="outline"
                onClick={handleUploadImages}
                disabled={pendingFiles.length === 0}
              >
                {strings.adminProductEdit.uploadImages}
              </Button>
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.basicInfo}</h3>
            <div className="grid gap-3">
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.nameLabel}</label>
                <Input value={name} onChange={(event) => setName(event.target.value)} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.categoryLabel}</label>
                <Input value={category} onChange={(event) => setCategory(event.target.value)} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.descriptionLabel}</label>
                <textarea
                  className="w-full min-h-[96px] px-3 py-2 text-sm rounded-md border border-input bg-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.stockLabel}</label>
                <Input
                  type="number"
                  value={stock}
                  onChange={(event) => setStock(Number(event.target.value) || 0)}
                />
              </div>
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.metadata}</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.material}</label>
                <Input
                  value={metadata.material}
                  onChange={(event) =>
                    setMetadata((prev) => ({ ...prev, material: event.target.value }))
                  }
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.color}</label>
                <Input
                  value={metadata.color}
                  onChange={(event) =>
                    setMetadata((prev) => ({ ...prev, color: event.target.value }))
                  }
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.style}</label>
                <Input
                  value={metadata.style}
                  onChange={(event) =>
                    setMetadata((prev) => ({ ...prev, style: event.target.value }))
                  }
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.height}</label>
                <Input
                  type="number"
                  value={metadata.height}
                  onChange={(event) =>
                    setMetadata((prev) => ({
                      ...prev,
                      height: Number(event.target.value) || 0,
                    }))
                  }
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.width}</label>
                <Input
                  type="number"
                  value={metadata.width}
                  onChange={(event) =>
                    setMetadata((prev) => ({
                      ...prev,
                      width: Number(event.target.value) || 0,
                    }))
                  }
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.length}</label>
                <Input
                  type="number"
                  value={metadata.length}
                  onChange={(event) =>
                    setMetadata((prev) => ({
                      ...prev,
                      length: Number(event.target.value) || 0,
                    }))
                  }
                />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProductEdit.weight}</label>
                <Input
                  type="number"
                  value={metadata.weight}
                  onChange={(event) =>
                    setMetadata((prev) => ({
                      ...prev,
                      weight: Number(event.target.value) || 0,
                    }))
                  }
                />
              </div>
            </div>
          </section>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {strings.adminProductEdit.cancel}
          </Button>
          <Button onClick={handleSave} disabled={!canSave || !token}>
            {strings.adminProductEdit.save}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
