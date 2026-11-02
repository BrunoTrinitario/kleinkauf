
import axios from "axios"
import { useEffect, useMemo, useState } from "react"
import type { Product, ProductMetadata } from "@/lib/types"
import { defaultProductMetadata, normalizeMetadata } from "@/lib/types"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
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
import { X } from "lucide-react"

interface AdminProductFormModalProps {
  // null = alta de un producto nuevo; con producto = edicion
  product: Product | null
  open: boolean
  onOpenChange: (open: boolean) => void
  token: string | null
  // Precio y costo piden el permiso PRECIOS: sin el, esos campos no se muestran
  canEditPrices: boolean
  onSaved: () => void
}

interface ImageDto {
  id: number
  path: string
  order: number
}

// Mismo limite que el backend (ProductService.MAX_IMAGE_BYTES)
const MAX_IMAGE_BYTES = 1024 * 1024

const textareaClass =
  "w-full min-h-[96px] px-3 py-2 text-sm rounded-md border border-input bg-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"

// "" = sin dato (precio null = "Consultar precio")
const toAmount = (value: string): number | null => (value.trim() === "" ? null : Number(value))

export function AdminProductFormModal({
  product,
  open,
  onOpenChange,
  token,
  canEditPrices,
  onSaved,
}: AdminProductFormModalProps) {
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"
  const isEdit = product !== null

  const [name, setName] = useState("")
  const [category, setCategory] = useState("")
  const [description, setDescription] = useState("")
  const [price, setPrice] = useState("")
  const [cost, setCost] = useState("")
  const [initialPricing, setInitialPricing] = useState({ price: "", cost: "" })
  const [stock, setStock] = useState(0)
  const [minStock, setMinStock] = useState(0)
  const [metadata, setMetadata] = useState<ProductMetadata>(defaultProductMetadata)
  const [images, setImages] = useState<ImageDto[]>([])
  const [pendingFiles, setPendingFiles] = useState<File[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const headers = { Authorization: `Bearer ${token}` }

  // Vista previa de las imagenes elegidas que todavia no se subieron (se liberan al cambiar)
  const previews = useMemo(() => pendingFiles.map((file) => URL.createObjectURL(file)), [pendingFiles])
  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews])

  // Al abrir: vacio para un alta, o los datos del producto para editar
  useEffect(() => {
    if (!open) return
    setName(product?.name ?? "")
    setCategory(product?.category ?? "")
    setDescription(product?.description ?? "")
    const productPrice = product?.price != null ? String(product.price) : ""
    setPrice(productPrice)
    setCost("")
    setInitialPricing({ price: productPrice, cost: "" })
    setStock(product?.stock ?? 0)
    setMinStock(product?.minStock ?? 0)
    setMetadata(defaultProductMetadata)
    setImages([])
    setPendingFiles([])
    setError(null)
  }, [product, open])

  // En edicion: metadatos, imagenes y (con PRECIOS) el costo, que no viene en el listado
  useEffect(() => {
    if (!product || !open || !token) return
    let isActive = true
    const auth = { headers: { Authorization: `Bearer ${token}` } }

    axios
      .get(`${apiBasePublic}/api/v1/products/${product.id}/metadata`, auth)
      // Sin metadatos el backend responde vacio
      .then((response) => isActive && setMetadata(normalizeMetadata(response.data === "" ? null : response.data)))
      .catch(() => isActive && setMetadata(defaultProductMetadata))

    axios
      .get<ImageDto[]>(`${apiBasePublic}/api/v1/products/${product.id}/images`, auth)
      .then((response) => isActive && setImages(response.data ?? []))
      .catch((err) => isActive && notifyError(err, strings.errors.loadImages))

    if (canEditPrices) {
      axios
        .get<{ price: number | null; cost: number | null }>(
          `${apiBasePublic}/api/v1/products/${product.id}/pricing`,
          auth
        )
        .then((response) => {
          if (!isActive) return
          const loaded = {
            price: response.data.price != null ? String(response.data.price) : "",
            cost: response.data.cost != null ? String(response.data.cost) : "",
          }
          setPrice(loaded.price)
          setCost(loaded.cost)
          setInitialPricing(loaded)
        })
        .catch(() => {})
    }

    return () => {
      isActive = false
    }
  }, [apiBasePublic, open, product, token, canEditPrices])

  const handleFileSelection = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ""
    const tooLarge = files.find((file) => file.size > MAX_IMAGE_BYTES)
    if (tooLarge) {
      setError(strings.adminProductEdit.imageTooLarge(tooLarge.name))
      return
    }
    setError(null)
    setPendingFiles((prev) => [...prev, ...files])
  }

  const refreshImages = async (productId: string) => {
    const response = await axios.get<ImageDto[]>(
      `${apiBasePublic}/api/v1/products/${productId}/images`,
      { headers }
    )
    setImages(response.data ?? [])
  }

  // Quitar una imagen ya guardada es inmediato (como antes en la edicion)
  const handleRemoveImage = async (image: ImageDto) => {
    if (!token || !product) return
    try {
      await axios.delete(`${apiBasePublic}/api/v1/products/images/${image.id}`, { headers })
      await refreshImages(product.id)
    } catch (err) {
      notifyError(err, strings.errors.deleteImage)
    }
  }

  const validate = (): string | null => {
    if (name.trim() === "") return strings.adminProductEdit.nameRequired
    const amounts = [toAmount(price), toAmount(cost)]
    if (amounts.some((value) => value !== null && (Number.isNaN(value) || value < 0))) {
      return strings.adminProductEdit.invalidNumbers
    }
    if (stock < 0 || minStock < 0) return strings.adminProductEdit.invalidNumbers
    return null
  }

  const handleSave = async () => {
    if (!token) return
    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }

    setIsSaving(true)
    setError(null)
    try {
      let productId: string
      const basic = { name: name.trim(), category: category.trim(), description, minStock }

      if (product) {
        productId = product.id
        // El stock no se edita aca: cambia con movimientos de stock
        await axios.put(`${apiBasePublic}/api/v1/products/${productId}`, basic, { headers })
        const pricingChanged = price !== initialPricing.price || cost !== initialPricing.cost
        if (canEditPrices && pricingChanged) {
          await axios.put(
            `${apiBasePublic}/api/v1/products/${productId}/pricing`,
            { price: toAmount(price), cost: toAmount(cost) },
            { headers }
          )
        }
      } else {
        // El stock inicial queda registrado como movimiento de entrada
        const response = await axios.post(
          `${apiBasePublic}/api/v1/products`,
          {
            ...basic,
            stock,
            price: canEditPrices ? toAmount(price) : null,
            cost: canEditPrices ? toAmount(cost) : null,
          },
          { headers }
        )
        productId = String(response.data.id)
      }

      await axios.put(`${apiBasePublic}/api/v1/products/${productId}/metadata`, metadata, { headers })

      if (pendingFiles.length > 0) {
        const formData = new FormData()
        pendingFiles.forEach((file) => formData.append("files", file))
        await axios.post(`${apiBasePublic}/api/v1/products/${productId}/images`, formData, { headers })
      }

      onSaved()
    } catch (err) {
      setError(getErrorMessage(err, isEdit ? strings.errors.saveProduct : strings.adminProducts.createError))
    } finally {
      setIsSaving(false)
    }
  }

  const numberField = (
    label: string,
    value: number,
    onChange: (value: number) => void,
    hint?: string
  ) => (
    <div className="grid gap-2">
      <label className="text-sm font-medium">{label}</label>
      <Input type="number" min={0} value={value} onChange={(e) => onChange(Number(e.target.value) || 0)} />
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )

  const textMetadata = (key: "material" | "color" | "style", label: string) => (
    <div className="grid gap-2">
      <label className="text-sm font-medium">{label}</label>
      <Input
        value={metadata[key]}
        onChange={(e) => setMetadata((prev) => ({ ...prev, [key]: e.target.value }))}
      />
    </div>
  )

  const numberMetadata = (key: "height" | "width" | "length" | "weight", label: string) =>
    numberField(label, metadata[key], (value) => setMetadata((prev) => ({ ...prev, [key]: value })))

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? strings.adminProductEdit.title : strings.adminProductEdit.createTitle}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? strings.adminProductEdit.description : strings.adminProductEdit.createDescription}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.images}</h3>
            {images.length === 0 && pendingFiles.length === 0 ? (
              <p className="text-sm text-muted-foreground">{strings.adminProductEdit.noImages}</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {images.map((image) => (
                  <div key={image.id} className="relative h-20 w-20 overflow-hidden rounded-md border border-border bg-muted">
                    <img
                      src={`${apiBasePublic}/products/images/${image.path}`}
                      alt=""
                      className="h-full w-full object-cover"
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveImage(image)}
                      className="absolute right-1 top-1 rounded-full bg-background/90 p-0.5 text-foreground shadow-sm hover:bg-background"
                      aria-label={strings.adminProductEdit.removeImage}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                ))}
                {pendingFiles.map((file, index) => (
                  <div
                    key={`${file.name}-${index}`}
                    className="relative h-20 w-20 overflow-hidden rounded-md border border-dashed border-border bg-muted"
                  >
                    <img src={previews[index]} alt="" className="h-full w-full object-cover opacity-70" />
                    <button
                      type="button"
                      onClick={() => setPendingFiles((prev) => prev.filter((_, i) => i !== index))}
                      className="absolute right-1 top-1 rounded-full bg-background/90 p-0.5 text-foreground shadow-sm hover:bg-background"
                      aria-label={strings.adminProductEdit.removeImage}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
            <label className="inline-flex">
              <input type="file" multiple accept="image/png" onChange={handleFileSelection} className="sr-only" />
              <span className="cursor-pointer rounded-full border border-border px-4 py-2 text-sm hover:bg-muted">
                {strings.adminProductEdit.addImages}
              </span>
            </label>
            {pendingFiles.length > 0 ? (
              <p className="text-xs text-muted-foreground">
                {strings.adminProductEdit.pendingImages(pendingFiles.length)}
              </p>
            ) : null}
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.basicInfo}</h3>
            <div className="grid gap-3">
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.nameLabel}</label>
                <Input value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.categoryLabel}</label>
                <Input value={category} onChange={(e) => setCategory(e.target.value)} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminProducts.descriptionLabel}</label>
                <textarea className={textareaClass} value={description} onChange={(e) => setDescription(e.target.value)} />
              </div>
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.priceAndStock}</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              {canEditPrices ? (
                <>
                  <div className="grid gap-2">
                    <label className="text-sm font-medium">{strings.adminProductEdit.price}</label>
                    <Input
                      type="number"
                      min={0}
                      step="0.01"
                      placeholder={strings.adminProductEdit.pricePlaceholder}
                      value={price}
                      onChange={(e) => setPrice(e.target.value)}
                    />
                  </div>
                  <div className="grid gap-2">
                    <label className="text-sm font-medium">{strings.adminProductEdit.cost}</label>
                    <Input type="number" min={0} step="0.01" value={cost} onChange={(e) => setCost(e.target.value)} />
                  </div>
                </>
              ) : null}
              {isEdit ? (
                <div className="grid gap-2">
                  <label className="text-sm font-medium">{strings.adminProductEdit.currentStock}</label>
                  <Input type="number" value={stock} disabled readOnly />
                  <p className="text-xs text-muted-foreground">{strings.adminProductEdit.stockHint}</p>
                </div>
              ) : (
                numberField(strings.adminProductEdit.initialStock, stock, setStock)
              )}
              {numberField(strings.adminProductEdit.minStock, minStock, setMinStock, strings.adminProductEdit.minStockHint)}
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminProductEdit.metadata}</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              {textMetadata("material", strings.adminProductEdit.material)}
              {textMetadata("color", strings.adminProductEdit.color)}
              {textMetadata("style", strings.adminProductEdit.style)}
              {numberMetadata("height", strings.adminProductEdit.height)}
              {numberMetadata("width", strings.adminProductEdit.width)}
              {numberMetadata("length", strings.adminProductEdit.length)}
              {numberMetadata("weight", strings.adminProductEdit.weight)}
            </div>
          </section>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {strings.adminProductEdit.cancel}
          </Button>
          <Button onClick={handleSave} disabled={isSaving || !token}>
            {isSaving
              ? strings.adminProductEdit.saving
              : isEdit
                ? strings.adminProductEdit.save
                : strings.adminProductEdit.create}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
