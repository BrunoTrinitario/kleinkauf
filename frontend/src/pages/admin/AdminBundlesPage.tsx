import { useEffect, useState } from "react"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
import { formatPrice } from "@/lib/utils"
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
import { Boxes, Plus, Trash2, X } from "lucide-react"

// "" = sin precio (el combo no aparece en el catalogo); undefined = no es un precio valido
const parsePrice = (value: string): number | null | undefined => {
  if (value.trim() === "") return null
  const price = Number(value)
  return Number.isFinite(price) && price >= 0 ? price : undefined
}

// Abre con cualquier area del panel (RequireArea en App.tsx); crear, cambiar y borrar piden COMBOS
export default function AdminBundlesPage() {
  const { user, activeRole, can } = useAuth()
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
          backTo={activeRole === "ADMINISTRADOR" ? "/admin" : "/"}
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
          <div className="grid gap-4 lg:grid-cols-2">
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
  const [priceInput, setPriceInput] = useState(bundle.price != null ? String(bundle.price) : "")
  const [priceError, setPriceError] = useState<string | null>(null)

  useEffect(() => {
    setPriceInput(bundle.price != null ? String(bundle.price) : "")
  }, [bundle.price])

  const savings =
    bundle.price != null && bundle.listPrice != null && bundle.listPrice > bundle.price
      ? bundle.listPrice - bundle.price
      : null
  const priceChanged = priceInput !== (bundle.price != null ? String(bundle.price) : "")

  const handleToggle = async (enabled: boolean) => {
    try {
      await toggle.mutateAsync({ id: bundle.id, enabled })
    } catch (err) {
      notifyError(err, strings.adminBundles.saveError)
    }
  }

  const handleSavePrice = async (event: React.FormEvent) => {
    event.preventDefault()
    const price = parsePrice(priceInput)
    if (price === undefined) {
      setPriceError(strings.adminBundles.invalidPrice)
      return
    }
    setPriceError(null)
    try {
      await setPrice.mutateAsync({ id: bundle.id, price })
    } catch (err) {
      setPriceError(getErrorMessage(err, strings.adminBundles.saveError))
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0">
        <div className="min-w-0 space-y-1">
          <CardTitle className="truncate">{bundle.name}</CardTitle>
          <CardDescription>
            {strings.adminBundles.available(bundle.available)}
            {bundle.listPrice != null && bundle.products.length > 0
              ? ` · ${strings.adminBundles.listPrice} ${formatPrice(bundle.listPrice, "")}`
              : null}
          </CardDescription>
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
            <label htmlFor={`bundle-price-${bundle.id}`} className="text-sm font-medium">
              {strings.adminBundles.price}
            </label>
            <div className="flex gap-2">
              <Input
                id={`bundle-price-${bundle.id}`}
                type="number"
                min={0}
                step="0.01"
                value={priceInput}
                placeholder={strings.adminProductEdit.pricePlaceholder}
                onChange={(event) => setPriceInput(event.target.value)}
              />
              <Button type="submit" variant="outline" disabled={!priceChanged || setPrice.isPending}>
                {strings.adminBundles.savePrice}
              </Button>
            </div>
            {priceError ? <p className="text-sm text-destructive">{priceError}</p> : null}
          </form>
        ) : (
          <p className="text-sm">
            {strings.adminBundles.price}: {formatPrice(bundle.price, strings.price.ask)}
          </p>
        )}
        {savings != null ? (
          <p className="text-sm text-cyan-edge">{strings.adminBundles.saving(formatPrice(savings, ""))}</p>
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
    <li className="flex items-center gap-2">
      <span className="flex-1 min-w-0 truncate text-sm">{item.productName}</span>
      {canEdit ? (
        <>
          <Input
            type="number"
            min={1}
            step={1}
            className="w-20"
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
                  className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-muted/50 disabled:opacity-50"
                  disabled={setProduct.isPending}
                  onClick={() => add(product.id)}
                >
                  <span className="truncate">{product.name}</span>
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

function CreateBundleDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const createBundle = useCreateBundle()
  const [name, setName] = useState("")
  const [price, setPrice] = useState("")
  const [enabled, setEnabled] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setName("")
    setPrice("")
    setEnabled(true)
    setError(null)
  }, [open])

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (name.trim() === "") {
      setError(strings.adminBundles.nameRequired)
      return
    }
    const parsed = parsePrice(price)
    if (parsed === undefined) {
      setError(strings.adminBundles.invalidPrice)
      return
    }
    setError(null)
    try {
      await createBundle.mutateAsync({ name: name.trim(), price: parsed, enabled })
      onOpenChange(false)
    } catch (err) {
      setError(getErrorMessage(err, strings.adminBundles.saveError))
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{strings.adminBundles.createTitle}</DialogTitle>
            <DialogDescription>{strings.adminBundles.createDescription}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <label htmlFor="bundle-name" className="text-sm font-medium">
              {strings.adminBundles.nameLabel}
            </label>
            <Input id="bundle-name" value={name} maxLength={255} onChange={(event) => setName(event.target.value)} />
          </div>
          <div className="grid gap-2">
            <label htmlFor="bundle-new-price" className="text-sm font-medium">
              {strings.adminBundles.price}
            </label>
            <Input
              id="bundle-new-price"
              type="number"
              min={0}
              step="0.01"
              value={price}
              placeholder={strings.adminProductEdit.pricePlaceholder}
              onChange={(event) => setPrice(event.target.value)}
            />
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
