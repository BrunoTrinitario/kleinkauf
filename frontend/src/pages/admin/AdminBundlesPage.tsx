import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
import { formatPrice } from "@/lib/utils"
import { productImageUrl } from "@/lib/api"
import { bundlePrice, emptyPricing, parsePricing, pricingDraftOf, type PricingDraft } from "@/lib/bundle-pricing"
import type { Product } from "@/lib/types"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { useAdminProducts } from "@/api/products"
import {
  useBundles,
  useCreateBundle,
  useDeleteBundle,
  useRemoveBundleProduct,
  useSetBundlePrice,
  useSetBundleProduct,
  useSetBundleEnabled,
  type Bundle,
  type BundlePricingType,
  type BundleProduct,
} from "@/api/bundles"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Boxes, ImageIcon, Plus, Trash2, X } from "lucide-react"

const invalidPricingMessage = (draft: PricingDraft) =>
  draft.type === "FIXED" ? strings.adminBundles.invalidPrice : strings.adminBundles.invalidDiscount

/*
Precio del combo: precio fijo, % de descuento o $ de descuento sobre la suma de los productos (listPrice, null si
alguno no tiene precio). Con descuento muestra el precio final mientras se escribe. action va al lado del campo
(guardar en la tarjeta; "usar la suma" en el alta)
*/
function PricingFields({
  id,
  draft,
  onChange,
  listPrice,
  action,
}: {
  id: string
  draft: PricingDraft
  onChange: (draft: PricingDraft) => void
  listPrice: number | null
  action?: React.ReactNode
}) {
  // Lo escrito en cada tipo: al ir de "% de descuento" a "Precio fijo" y volver, el porcentaje sigue ahi
  const valuesByType = useRef<Partial<Record<BundlePricingType, string>>>({})
  const selectType = (type: BundlePricingType) => {
    if (type === draft.type) return
    valuesByType.current[draft.type] = draft.value
    onChange({ type, value: valuesByType.current[type] ?? "" })
  }
  const parsed = parsePricing(draft)
  const final = parsed ? bundlePrice(parsed, listPrice) : null
  const savings = final != null && listPrice != null && listPrice > final ? listPrice - final : null
  const label =
    draft.type === "PERCENT"
      ? strings.adminBundles.discountPercent
      : draft.type === "AMOUNT"
        ? strings.adminBundles.discountAmount
        : strings.adminBundles.price
  const types: BundlePricingType[] = ["FIXED", "PERCENT", "AMOUNT"]

  return (
    <div className="grid grid-cols-1 gap-2">
      <div
        role="radiogroup"
        aria-label={strings.adminBundles.pricingAria}
        className="grid grid-cols-3 gap-1 rounded-md bg-muted p-1"
      >
        {types.map((type) => (
          <button
            key={type}
            type="button"
            role="radio"
            aria-checked={draft.type === type}
            className={`rounded px-2 py-1.5 text-xs font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              draft.type === type ? "bg-background text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
            }`}
            onClick={() => selectType(type)}
          >
            {strings.adminBundles.pricingTypes[type]}
          </button>
        ))}
      </div>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <div className="flex gap-2">
        <Input
          id={id}
          type="number"
          min={0}
          max={draft.type === "PERCENT" ? 100 : undefined}
          step="0.01"
          className="no-spinner"
          value={draft.value}
          placeholder={draft.type === "FIXED" ? strings.adminProductEdit.pricePlaceholder : undefined}
          onChange={(event) => onChange({ ...draft, value: event.target.value })}
        />
        {action}
      </div>
      {draft.type !== "FIXED" ? (
        <div className="space-y-0.5 text-sm">
          {parsed ? (
            final != null ? (
              <p>
                {strings.adminBundles.finalPrice}: <span className="font-medium tabular-nums">{formatPrice(final, "")}</span>
              </p>
            ) : (
              <p className="text-muted-foreground">{strings.adminBundles.finalPriceUnknown}</p>
            )
          ) : null}
          <p className="text-xs text-muted-foreground">{strings.adminBundles.followsProducts}</p>
        </div>
      ) : null}
      {savings != null ? (
        <p className="text-sm text-cyan-edge">{strings.adminBundles.saving(formatPrice(savings, ""))}</p>
      ) : null}
    </div>
  )
}

// Abre con cualquier area del panel (RequireArea en App.tsx); crear, cambiar y borrar piden COMBOS
export default function AdminBundlesPage() {
  const { user, can } = useAuth()
  const canEdit = can("COMBOS")
  const bundles = useBundles(!!user?.token)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [toDelete, setToDelete] = useState<Bundle | null>(null)
  const deleteBundle = useDeleteBundle()

  const handleDelete = async (bundle: Bundle) => {
    try {
      await deleteBundle.mutateAsync(bundle.id)
    } catch (err) {
      notifyError(err, strings.adminBundles.deleteError)
    }
  }

  const list = bundles.data ?? []

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.adminBundles.title}
          highlight={strings.adminBundles.highlight}
          subtitle={strings.adminBundles.subtitle}
          action={
            canEdit ? (
              <Button onClick={() => setIsCreateOpen(true)}>
                <Plus className="h-4 w-4 mr-2" />
                {strings.adminBundles.createButton}
              </Button>
            ) : undefined
          }
        />

        {bundles.isPending ? (
          <p className="text-muted-foreground">{strings.adminBundles.loading}</p>
        ) : list.length === 0 ? (
          <Card>
            <CardContent className="py-12 text-center">
              <Boxes className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
              <p className="text-muted-foreground">{strings.adminBundles.empty}</p>
            </CardContent>
          </Card>
        ) : (
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            {list.map((bundle) => (
              <BundleCard key={bundle.id} bundle={bundle} canEdit={canEdit} onDelete={() => setToDelete(bundle)} />
            ))}
          </div>
        )}
      </main>

      <CreateBundleDialog open={isCreateOpen} onOpenChange={setIsCreateOpen} />

      <AlertDialog open={toDelete !== null} onOpenChange={(open) => !open && setToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{strings.adminBundles.deleteTitle}</AlertDialogTitle>
            <AlertDialogDescription>
              {toDelete ? strings.adminBundles.deleteQuestion(toDelete.name) : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{strings.adminBundles.cancel}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (toDelete) handleDelete(toDelete)
                setToDelete(null)
              }}
            >
              {strings.adminBundles.deleteConfirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function BundleCard({ bundle, canEdit, onDelete }: { bundle: Bundle; canEdit: boolean; onDelete: () => void }) {
  const toggle = useSetBundleEnabled()
  const setPrice = useSetBundlePrice()
  const saved = pricingDraftOf(bundle)
  const [pricing, setPricing] = useState<PricingDraft>(saved)
  const [priceError, setPriceError] = useState<string | null>(null)

  // Al guardar (o si otro lo cambia) se vuelve a lo guardado
  useEffect(() => {
    setPricing(saved)
  }, [saved.type, saved.value])

  const savings =
    bundle.price != null && bundle.listPrice != null && bundle.listPrice > bundle.price
      ? bundle.listPrice - bundle.price
      : null
  const priceChanged = pricing.type !== saved.type || pricing.value !== saved.value

  const handleToggle = async (enabled: boolean) => {
    try {
      await toggle.mutateAsync({ id: bundle.id, enabled })
    } catch (err) {
      notifyError(err, strings.adminBundles.saveError)
    }
  }

  const handleSavePrice = async (event: React.FormEvent) => {
    event.preventDefault()
    const parsed = parsePricing(pricing)
    if (parsed === undefined) {
      setPriceError(invalidPricingMessage(pricing))
      return
    }
    setPriceError(null)
    try {
      await setPrice.mutateAsync({ id: bundle.id, ...parsed })
    } catch (err) {
      setPriceError(getErrorMessage(err, strings.adminBundles.saveError))
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0">
        <div className="flex min-w-0 items-center gap-3">
          <BundleThumbs products={bundle.products} />
          <div className="min-w-0 space-y-1">
            <CardTitle className="truncate">{bundle.name}</CardTitle>
            <CardDescription>
              {strings.adminBundles.productCount(bundle.products.length)} ·{" "}
              {strings.adminBundles.available(bundle.available)}
            </CardDescription>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {canEdit ? (
            <Switch
              checked={bundle.enabled}
              disabled={toggle.isPending}
              onCheckedChange={handleToggle}
              aria-label={strings.adminBundles.toggleAria(bundle.name)}
            />
          ) : null}
          <Badge variant={bundle.enabled ? "default" : "secondary"}>
            {bundle.enabled ? strings.adminBundles.enabled : strings.adminBundles.disabled}
          </Badge>
          {canEdit ? (
            <Button
              variant="ghost"
              size="icon"
              className="text-destructive hover:text-destructive"
              aria-label={strings.adminBundles.deleteAria}
              onClick={onDelete}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          ) : null}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEdit ? (
          <form onSubmit={handleSavePrice} className="space-y-1">
            <PricingFields
              id={`bundle-price-${bundle.id}`}
              draft={pricing}
              onChange={setPricing}
              listPrice={bundle.listPrice}
              action={
                <Button type="submit" variant="outline" disabled={!priceChanged || setPrice.isPending}>
                  {strings.adminBundles.savePrice}
                </Button>
              }
            />
            {priceError ? <p className="text-sm text-destructive">{priceError}</p> : null}
          </form>
        ) : (
          <div className="space-y-0.5 text-sm">
            <p>
              {strings.adminBundles.price}: {formatPrice(bundle.price, strings.price.ask)}
            </p>
            {bundle.pricingType !== "FIXED" && bundle.discount != null ? (
              <p className="text-xs text-muted-foreground">
                {strings.adminBundles.discountSummary(
                  bundle.pricingType,
                  bundle.pricingType === "PERCENT" ? String(bundle.discount) : formatPrice(bundle.discount, "")
                )}
              </p>
            ) : null}
          </div>
        )}
        {bundle.listPrice != null && bundle.products.length > 0 ? (
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 rounded-md bg-muted/50 px-3 py-2 text-sm">
            <span className="text-muted-foreground">
              {strings.adminBundles.listPrice}{" "}
              <span className="font-medium text-foreground tabular-nums">{formatPrice(bundle.listPrice, "")}</span>
            </span>
            {savings != null && !canEdit ? (
              <span className="text-cyan-edge">{strings.adminBundles.saving(formatPrice(savings, ""))}</span>
            ) : null}
          </div>
        ) : null}
        {bundle.price == null ? <p className="text-xs text-muted-foreground">{strings.adminBundles.noPriceHint}</p> : null}

        <div className="space-y-2 border-t border-border pt-4">
          <h4 className="text-sm font-medium">{strings.adminBundles.products}</h4>
          {bundle.products.length === 0 ? (
            <p className="text-sm text-muted-foreground">{strings.adminBundles.noProducts}</p>
          ) : (
            <ul className="space-y-2">
              {bundle.products.map((item) => (
                <BundleProductRow key={item.productid} bundleId={bundle.id} item={item} canEdit={canEdit} />
              ))}
            </ul>
          )}
          {canEdit ? <AddBundleProduct bundle={bundle} /> : null}
        </div>
      </CardContent>
    </Card>
  )
}

// La cantidad se guarda al salir del campo o con Enter; 0 o vacio no se acepta (para eso se quita)
function BundleProductRow({ bundleId, item, canEdit }: { bundleId: number; item: BundleProduct; canEdit: boolean }) {
  const setProduct = useSetBundleProduct()
  const removeProduct = useRemoveBundleProduct()
  const [quantity, setQuantity] = useState(String(item.quantity))

  useEffect(() => {
    setQuantity(String(item.quantity))
  }, [item.quantity])

  const save = async () => {
    const value = Number(quantity)
    if (!Number.isInteger(value) || value <= 0) {
      setQuantity(String(item.quantity))
      return
    }
    if (value === item.quantity) return
    try {
      await setProduct.mutateAsync({ id: bundleId, productId: item.productid, quantity: value })
    } catch (err) {
      setQuantity(String(item.quantity))
      notifyError(err, strings.adminBundles.saveError)
    }
  }

  const remove = async () => {
    try {
      await removeProduct.mutateAsync({ id: bundleId, productId: item.productid })
    } catch (err) {
      notifyError(err, strings.adminBundles.saveError)
    }
  }

  return (
    <li className="flex items-center gap-3 rounded-lg border border-border p-2">
      <ProductThumb image={item.image} />
      <div className="flex-1 min-w-0">
        <p className="truncate text-sm font-medium">{item.productName}</p>
        <p className="text-xs text-muted-foreground tabular-nums">
          {item.price != null
            ? `${strings.adminBundles.unitPrice(formatPrice(item.price, ""))} · ${formatPrice(item.price * item.quantity, "")}`
            : strings.price.ask}
        </p>
      </div>
      {canEdit ? (
        <>
          <Input
            type="number"
            min={1}
            step={1}
            className="no-spinner w-14 text-center"
            aria-label={`${strings.adminBundles.quantity}: ${item.productName}`}
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            onBlur={save}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault()
                save()
              }
            }}
          />
          <Button
            variant="ghost"
            size="icon"
            aria-label={`${strings.adminBundles.removeProduct}: ${item.productName}`}
            disabled={removeProduct.isPending}
            onClick={remove}
          >
            <X className="h-4 w-4" />
          </Button>
        </>
      ) : (
        <span className="text-sm text-muted-foreground tabular-nums">x{item.quantity}</span>
      )}
    </li>
  )
}

// Busca entre los productos del panel; elegir uno lo agrega con cantidad 1 (si ya estaba, se cambia en su fila)
function AddBundleProduct({ bundle }: { bundle: Bundle }) {
  const [search, setSearch] = useState("")
  const query = useDebouncedValue(search.trim())
  const results = useAdminProducts({ q: query, page: 0 }, query.length > 0)
  const setProduct = useSetBundleProduct()
  const inBundle = new Set(bundle.products.map((item) => String(item.productid)))
  const options = (results.data?.items ?? []).filter((product) => !inBundle.has(String(product.id))).slice(0, 5)

  const add = async (productId: string) => {
    try {
      await setProduct.mutateAsync({ id: bundle.id, productId: Number(productId), quantity: 1 })
      setSearch("")
    } catch (err) {
      notifyError(err, strings.adminBundles.saveError)
    }
  }

  return (
    <div className="space-y-2 pt-2">
      <Input
        value={search}
        placeholder={strings.adminBundles.searchProduct}
        aria-label={strings.adminBundles.addProduct}
        onChange={(event) => setSearch(event.target.value)}
      />
      {query.length > 0 && results.data ? (
        options.length === 0 ? (
          <p className="text-sm text-muted-foreground">{strings.adminBundles.noResults}</p>
        ) : (
          <ul className="rounded-md border border-border divide-y divide-border">
            {options.map((product) => (
              <li key={product.id}>
                <button
                  type="button"
                  className="flex w-full items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted/50 disabled:opacity-50"
                  disabled={setProduct.isPending}
                  onClick={() => add(product.id)}
                >
                  <ProductThumb image={product.images?.[0] ?? null} />
                  <span className="flex-1 min-w-0 truncate">{product.name}</span>
                  <span className="shrink-0 tabular-nums text-muted-foreground">
                    {formatPrice(product.price, strings.price.ask)}
                  </span>
                  <Plus className="h-4 w-4 shrink-0 text-muted-foreground" />
                </button>
              </li>
            ))}
          </ul>
        )
      ) : null}
    </div>
  )
}

// Producto elegido en el dialogo de creacion: guarda lo que hace falta para mostrarlo sin volver a buscarlo
interface DraftProduct {
  id: string
  name: string
  price: number | null
  image: string | null
  quantity: number
}

function ProductThumb({ image }: { image: string | null }) {
  return (
    <div className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-md bg-muted">
      {image ? (
        <img src={productImageUrl(image)} alt="" className="h-full w-full object-cover" />
      ) : (
        <ImageIcon className="h-4 w-4 text-muted-foreground" />
      )}
    </div>
  )
}

// Hasta 3 imagenes de los productos del combo, encimadas; sin imagenes, el icono de combos
function BundleThumbs({ products }: { products: BundleProduct[] }) {
  const images = products.map((product) => product.image).filter((image): image is string => !!image).slice(0, 3)
  if (images.length === 0) {
    return (
      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-muted">
        <Boxes className="h-5 w-5 text-muted-foreground" />
      </div>
    )
  }
  return (
    <div className="flex shrink-0 -space-x-3">
      {images.map((image, index) => (
        <div
          key={`${image}-${index}`}
          className="h-12 w-12 overflow-hidden rounded-lg border-2 border-card bg-muted"
          style={{ zIndex: images.length - index }}
        >
          <img src={productImageUrl(image)} alt="" className="h-full w-full object-cover" />
        </div>
      ))}
    </div>
  )
}

// Se crea en un solo paso: nombre, productos y por ultimo el precio (con la suma de los productos como referencia)
function CreateBundleDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const createBundle = useCreateBundle()
  const [name, setName] = useState("")
  const [products, setProducts] = useState<DraftProduct[]>([])
  const [search, setSearch] = useState("")
  const [pricing, setPricing] = useState<PricingDraft>(emptyPricing)
  const [enabled, setEnabled] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const query = useDebouncedValue(search.trim())
  const results = useAdminProducts({ q: query, page: 0 }, open && query.length > 0)
  const chosen = new Set(products.map((product) => product.id))
  const options = (results.data?.items ?? []).filter((product) => !chosen.has(String(product.id))).slice(0, 5)

  // Suma de precio x cantidad; los productos sin precio no suman (se avisa abajo)
  const total = products.reduce((sum, product) => sum + (product.price ?? 0) * product.quantity, 0)
  const missingPrice = products.some((product) => product.price == null)
  // Como en el backend: con algun producto sin precio no hay suma (un descuento deja el combo sin precio)
  const listPrice = products.length > 0 && !missingPrice ? total : null

  useEffect(() => {
    if (!open) return
    setName("")
    setProducts([])
    setSearch("")
    setPricing(emptyPricing)
    setEnabled(true)
    setError(null)
  }, [open])

  const addProduct = (product: Product) => {
    setProducts((current) => [
      ...current,
      {
        id: String(product.id),
        name: product.name,
        price: product.price,
        image: product.images?.[0] ?? null,
        quantity: 1,
      },
    ])
    setSearch("")
  }

  const setQuantity = (id: string, quantity: number) =>
    setProducts((current) => current.map((product) => (product.id === id ? { ...product, quantity } : product)))

  const removeProduct = (id: string) => setProducts((current) => current.filter((product) => product.id !== id))

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (name.trim() === "") {
      setError(strings.adminBundles.nameRequired)
      return
    }
    if (products.length === 0) {
      setError(strings.adminBundles.productsRequired)
      return
    }
    const parsed = parsePricing(pricing)
    if (parsed === undefined) {
      setError(invalidPricingMessage(pricing))
      return
    }
    setError(null)
    try {
      await createBundle.mutateAsync({
        name: name.trim(),
        ...parsed,
        enabled,
        products: products.map((product) => ({ productId: Number(product.id), quantity: product.quantity })),
      })
      onOpenChange(false)
    } catch (err) {
      setError(getErrorMessage(err, strings.adminBundles.saveError))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="grid-cols-1 sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit} className="min-w-0 space-y-5">
          <DialogHeader>
            <DialogTitle>{strings.adminBundles.createTitle}</DialogTitle>
            <DialogDescription>{strings.adminBundles.createDescription}</DialogDescription>
          </DialogHeader>

          <div className="grid grid-cols-1 gap-2">
            <label htmlFor="bundle-name" className="text-sm font-medium">
              {strings.adminBundles.nameLabel}
            </label>
            <Input id="bundle-name" value={name} maxLength={255} onChange={(event) => setName(event.target.value)} />
          </div>

          <div className="grid grid-cols-1 gap-2">
            <span className="text-sm font-medium">{strings.adminBundles.products}</span>
            {products.length === 0 ? (
              <p className="text-sm text-muted-foreground">{strings.adminBundles.noProductsYet}</p>
            ) : (
              <ul className="space-y-2">
                {products.map((product) => (
                  <li key={product.id} className="flex items-center gap-3 rounded-lg border border-border p-2">
                    <ProductThumb image={product.image} />
                    <div className="flex-1 min-w-0">
                      <p className="truncate text-sm font-medium">{product.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {product.price != null
                          ? strings.adminBundles.unitPrice(formatPrice(product.price, ""))
                          : strings.price.ask}
                      </p>
                    </div>
                    <Input
                      type="number"
                      min={1}
                      step={1}
                      className="no-spinner w-14 text-center"
                      aria-label={`${strings.adminBundles.quantity}: ${product.name}`}
                      value={product.quantity}
                      onChange={(event) => {
                        const value = Number(event.target.value)
                        if (Number.isInteger(value) && value >= 1) setQuantity(product.id, value)
                      }}
                    />
                    <span className="hidden w-24 text-right text-sm tabular-nums sm:block">
                      {product.price != null ? formatPrice(product.price * product.quantity, "") : "—"}
                    </span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label={`${strings.adminBundles.removeProduct}: ${product.name}`}
                      onClick={() => removeProduct(product.id)}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            <Input
              value={search}
              placeholder={strings.adminBundles.searchProduct}
              aria-label={strings.adminBundles.addProduct}
              onChange={(event) => setSearch(event.target.value)}
            />
            {query.length > 0 && results.data ? (
              options.length === 0 ? (
                <p className="text-sm text-muted-foreground">{strings.adminBundles.noResults}</p>
              ) : (
                <ul className="rounded-md border border-border divide-y divide-border">
                  {options.map((product) => (
                    <li key={product.id}>
                      <button
                        type="button"
                        className="flex w-full items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted/50"
                        onClick={() => addProduct(product)}
                      >
                        <ProductThumb image={product.images?.[0] ?? null} />
                        <span className="flex-1 min-w-0 truncate">{product.name}</span>
                        <span className="shrink-0 tabular-nums text-muted-foreground">
                          {formatPrice(product.price, strings.price.ask)}
                        </span>
                        <Plus className="h-4 w-4 shrink-0 text-muted-foreground" />
                      </button>
                    </li>
                  ))}
                </ul>
              )
            ) : null}
          </div>

          <div className="grid grid-cols-1 gap-2 border-t border-border pt-4">
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="text-muted-foreground">{strings.adminBundles.productsTotal}</span>
              <span className="font-medium tabular-nums">{formatPrice(total, "")}</span>
            </div>
            {missingPrice ? (
              <p className="text-xs text-muted-foreground">{strings.adminBundles.productsTotalPartial}</p>
            ) : null}

            <PricingFields
              id="bundle-new-price"
              draft={pricing}
              onChange={setPricing}
              listPrice={listPrice}
              action={
                pricing.type === "FIXED" ? (
                  <Button
                    type="button"
                    variant="outline"
                    disabled={total === 0}
                    onClick={() => setPricing({ type: "FIXED", value: String(total) })}
                  >
                    {strings.adminBundles.useTotal}
                  </Button>
                ) : null
              }
            />
            {pricing.type === "FIXED" && pricing.value.trim() === "" ? (
              <p className="text-xs text-muted-foreground">{strings.adminBundles.priceHint}</p>
            ) : null}
          </div>

          <label className="flex items-center gap-3 text-sm">
            <Switch checked={enabled} onCheckedChange={setEnabled} />
            {strings.adminBundles.enabled}
          </label>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {strings.adminBundles.cancel}
            </Button>
            <Button type="submit" disabled={createBundle.isPending}>
              {strings.adminBundles.create}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
