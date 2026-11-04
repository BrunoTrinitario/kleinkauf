import { useEffect, useRef, useState, type ChangeEvent } from "react"
import { Link, useLocation, useParams } from "react-router-dom"
import axios from "axios"
import { toast } from "sonner"
import { CheckCircle2, Copy } from "lucide-react"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { knownOrderEmail, normalizeOrderNumber, rememberOrder } from "@/lib/known-orders"
import { formatAmount, formatServerDateTime, parseServerDateTime } from "@/lib/utils"
import type { Order, OrderStatus, PaymentDetails } from "@/lib/types"
import { useCancelOrder, useOrderLookup, useUploadReceipt } from "@/api/sales"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { OrderLookupForm } from "@/components/order-lookup-form"
import { OrderStatusBadge } from "@/components/order-status-badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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

// Mismos topes que sales (sales.receipts.max-size y los tipos que acepta)
const RECEIPT_MAX_BYTES = 5 * 1024 * 1024
const RECEIPT_TYPES = ["image/jpeg", "image/png", "application/pdf"]

// El comprador puede cancelar mientras no se valido el pago
const CANCELLABLE: OrderStatus[] = ["PENDIENTE_PAGO", "PAGO_EN_REVISION", "RECHAZADO"]

interface OrderLocationState {
  email?: string
  justCreated?: boolean
}

// Pantalla del pedido (/pedido/:number). El email sale del estado de la navegacion (checkout, consulta o "mis pedidos")
// o de los pedidos guardados en este navegador; si no esta, se pide
export default function OrderPage() {
  const { number: rawNumber = "" } = useParams()
  const number = normalizeOrderNumber(rawNumber)
  const location = useLocation()
  const state = (location.state ?? {}) as OrderLocationState
  const email = state.email ?? knownOrderEmail(number)

  const lookup = useOrderLookup(number, email)
  const notFound = axios.isAxiosError(lookup.error) && lookup.error.response?.status === 404

  // Consultado con exito: se recuerda para volver a abrirlo sin el email
  useEffect(() => {
    if (lookup.data && email) rememberOrder(lookup.data.number, email)
  }, [lookup.data, email])

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        {!email || notFound ? (
          <div className="max-w-md space-y-6">
            <h1 className="text-heading-sm">{strings.orderLookup.title}</h1>
            <Card>
              <CardContent>
                <OrderLookupForm
                  initialNumber={number}
                  initialEmail={email ?? ""}
                  message={notFound ? strings.order.notFound : null}
                />
              </CardContent>
            </Card>
          </div>
        ) : lookup.data ? (
          <OrderDetail order={lookup.data} email={email} justCreated={!!state.justCreated} />
        ) : lookup.isError ? (
          <p className="text-destructive">{getErrorMessage(lookup.error, strings.order.loadError)}</p>
        ) : (
          <p className="text-muted-foreground">{strings.order.loading}</p>
        )}
      </main>
      <Footer />
    </div>
  )
}

function OrderDetail({ order, email, justCreated }: { order: Order; email: string; justCreated: boolean }) {
  const lastReason = (status: OrderStatus) =>
    [...order.history].reverse().find((entry) => entry.status === status)?.reason ?? null

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
      <div className="space-y-6 min-w-0">
        {justCreated && order.status === "PENDIENTE_PAGO" ? (
          <div className="flex gap-3 rounded-lg border border-border bg-card p-4" role="status">
            <CheckCircle2 className="h-5 w-5 shrink-0 text-primary" />
            <div>
              <p className="font-medium">{strings.order.createdTitle}</p>
              <p className="text-sm text-muted-foreground">{strings.order.createdText}</p>
            </div>
          </div>
        ) : null}

        <div className="space-y-2">
          <p className="text-xs uppercase tracking-wider text-muted-foreground">{strings.order.numberLabel}</p>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-heading-sm font-mono tracking-normal">{order.number}</h1>
            <OrderStatusBadge status={order.status} />
          </div>
          <p className="text-muted-foreground">{strings.order.statusHelp[order.status]}</p>
          {order.status === "RECHAZADO" && lastReason("RECHAZADO") ? (
            <p className="text-sm text-destructive">{strings.order.rejectedReason(lastReason("RECHAZADO")!)}</p>
          ) : null}
          {order.status === "CANCELADO" && lastReason("CANCELADO") ? (
            <p className="text-sm text-muted-foreground">{strings.order.cancelledReason(lastReason("CANCELADO")!)}</p>
          ) : null}
        </div>

        {order.payment ? <PaymentCard order={order} payment={order.payment} email={email} /> : null}

        <Card>
          <CardHeader>
            <CardTitle>{strings.order.itemsTitle}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <ul className="space-y-2 text-sm">
              {order.items.map((item) => (
                <li key={`${item.type}-${item.id}`} className="flex justify-between gap-4">
                  <span className="min-w-0">
                    {item.quantity} x {item.name}
                    {item.type === "BUNDLE" ? <span className="text-muted-foreground"> ({strings.cart.bundle})</span> : null}
                  </span>
                  <span className="tabular-nums">{formatAmount(item.subtotal)}</span>
                </li>
              ))}
            </ul>
            {order.discount > 0 ? (
              <div className="flex justify-between text-sm text-muted-foreground">
                <span>{strings.order.discount}</span>
                <span className="tabular-nums">-{formatAmount(order.discount)}</span>
              </div>
            ) : null}
            <div className="flex items-baseline justify-between border-t border-border pt-4">
              <span className="text-muted-foreground">{strings.cart.total}</span>
              <span className="text-lg font-medium">{formatAmount(order.total)}</span>
            </div>
          </CardContent>
        </Card>

        {CANCELLABLE.includes(order.status) ? <CancelOrder number={order.number} email={email} /> : null}
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>{strings.order.deliveryTitle}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <p>{strings.checkout[order.deliveryType]}</p>
            {order.address ? (
              <p className="text-muted-foreground">
                {order.address.street} {order.address.number}
                {order.address.apartment ? `, ${order.address.apartment}` : ""}
                <br />
                {order.address.city}, {order.address.province} ({order.address.postalCode})
              </p>
            ) : null}
            {order.notes ? (
              <p className="pt-2 text-muted-foreground">
                {strings.order.notes}: {order.notes}
              </p>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{strings.order.buyerTitle}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <p>{order.buyer.name}</p>
            <p className="text-muted-foreground">{order.buyer.email}</p>
            <p className="text-muted-foreground">{order.buyer.phone}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{strings.order.historyTitle}</CardTitle>
          </CardHeader>
          <CardContent>
            <ol className="space-y-3 text-sm">
              {order.history.map((entry, index) => (
                <li key={index} className="flex flex-col">
                  <span>{strings.order.status[entry.status]}</span>
                  <span className="text-xs text-muted-foreground">{formatServerDateTime(entry.createdAt)}</span>
                  {entry.reason ? <span className="text-xs text-muted-foreground">{entry.reason}</span> : null}
                </li>
              ))}
            </ol>
          </CardContent>
        </Card>

        <Link to="/pedido" className="inline-block text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
          {strings.order.otherOrder}
        </Link>
      </div>
    </div>
  )
}

// Cuenta regresiva hasta el vencimiento; se actualiza cada segundo mientras se muestra
function useRemaining(expiresAt: string | null) {
  const target = expiresAt ? parseServerDateTime(expiresAt).getTime() : null
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (target === null) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [target])
  return target === null ? null : Math.max(0, target - now)
}

function formatRemaining(ms: number) {
  const totalSeconds = Math.floor(ms / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const pad = (value: number) => String(value).padStart(2, "0")
  return hours > 0 ? `${hours} h ${pad(minutes)} min` : `${pad(minutes)}:${pad(seconds)}`
}

function PaymentCard({ order, payment, email }: { order: Order; payment: PaymentDetails; email: string }) {
  const remaining = useRemaining(order.expiresAt)
  const expired = remaining === 0

  const rows: [string, string | null][] = [
    [strings.order.alias, payment.alias],
    [strings.order.cbu, payment.cbu],
    [strings.order.holder, payment.holder],
    [strings.order.bank, payment.bank],
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{strings.order.payTitle}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <span className="text-muted-foreground">{strings.order.total}</span>
          <span className="text-2xl font-medium tabular-nums">{formatAmount(order.total)}</span>
        </div>

        <dl className="grid gap-3 text-sm">
          {rows
            .filter((row): row is [string, string] => !!row[1])
            .map(([label, value]) => (
              <div key={label} className="flex items-center justify-between gap-4">
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="flex min-w-0 items-center gap-1">
                  <span className="break-all font-medium">{value}</span>
                  {label === strings.order.alias || label === strings.order.cbu ? <CopyButton label={label} value={value} /> : null}
                </dd>
              </div>
            ))}
        </dl>

        {remaining !== null && order.expiresAt ? (
          <p className={`text-sm ${expired ? "text-destructive" : "text-muted-foreground"}`} aria-live="off">
            {expired ? (
              strings.order.expired
            ) : (
              <>
                <span className="font-medium text-foreground">{strings.order.expiresIn(formatRemaining(remaining))}</span>{" "}
                ({strings.order.expiresAt(formatServerDateTime(order.expiresAt))})
              </>
            )}
          </p>
        ) : null}

        {!expired ? <ReceiptUpload order={order} email={email} /> : null}
      </CardContent>
    </Card>
  )
}

function CopyButton({ label, value }: { label: string; value: string }) {
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value)
      toast.success(strings.order.copied, { id: `copy-${label}` })
    } catch {
      toast.error(strings.order.copyError)
    }
  }
  return (
    <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={handleCopy} aria-label={strings.order.copy(label)}>
      <Copy className="h-3.5 w-3.5" />
    </Button>
  )
}

function receiptErrorMessage(error: unknown) {
  if (axios.isAxiosError(error) && error.response?.status === 409) {
    const text = String(typeof error.response.data === "string" ? error.response.data : error.response.data?.message ?? "")
    if (/maximum/i.test(text)) return strings.order.receiptMax
    if (/someone else/i.test(text)) return strings.order.changedByOther
    return strings.order.receiptClosed
  }
  return getErrorMessage(error, strings.order.receiptError, { badRequest: strings.order.receiptInvalid })
}

function ReceiptUpload({ order, email }: { order: Order; email: string }) {
  const upload = useUploadReceipt(order.number, email)
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0] ?? null
    setError(null)
    if (selected && (selected.size > RECEIPT_MAX_BYTES || (selected.type && !RECEIPT_TYPES.includes(selected.type)))) {
      setError(strings.order.receiptInvalid)
      setFile(null)
      event.target.value = ""
      return
    }
    setFile(selected)
  }

  const handleUpload = async () => {
    if (!file) return
    setError(null)
    try {
      await upload.mutateAsync(file)
      toast.success(strings.order.receiptUploaded)
      setFile(null)
      if (inputRef.current) inputRef.current.value = ""
    } catch (err) {
      setError(receiptErrorMessage(err))
    }
  }

  return (
    <div className="space-y-3 border-t border-border pt-6">
      <p className="font-medium">{strings.order.receiptTitle}</p>
      <p className="text-sm text-muted-foreground">{strings.order.receiptHelp}</p>
      {order.receipts > 0 ? <p className="text-sm text-muted-foreground">{strings.order.receiptCount(order.receipts)}</p> : null}
      {error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          ref={inputRef}
          type="file"
          accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
          onChange={handleChange}
          aria-label={strings.order.receiptChoose}
          className="block w-full min-w-0 text-sm text-muted-foreground file:mr-3 file:rounded-md file:border file:border-border file:bg-card file:px-3 file:py-1.5 file:text-sm file:text-foreground hover:file:bg-muted"
        />
        <Button onClick={handleUpload} disabled={!file || upload.isPending} className="shrink-0">
          {upload.isPending ? strings.order.receiptUploading : strings.order.receiptUpload}
        </Button>
      </div>
    </div>
  )
}

function CancelOrder({ number, email }: { number: string; email: string }) {
  const cancel = useCancelOrder(number, email)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleCancel = async () => {
    setError(null)
    try {
      await cancel.mutateAsync()
      toast.success(strings.order.cancelled)
    } catch (err) {
      setError(getErrorMessage(err, strings.order.cancelError, { conflict: strings.order.cancelClosed }))
    }
  }

  return (
    <div className="space-y-2">
      {error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}
      <Button
        variant="outline"
        className="text-destructive hover:text-destructive"
        onClick={() => setOpen(true)}
        disabled={cancel.isPending}
      >
        {cancel.isPending ? strings.order.cancelling : strings.order.cancel}
      </Button>
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{strings.order.cancelQuestion}</AlertDialogTitle>
            <AlertDialogDescription>{strings.order.cancelDescription}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{strings.order.cancelKeep}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                setOpen(false)
                handleCancel()
              }}
            >
              {strings.order.cancelConfirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
