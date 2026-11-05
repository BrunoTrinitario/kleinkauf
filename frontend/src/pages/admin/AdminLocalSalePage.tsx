import { useState, type FormEvent, type ReactNode } from "react"
import { useNavigate } from "react-router-dom"
import axios from "axios"
import { toast } from "sonner"
import { AlertTriangle, Plus, Trash2 } from "lucide-react"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { formatAmount, formatPrice } from "@/lib/utils"
import { PAYMENT_METHODS, type Address, type DeliveryType, type NewLocalSale, type OrderItemType, type PaymentMethod } from "@/lib/types"
import { useCreateLocalSale } from "@/api/sales"
import { useAdminProducts } from "@/api/products"
import { useBundles } from "@/api/bundles"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { SearchField } from "@/components/list-toolbar"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"

// Same limits and rules as sales (sales.local.* and OrderValidation)
const MAX_LINES = 100
const MAX_QUANTITY = 999
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const PHONE_PATTERN = /^[0-9 +()-]{6,30}$/
const REQUIRED_ADDRESS = ["street", "number", "city", "province", "postalCode"] as const

interface Line {
  type: OrderItemType
  id: number
  name: string
  // Product stock or bundles that can be put together, at the time it was added
  stock: number
  listPrice: number | null
  quantity: string
  price: string
}

const lineKey = (line: { type: OrderItemType; id: number }) => `${line.type}-${line.id}`
const parseAmount = (value: string) => (value.trim() === "" ? null : Number(value.replace(",", ".")))

// Counter sale already charged (/admin/orders/new, ADMINISTRADOR only). It is sold even if stock is short: products
// records an adjustment and the order is flagged for review
export default function AdminLocalSalePage() {
  const navigate = useNavigate()
  const create = useCreateLocalSale()
  const [lines, setLines] = useState<Line[]>([])
  const [discount, setDiscount] = useState("")
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("EFECTIVO")
  const [status, setStatus] = useState<"ENTREGADO" | "PAGADO">("ENTREGADO")
  const [buyer, setBuyer] = useState({ name: "", email: "", phone: "" })
  const [delivery, setDelivery] = useState<DeliveryType>("RETIRO")
  const [address, setAddress] = useState<Address>({ street: "", number: "", apartment: "", city: "", province: "", postalCode: "" })
  const [notes, setNotes] = useState("")
  const [error, setError] = useState<string | null>(null)

  const unitPrice = (line: Line) => parseAmount(line.price) ?? line.listPrice ?? 0
  const subtotal = lines.reduce((sum, line) => sum + unitPrice(line) * (Number(line.quantity) || 0), 0)
  const discountValue = parseAmount(discount) ?? 0
  const total = Math.max(0, subtotal - (Number.isFinite(discountValue) ? discountValue : 0))

  const addLine = (line: Omit<Line, "quantity" | "price">) => {
    if (lines.some((current) => lineKey(current) === lineKey(line))) return
    setLines((current) => [...current, { ...line, quantity: "1", price: line.listPrice === null ? "" : String(line.listPrice) }])
    setError(null)
  }

  const updateLine = (key: string, changes: Partial<Line>) =>
    setLines((current) => current.map((line) => (lineKey(line) === key ? { ...line, ...changes } : line)))

  const removeLine = (key: string) => setLines((current) => current.filter((line) => lineKey(line) !== key))

  const validate = (): string | null => {
    if (lines.length === 0) return strings.localSale.noItems
    if (lines.length > MAX_LINES) return strings.localSale.tooManyLines(MAX_LINES)
    for (const line of lines) {
      const quantity = Number(line.quantity)
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) return strings.localSale.invalidQuantity(MAX_QUANTITY)
      const price = parseAmount(line.price)
      if (price === null && line.listPrice === null) return strings.localSale.missingPrice
      if (price !== null && (!Number.isFinite(price) || price < 0)) return strings.localSale.invalidPrice
    }
    const discountAmount = parseAmount(discount)
    if (discountAmount !== null && (!Number.isFinite(discountAmount) || discountAmount < 0 || discountAmount > subtotal)) {
      return strings.localSale.invalidDiscount
    }
    const name = buyer.name.trim()
    const email = buyer.email.trim()
    const phone = buyer.phone.trim()
    if (name.length > 100 || (email && (email.length > 150 || !EMAIL_PATTERN.test(email))) || (phone && !PHONE_PATTERN.test(phone))) {
      return strings.localSale.invalidBuyer
    }
    if (delivery === "ENVIO" && REQUIRED_ADDRESS.some((field) => !address[field]?.trim())) return strings.localSale.invalidAddress
    return null
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validate()
    if (invalid) {
      setError(invalid)
      return
    }
    setError(null)
    const hasBuyer = !!(buyer.name.trim() || buyer.email.trim() || buyer.phone.trim())
    const sale: NewLocalSale = {
      items: lines.map((line) => {
        const price = parseAmount(line.price)
        // With no change from the list price null is sent: the backend uses the current price
        return {
          type: line.type,
          id: line.id,
          quantity: Number(line.quantity),
          price: price === null || price === line.listPrice ? null : price,
        }
      }),
      discount: parseAmount(discount),
      paymentMethod,
      status,
      buyer: hasBuyer
        ? { name: buyer.name.trim() || undefined, email: buyer.email.trim() || undefined, phone: buyer.phone.trim() || undefined }
        : null,
      deliveryType: delivery,
      address:
        delivery === "ENVIO"
          ? {
              street: address.street.trim(),
              number: address.number.trim(),
              apartment: address.apartment?.trim() || null,
              city: address.city.trim(),
              province: address.province.trim(),
              postalCode: address.postalCode.trim(),
            }
          : undefined,
      notes: notes.trim() || undefined,
    }

    try {
      const order = await create.mutateAsync(sale)
      toast.success(order.stockReview ? strings.localSale.createdWithShortage(order.number) : strings.localSale.created(order.number))
      navigate(`/admin/orders/${order.number}`, { replace: true })
    } catch (err) {
      setError(saleErrorMessage(err))
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        <PageHeader title={strings.localSale.title} highlight={strings.localSale.highlight} subtitle={strings.localSale.subtitle} backTo="/admin/orders" />

        <form onSubmit={handleSubmit} noValidate className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem] lg:items-start">
          <div className="space-y-6 min-w-0">
            <ItemSearch selected={new Set(lines.map(lineKey))} onAdd={addLine} />

            <Card>
              <CardHeader>
                <CardTitle>{strings.localSale.itemsTitle}</CardTitle>
              </CardHeader>
              <CardContent>
                {lines.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{strings.localSale.empty}</p>
                ) : (
                  <ul className="divide-y divide-border">
                    {lines.map((line) => {
                      const key = lineKey(line)
                      const quantity = Number(line.quantity) || 0
                      return (
                        <li key={key} className="space-y-2 py-3 first:pt-0 last:pb-0">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <p className="font-medium">
                                {line.name}
                                {line.type === "BUNDLE" ? <span className="text-muted-foreground"> ({strings.localSale.bundle})</span> : null}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {line.listPrice === null
                                  ? strings.localSale.priceRequired
                                  : strings.localSale.listPrice(formatAmount(line.listPrice))}
                              </p>
                            </div>
                            <Button type="button" variant="ghost" size="icon" className="h-8 w-8 shrink-0" onClick={() => removeLine(key)} aria-label={strings.localSale.remove(line.name)}>
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </div>
                          <div className="flex flex-wrap items-end gap-3">
                            <label className="grid gap-1 text-xs text-muted-foreground">
                              {strings.localSale.quantity}
                              <Input type="number" inputMode="numeric" min={1} max={MAX_QUANTITY} step={1} value={line.quantity} onChange={(event) => updateLine(key, { quantity: event.target.value })} className="h-9 w-24" />
                            </label>
                            <label className="grid gap-1 text-xs text-muted-foreground">
                              {strings.localSale.price}
                              <Input type="number" inputMode="decimal" min={0} step="0.01" value={line.price} onChange={(event) => updateLine(key, { price: event.target.value })} className="h-9 w-36" aria-invalid={line.listPrice === null && !line.price.trim()} />
                            </label>
                            <span className="ml-auto pb-2 text-sm font-medium tabular-nums">{formatAmount(unitPrice(line) * quantity)}</span>
                          </div>
                          {quantity > line.stock ? (
                            <p className="flex items-start gap-2 text-xs text-destructive">
                              <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                              {strings.localSale.noStock(Math.max(0, line.stock))}
                            </p>
                          ) : null}
                        </li>
                      )
                    })}
                  </ul>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{strings.localSale.buyerTitle}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-muted-foreground">{strings.localSale.buyerHelp}</p>
                <div className="grid gap-4 sm:grid-cols-3">
                  <Field label={strings.localSale.name}>
                    <Input value={buyer.name} maxLength={100} onChange={(event) => setBuyer({ ...buyer, name: event.target.value })} />
                  </Field>
                  <Field label={strings.localSale.email}>
                    <Input type="email" value={buyer.email} maxLength={150} onChange={(event) => setBuyer({ ...buyer, email: event.target.value })} />
                  </Field>
                  <Field label={strings.localSale.phone}>
                    <Input type="tel" value={buyer.phone} maxLength={30} onChange={(event) => setBuyer({ ...buyer, phone: event.target.value })} />
                  </Field>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{strings.localSale.deliveryTitle}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <ChoiceGroup
                  label={strings.localSale.deliveryTitle}
                  value={delivery}
                  options={(["RETIRO", "ENVIO"] as DeliveryType[]).map((value) => ({ value, label: strings.checkout[value] }))}
                  onChange={(value) => setDelivery(value as DeliveryType)}
                />
                {delivery === "ENVIO" ? (
                  <div className="grid gap-4 sm:grid-cols-2">
                    {(["street", "number", "apartment", "city", "province", "postalCode"] as const).map((field) => (
                      <Field key={field} label={strings.localSale[field]}>
                        <Input value={address[field] ?? ""} maxLength={100} onChange={(event) => setAddress({ ...address, [field]: event.target.value })} />
                      </Field>
                    ))}
                  </div>
                ) : null}
                <Field label={strings.localSale.notes}>
                  <Textarea value={notes} maxLength={500} rows={2} onChange={(event) => setNotes(event.target.value)} />
                </Field>
              </CardContent>
            </Card>
          </div>

          <Card className="lg:sticky lg:top-20">
            <CardHeader>
              <CardTitle>{strings.localSale.paymentTitle}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <ChoiceGroup
                label={strings.localSale.paymentMethod}
                value={paymentMethod}
                options={PAYMENT_METHODS.map((value) => ({ value, label: strings.admin.sales.payments[value] }))}
                onChange={(value) => setPaymentMethod(value as PaymentMethod)}
              />
              <ChoiceGroup
                label={strings.localSale.statusLabel}
                value={status}
                options={(["ENTREGADO", "PAGADO"] as const).map((value) => ({ value, label: strings.localSale.statuses[value] }))}
                onChange={(value) => setStatus(value as "ENTREGADO" | "PAGADO")}
                columns={1}
              />
              <Field label={strings.localSale.discount}>
                <Input type="number" inputMode="decimal" min={0} step="0.01" value={discount} placeholder="0" onChange={(event) => setDiscount(event.target.value)} />
              </Field>

              <dl className="space-y-1 border-t border-border pt-4 text-sm">
                <div className="flex justify-between text-muted-foreground">
                  <dt>{strings.localSale.subtotal}</dt>
                  <dd className="tabular-nums">{formatAmount(subtotal)}</dd>
                </div>
                {discountValue > 0 ? (
                  <div className="flex justify-between text-muted-foreground">
                    <dt>{strings.adminOrder.discount}</dt>
                    <dd className="tabular-nums">-{formatAmount(discountValue)}</dd>
                  </div>
                ) : null}
                <div className="flex items-baseline justify-between">
                  <dt className="text-muted-foreground">{strings.localSale.total}</dt>
                  <dd className="text-xl font-medium tabular-nums">{formatAmount(total)}</dd>
                </div>
              </dl>

              {error ? (
                <p className="text-sm text-destructive" role="alert">
                  {error}
                </p>
              ) : null}
              <Button type="submit" className="w-full" disabled={create.isPending}>
                {create.isPending ? strings.localSale.submitting : strings.localSale.submit}
              </Button>
            </CardContent>
          </Card>
        </form>
      </main>
    </div>
  )
}

function saleErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const text = typeof error.response?.data === "string" ? error.response.data : ""
    if (status === 400 && /have no price/i.test(text)) {
      return strings.localSale.priceRequiredError(text.slice(text.indexOf(":") + 1).trim())
    }
    if (status === 400 && /discount/i.test(text)) return strings.localSale.invalidDiscount
    if (status === 503) return strings.localSale.unavailable
    // 409 from products: does not exist, disabled bundle... the message carries the name
    if (status === 409 && text) return text
  }
  return getErrorMessage(error, strings.localSale.error)
}

// Searches products (on the server) and bundles (the full list, filtered here) to add them to the sale
function ItemSearch({ selected, onAdd }: { selected: Set<string>; onAdd: (line: Omit<Line, "quantity" | "price">) => void }) {
  const [input, setInput] = useState("")
  const query = useDebouncedValue(input.trim())
  const products = useAdminProducts({ q: query, page: 0 }, query.length > 0)
  const bundles = useBundles(true)

  const normalized = query.toLowerCase()
  const bundleResults = query
    ? (bundles.data ?? []).filter((bundle) => bundle.name.toLowerCase().includes(normalized)).slice(0, 5)
    : []
  const productResults = query ? (products.data?.items ?? []).slice(0, 8) : []

  const results = [
    ...productResults.map((product) => ({
      type: "PRODUCT" as const,
      id: Number(product.id),
      name: product.name,
      stock: product.stock,
      listPrice: product.price,
      detail: strings.localSale.stock(product.stock),
      disabled: false,
    })),
    ...bundleResults.map((bundle) => ({
      type: "BUNDLE" as const,
      id: bundle.id,
      name: bundle.name,
      stock: bundle.available,
      listPrice: bundle.price,
      detail: bundle.enabled ? strings.localSale.available(bundle.available) : strings.localSale.disabledBundle,
      disabled: !bundle.enabled,
    })),
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{strings.localSale.searchLabel}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <SearchField value={input} onChange={setInput} placeholder={strings.localSale.search} />
        {!query ? null : products.isFetching && results.length === 0 ? (
          <p className="text-sm text-muted-foreground">{strings.localSale.searching}</p>
        ) : results.length === 0 ? (
          <p className="text-sm text-muted-foreground">{strings.localSale.noResults}</p>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {results.map((result) => {
              const added = selected.has(lineKey(result))
              return (
                <li key={lineKey(result)} className="flex items-center justify-between gap-3 px-3 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">
                      {result.name}
                      {result.type === "BUNDLE" ? <span className="text-muted-foreground"> ({strings.localSale.bundle})</span> : null}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {formatPrice(result.listPrice, strings.localSale.noPrice)} · {result.detail}
                    </p>
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant={added ? "ghost" : "outline"}
                    disabled={added || result.disabled}
                    onClick={() => onAdd(result)}
                  >
                    {added ? (
                      strings.localSale.added
                    ) : (
                      <>
                        <Plus className="mr-1 h-4 w-4" />
                        {strings.localSale.add}
                      </>
                    )}
                  </Button>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-2 text-sm font-medium">
      {label}
      {children}
    </label>
  )
}

function ChoiceGroup({
  label,
  value,
  options,
  onChange,
  columns = 2,
}: {
  label: string
  value: string
  options: { value: string; label: string }[]
  onChange: (value: string) => void
  columns?: 1 | 2
}) {
  return (
    <div className="space-y-2">
      <span className="text-sm font-medium">{label}</span>
      <div className={`grid gap-2 ${columns === 2 ? "grid-cols-2" : "grid-cols-1"}`} role="radiogroup" aria-label={label}>
        {options.map((option) => (
          <Button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            variant={value === option.value ? "default" : "outline"}
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </Button>
        ))}
      </div>
    </div>
  )
}
