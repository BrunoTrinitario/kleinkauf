import { useEffect, useState, type FormEvent, type ReactNode } from "react"
import { Link, Navigate, useNavigate } from "react-router-dom"
import axios from "axios"
import { strings } from "@/lib/strings"
import { useAuth } from "@/lib/auth-context"
import { useCart } from "@/lib/cart"
import { rememberOrder } from "@/lib/known-orders"
import { getErrorMessage } from "@/lib/api-error"
import { formatAmount } from "@/lib/utils"
import { isValidEmail } from "@/lib/validation"
import type { Address, DeliveryType, NewOrder, PriceChange } from "@/lib/types"
import { priceChangesOf, useCheckoutInfo, useCreateOrder } from "@/api/sales"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Textarea } from "@/components/ui/textarea"

// Same rules as sales (services/OrderValidation.java); if they change, change both
const NAME_MAX = 100
const EMAIL_MAX = 150
const FIELD_MAX = 100
const NOTES_MAX = 500
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const PHONE_PATTERN = /^[0-9 +()-]{6,30}$/

const emptyAddress: Address = { street: "", number: "", apartment: "", city: "", province: "", postalCode: "" }

const REQUIRED_ADDRESS_FIELDS = ["street", "number", "city", "province", "postalCode"] as const

function validate(form: { name: string; email: string; phone: string; notes: string }, delivery: DeliveryType, address: Address) {
  const name = form.name.trim()
  if (name.length === 0 || name.length > NAME_MAX) return strings.checkout.nameInvalid
  const email = form.email.trim()
  if (email.length > EMAIL_MAX || !EMAIL_PATTERN.test(email) || !isValidEmail(email)) return strings.checkout.emailInvalid
  if (!PHONE_PATTERN.test(form.phone.trim())) return strings.checkout.phoneInvalid
  if (delivery === "ENVIO") {
    const incomplete = REQUIRED_ADDRESS_FIELDS.some((field) => {
      const value = address[field]?.trim() ?? ""
      return value.length === 0 || value.length > FIELD_MAX
    })
    if (incomplete || (address.apartment?.trim().length ?? 0) > FIELD_MAX) return strings.checkout.addressInvalid
  }
  if (form.notes.trim().length > NOTES_MAX) return strings.checkout.notesInvalid
  return null
}

// The 409 with text comes from products (or from sales if the store has no payment details): known ones are translated
function conflictMessage(error: unknown): string | null {
  if (!axios.isAxiosError(error) || error.response?.status !== 409) return null
  const data = error.response.data
  const text = typeof data === "string" ? data : typeof data?.message === "string" ? data.message : ""
  const afterColon = text.includes(":") ? text.slice(text.indexOf(":") + 1).trim() : ""
  if (/not enough stock/i.test(text)) return strings.checkout.notEnoughStock(afterColon)
  if (/without price/i.test(text)) return strings.checkout.withoutPrice(afterColon)
  if (/not available/i.test(text)) return strings.checkout.notAvailable
  if (/payment details/i.test(text)) return strings.checkout.notConfigured
  return null
}

// Checkout (/checkout): buyer and delivery details. Guest or with a session (with a session it shows in "my orders")
export default function CheckoutPage() {
  const { user, isAuthenticated, activeRole } = useAuth()
  const { items, total, clear, applyPriceChanges } = useCart()
  const navigate = useNavigate()
  const checkout = useCheckoutInfo()
  const createOrder = useCreateOrder()

  const [form, setForm] = useState({ name: "", email: "", phone: "", notes: "" })
  const [delivery, setDelivery] = useState<DeliveryType>("RETIRO")
  const [address, setAddress] = useState<Address>(emptyAddress)
  const [error, setError] = useState<string | null>(null)
  const [priceChanges, setPriceChanges] = useState<PriceChange[]>([])
  const [placed, setPlaced] = useState(false)

  // With a session the name and email are filled in (they can be changed: the order stores whatever is sent)
  useEffect(() => {
    if (user) {
      setForm((prev) => ({ ...prev, name: prev.name || user.name, email: prev.email || user.email }))
    }
  }, [user])

  // When the cart is emptied after placing the order it does not go back to the cart: it navigates to the order screen
  if (items.length === 0 && !placed) {
    return <Navigate to="/carrito" replace />
  }

  const info = checkout.data
  const deliveryTypes = info?.deliveryTypes ?? ["RETIRO", "ENVIO"]

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setPriceChanges([])

    const invalid = validate(form, delivery, address)
    if (invalid) {
      setError(invalid)
      return
    }

    const order: NewOrder = {
      items: items.map((item) => ({ type: item.type, id: item.id, quantity: item.quantity, price: item.price })),
      buyer: { name: form.name.trim(), email: form.email.trim(), phone: form.phone.trim() },
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
      notes: form.notes.trim() || undefined,
    }

    try {
      const created = await createOrder.mutateAsync(order)
      rememberOrder(created.number, order.buyer.email)
      setPlaced(true)
      clear()
      navigate(`/pedido/${created.number}`, { replace: true, state: { email: order.buyer.email, justCreated: true } })
    } catch (err) {
      const changes = priceChangesOf(err)
      if (changes) {
        // The cart is updated with the new prices and confirmation is asked again
        applyPriceChanges(changes)
        setPriceChanges(changes)
        setError(strings.checkout.pricesChanged)
        return
      }
      setError(conflictMessage(err) ?? getErrorMessage(err, strings.checkout.error))
    }
  }

  const updateAddress = (field: keyof Address, value: string) => setAddress((prev) => ({ ...prev, [field]: value }))

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <PageHeader
          title={strings.checkout.title}
          highlight={strings.checkout.highlight}
          subtitle={strings.checkout.subtitle}
          backTo="/carrito"
        />

        {checkout.isPending ? (
          <p className="text-muted-foreground">{strings.checkout.loading}</p>
        ) : checkout.isError ? (
          <p className="text-destructive">{getErrorMessage(checkout.error, strings.checkout.loadError)}</p>
        ) : !info?.paymentConfigured ? (
          <Card className="max-w-2xl">
            <CardContent className="space-y-4">
              <p className="text-muted-foreground">{strings.checkout.notConfigured}</p>
              {/* The administrator sees how to fix it: the payment details are entered in the sales dashboard */}
              {activeRole === "ADMINISTRADOR" ? (
                <div className="space-y-3 border-t border-border pt-4">
                  <p className="text-sm">{strings.checkout.notConfiguredAdmin}</p>
                  <Button asChild>
                    <Link to="/admin/orders" state={{ openSettings: true }}>
                      {strings.adminOrders.paymentMissingAction}
                    </Link>
                  </Button>
                </div>
              ) : null}
            </CardContent>
          </Card>
        ) : (
          <form onSubmit={handleSubmit} noValidate className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
            <div className="space-y-6 min-w-0">
              <Card>
                <CardHeader>
                  <CardTitle>{strings.checkout.buyerTitle}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <Field id="name" label={strings.checkout.name}>
                    <Input
                      id="name"
                      autoComplete="name"
                      maxLength={NAME_MAX}
                      value={form.name}
                      onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
                    />
                  </Field>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field id="email" label={strings.checkout.email}>
                      <Input
                        id="email"
                        type="email"
                        autoComplete="email"
                        maxLength={EMAIL_MAX}
                        value={form.email}
                        onChange={(event) => setForm((prev) => ({ ...prev, email: event.target.value }))}
                      />
                    </Field>
                    <Field id="phone" label={strings.checkout.phone}>
                      <Input
                        id="phone"
                        type="tel"
                        autoComplete="tel"
                        maxLength={30}
                        value={form.phone}
                        onChange={(event) => setForm((prev) => ({ ...prev, phone: event.target.value }))}
                      />
                    </Field>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {isAuthenticated ? (
                      strings.checkout.accountNote
                    ) : (
                      <>
                        {strings.checkout.guestNote}{" "}
                        <Link to="/login?next=/checkout" className="underline underline-offset-4 hover:text-foreground">
                          {strings.checkout.signInNote}
                        </Link>
                      </>
                    )}
                  </p>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>{strings.checkout.deliveryTitle}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <RadioGroup value={delivery} onValueChange={(value) => setDelivery(value as DeliveryType)}>
                    {deliveryTypes.map((type) => (
                      <div key={type} className="flex items-center gap-2">
                        <RadioGroupItem value={type} id={`delivery-${type}`} />
                        <Label htmlFor={`delivery-${type}`} className="font-normal">
                          {strings.checkout[type]}
                        </Label>
                      </div>
                    ))}
                  </RadioGroup>

                  {delivery === "ENVIO" ? (
                    <div className="space-y-4">
                      <p className="text-xs text-muted-foreground">{strings.checkout.shippingNote}</p>
                      <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_8rem]">
                        <Field id="street" label={strings.checkout.street}>
                          <Input id="street" autoComplete="address-line1" maxLength={FIELD_MAX} value={address.street} onChange={(event) => updateAddress("street", event.target.value)} />
                        </Field>
                        <Field id="number" label={strings.checkout.number}>
                          <Input id="number" maxLength={FIELD_MAX} value={address.number} onChange={(event) => updateAddress("number", event.target.value)} />
                        </Field>
                      </div>
                      <Field id="apartment" label={strings.checkout.apartment}>
                        <Input id="apartment" autoComplete="address-line2" maxLength={FIELD_MAX} value={address.apartment ?? ""} onChange={(event) => updateAddress("apartment", event.target.value)} />
                      </Field>
                      <div className="grid gap-4 sm:grid-cols-3">
                        <Field id="city" label={strings.checkout.city}>
                          <Input id="city" autoComplete="address-level2" maxLength={FIELD_MAX} value={address.city} onChange={(event) => updateAddress("city", event.target.value)} />
                        </Field>
                        <Field id="province" label={strings.checkout.province}>
                          <Input id="province" autoComplete="address-level1" maxLength={FIELD_MAX} value={address.province} onChange={(event) => updateAddress("province", event.target.value)} />
                        </Field>
                        <Field id="postalCode" label={strings.checkout.postalCode}>
                          <Input id="postalCode" autoComplete="postal-code" maxLength={FIELD_MAX} value={address.postalCode} onChange={(event) => updateAddress("postalCode", event.target.value)} />
                        </Field>
                      </div>
                    </div>
                  ) : null}

                  <Field id="notes" label={strings.checkout.notes}>
                    <Textarea
                      id="notes"
                      maxLength={NOTES_MAX}
                      value={form.notes}
                      onChange={(event) => setForm((prev) => ({ ...prev, notes: event.target.value }))}
                    />
                  </Field>
                </CardContent>
              </Card>
            </div>

            <Card className="lg:sticky lg:top-24">
              <CardHeader className="flex flex-row items-center justify-between">
                <CardTitle>{strings.checkout.summaryTitle}</CardTitle>
                <Link to="/carrito" className="text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
                  {strings.checkout.editCart}
                </Link>
              </CardHeader>
              <CardContent className="space-y-4">
                <ul className="space-y-2 text-sm">
                  {items.map((item) => (
                    <li key={`${item.type}-${item.id}`} className="flex justify-between gap-4">
                      <span className="min-w-0">
                        {item.quantity} x {item.name}
                      </span>
                      <span className="tabular-nums">{formatAmount(item.price * item.quantity)}</span>
                    </li>
                  ))}
                </ul>
                <div className="flex items-baseline justify-between border-t border-border pt-4">
                  <span className="text-muted-foreground">{strings.cart.total}</span>
                  <span className="text-lg font-medium">{formatAmount(total)}</span>
                </div>
                <p className="text-xs text-muted-foreground">{strings.checkout.paymentNote(info.expirationHours)}</p>

                {error ? (
                  <div className="space-y-1 text-sm text-destructive" role="alert">
                    <p>{error}</p>
                    {priceChanges.length > 0 ? (
                      <ul className="list-disc pl-5">
                        {priceChanges.map((change) => (
                          <li key={`${change.type}-${change.id}`}>
                            {change.currentPrice == null
                              ? strings.checkout.noLongerAvailable(change.name)
                              : strings.checkout.priceChange(
                                  change.name,
                                  formatAmount(change.expectedPrice),
                                  formatAmount(change.currentPrice)
                                )}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </div>
                ) : null}

                <Button type="submit" className="w-full" disabled={createOrder.isPending}>
                  {createOrder.isPending ? strings.checkout.submitting : strings.checkout.submit}
                </Button>
              </CardContent>
            </Card>
          </form>
        )}
      </main>
      <Footer />
    </div>
  )
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  )
}
