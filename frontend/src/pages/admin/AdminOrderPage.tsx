import { useEffect, useState } from "react"
import { useLocation, useParams } from "react-router-dom"
import axios from "axios"
import { toast } from "sonner"
import { AlertTriangle, Download, Eye } from "lucide-react"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { formatAmount, formatServerDateTime } from "@/lib/utils"
import type { AdminOrder, OrderReceipt, OrderStatus } from "@/lib/types"
import { fetchReceipt, useAdminOrder, useChangeOrderStatus } from "@/api/sales"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { OrderStatusBadge } from "@/components/order-status-badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Textarea } from "@/components/ui/textarea"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

const AWAITING_PAYMENT: OrderStatus[] = ["PENDIENTE_PAGO", "PAGO_EN_REVISION", "RECHAZADO"]
const PAID: OrderStatus[] = ["PAGADO", "EN_PREPARACION", "LISTO", "ENTREGADO"]
const FINAL: OrderStatus[] = ["ENTREGADO", "CANCELADO", "VENCIDO"]
const DELIVERY_STEPS: OrderStatus[] = ["EN_PREPARACION", "LISTO", "ENTREGADO"]
const NEEDS_REASON: OrderStatus[] = ["RECHAZADO", "CANCELADO"]

/*
Los cambios que puede hacer el administrador desde el estado actual (el mismo criterio que OrderStatus.canChangeTo en
sales): validar mientras no se pago, rechazar un comprobante en revision, avanzar la entrega sin volver atras (se
pueden saltear pasos) y cancelar cualquier pedido que no este cerrado
 */
function availableChanges(status: OrderStatus): OrderStatus[] {
  if (FINAL.includes(status)) return []
  const changes: OrderStatus[] = []
  if (AWAITING_PAYMENT.includes(status)) changes.push("PAGADO")
  if (status === "PAGO_EN_REVISION") changes.push("RECHAZADO")
  if (PAID.includes(status)) {
    const current = PAID.indexOf(status)
    changes.push(...DELIVERY_STEPS.filter((step) => PAID.indexOf(step) > current))
  }
  changes.push("CANCELADO")
  return changes
}

// Detalle de un pedido en el panel de ventas (/admin/orders/:number, solo ADMINISTRADOR)
export default function AdminOrderPage() {
  const { number = "" } = useParams()
  // La flecha vuelve a la lista con los filtros con los que se entro (vienen en el estado del link)
  const listSearch = (useLocation().state as { listSearch?: string } | null)?.listSearch ?? ""
  const listPath = `/admin/orders${listSearch}`
  const order = useAdminOrder(number)
  const notFound = axios.isAxiosError(order.error) && order.error.response?.status === 404

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        {order.data ? (
          <OrderDetail order={order.data} listPath={listPath} />
        ) : order.isError ? (
          <>
            <PageHeader title={strings.order.title} highlight="" subtitle="" backTo={listPath} />
            <p className="text-destructive">{notFound ? strings.adminOrder.notFound : strings.adminOrder.loadError}</p>
          </>
        ) : (
          <p className="text-muted-foreground">{strings.adminOrder.loading}</p>
        )}
      </main>
    </div>
  )
}

function OrderDetail({ order, listPath }: { order: AdminOrder; listPath: string }) {
  const subtitle = [
    strings.adminOrder.created(formatServerDateTime(order.createdAt)),
    strings.adminOrder.channelPayment(
      strings.admin.sales.channels[order.channel],
      order.paymentMethod ? strings.admin.sales.payments[order.paymentMethod] : "-"
    ),
  ].join(" · ")

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
        <PageHeader title={order.number} highlight="" subtitle={subtitle} backTo={listPath} action={<OrderStatusBadge status={order.status} />} />
        {order.stockReview ? (
          <p className="-mt-6 flex items-center gap-2 text-sm text-destructive">
            <AlertTriangle className="h-4 w-4" />
            {strings.adminOrders.stockReviewHelp}
          </p>
        ) : null}
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        <div className="space-y-6 min-w-0">
          <StatusActions order={order} />
          <ItemsCard order={order} />
          {order.channel === "ONLINE" ? <ReceiptsCard order={order} /> : null}
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>{strings.adminOrder.buyerTitle}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1 text-sm">
              {order.buyer?.name || order.buyer?.email || order.buyer?.phone ? (
                <>
                  {order.buyer.name ? <p>{order.buyer.name}</p> : null}
                  {order.buyer.email ? (
                    <p>
                      <a href={`mailto:${order.buyer.email}`} className="text-muted-foreground underline-offset-4 hover:underline">
                        {order.buyer.email}
                      </a>
                    </p>
                  ) : null}
                  {order.buyer.phone ? (
                    <p>
                      <a href={`tel:${order.buyer.phone}`} className="text-muted-foreground underline-offset-4 hover:underline">
                        {order.buyer.phone}
                      </a>
                    </p>
                  ) : null}
                </>
              ) : (
                <p className="text-muted-foreground">{strings.adminOrder.noBuyer}</p>
              )}
              {order.userId ? <p className="text-xs text-muted-foreground">{strings.adminOrder.account(order.userId)}</p> : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{strings.adminOrder.deliveryTitle}</CardTitle>
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
                  {strings.adminOrder.notes}: {order.notes}
                </p>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{strings.adminOrder.historyTitle}</CardTitle>
            </CardHeader>
            <CardContent>
              <ol className="space-y-3 text-sm">
                {order.history.map((entry, index) => (
                  <li key={index} className="flex flex-col">
                    <span>{strings.order.status[entry.status]}</span>
                    <span className="text-xs text-muted-foreground">
                      {formatServerDateTime(entry.createdAt)} ·{" "}
                      {entry.userId ? strings.adminOrder.byUser(entry.userId) : strings.adminOrder.byBuyerOrSystem}
                    </span>
                    {entry.reason ? <span className="text-xs text-muted-foreground">{entry.reason}</span> : null}
                  </li>
                ))}
              </ol>
              {order.expiresAt && AWAITING_PAYMENT.includes(order.status) ? (
                <p className="mt-4 border-t border-border pt-3 text-xs text-muted-foreground">
                  {strings.adminOrder.expires(formatServerDateTime(order.expiresAt))}
                </p>
              ) : null}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

function StatusActions({ order }: { order: AdminOrder }) {
  const changes = availableChanges(order.status)
  const [target, setTarget] = useState<OrderStatus | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{strings.adminOrder.actionsTitle}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm text-muted-foreground">{strings.adminOrder.statusHelp[order.status]}</p>
        {changes.length === 0 ? (
          <p className="text-sm text-muted-foreground">{strings.adminOrder.noActions}</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {changes.map((status) => (
              <Button
                key={status}
                variant={status === "CANCELADO" ? "outline" : status === "RECHAZADO" ? "outline" : changes[0] === status ? "default" : "secondary"}
                className={status === "CANCELADO" || status === "RECHAZADO" ? "text-destructive hover:text-destructive" : undefined}
                onClick={() => setTarget(status)}
              >
                {strings.adminOrder.actions[status]}
              </Button>
            ))}
          </div>
        )}
      </CardContent>
      <StatusDialog number={order.number} target={target} onClose={() => setTarget(null)} />
    </Card>
  )
}

function statusErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const text = typeof error.response?.data === "string" ? error.response.data : ""
    if (status === 409 && /someone else|reload/i.test(text)) return strings.adminOrder.changedByOther
    if (status === 409 && /cannot change/i.test(text)) return strings.adminOrder.invalidChange
    if (status === 503) return strings.adminOrder.unavailable
  }
  return getErrorMessage(error, strings.adminOrder.changeError, { badRequest: strings.adminOrder.reasonMissing })
}

// Confirmacion de cada cambio, con el motivo (obligatorio para rechazar o cancelar; lo ve el comprador)
function StatusDialog({ number, target, onClose }: { number: string; target: OrderStatus | null; onClose: () => void }) {
  const change = useChangeOrderStatus(number)
  const order = useAdminOrder(number)
  const [reason, setReason] = useState("")
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (target) {
      setReason("")
      setError(null)
    }
  }, [target])

  if (!target) return null
  const needsReason = NEEDS_REASON.includes(target)
  const confirm = strings.adminOrder.confirm[target]

  const handleConfirm = async () => {
    const clean = reason.trim()
    if (needsReason && !clean) {
      setError(strings.adminOrder.reasonMissing)
      return
    }
    if (clean.length > 500) {
      setError(strings.adminOrder.reasonTooLong)
      return
    }
    setError(null)
    try {
      await change.mutateAsync({ status: target, reason: clean })
      toast.success(strings.adminOrder.changed(strings.order.status[target]))
      onClose()
    } catch (err) {
      setError(statusErrorMessage(err))
      // Si el pedido cambio por otro lado, se recarga para que los botones muestren lo que corresponde
      if (axios.isAxiosError(err) && err.response?.status === 409) order.refetch()
    }
  }

  const destructive = target === "CANCELADO" || target === "RECHAZADO"

  return (
    <Dialog open onOpenChange={(open) => !open && !change.isPending && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{confirm.title}</DialogTitle>
          <DialogDescription>{confirm.text}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-2">
          <label htmlFor="status-reason" className="text-sm font-medium">
            {needsReason ? strings.adminOrder.reasonRequired : strings.adminOrder.reasonOptional}
          </label>
          <Textarea id="status-reason" value={reason} maxLength={500} rows={3} onChange={(event) => setReason(event.target.value)} />
        </div>
        {error ? (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={change.isPending}>
            {strings.adminOrder.confirmBack}
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={change.isPending}
            className={destructive ? "bg-destructive text-white hover:bg-destructive/90" : undefined}
          >
            {change.isPending ? strings.adminOrder.saving : strings.adminOrder.actions[target]}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ItemsCard({ order }: { order: AdminOrder }) {
  const costKnown = order.items.every((item) => item.unitCost !== null)
  const cost = order.items.reduce((sum, item) => sum + (item.unitCost ?? 0) * item.quantity, 0)
  const priceOrDash = (value: number | null, empty: string) => (value === null ? empty : formatAmount(value))

  return (
    <Card>
      <CardHeader>
        <CardTitle>{strings.adminOrder.itemsTitle}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th className="py-2 pr-4 font-normal">{strings.adminOrder.columns.product}</th>
                <th className="py-2 pr-4 text-right font-normal">{strings.adminOrder.columns.quantity}</th>
                <th className="py-2 pr-4 text-right font-normal">{strings.adminOrder.columns.listPrice}</th>
                <th className="py-2 pr-4 text-right font-normal">{strings.adminOrder.columns.unitPrice}</th>
                <th className="py-2 pr-4 text-right font-normal">{strings.adminOrder.columns.unitCost}</th>
                <th className="py-2 text-right font-normal">{strings.adminOrder.columns.subtotal}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {order.items.map((item) => (
                <tr key={`${item.type}-${item.id}`}>
                  <td className="py-2 pr-4">
                    {item.name}
                    {item.type === "BUNDLE" ? <span className="text-muted-foreground"> ({strings.cart.bundle})</span> : null}
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">{item.quantity}</td>
                  <td className="py-2 pr-4 text-right tabular-nums text-muted-foreground">
                    {priceOrDash(item.listPrice, strings.adminOrder.noListPrice)}
                  </td>
                  <td className="py-2 pr-4 text-right tabular-nums">{formatAmount(item.unitPrice)}</td>
                  <td className="py-2 pr-4 text-right tabular-nums text-muted-foreground">
                    {priceOrDash(item.unitCost, strings.adminOrder.noCost)}
                  </td>
                  <td className="py-2 text-right tabular-nums">{formatAmount(item.subtotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <dl className="space-y-1 border-t border-border pt-4 text-sm">
          {order.discount > 0 ? (
            <div className="flex justify-between text-muted-foreground">
              <dt>{strings.adminOrder.discount}</dt>
              <dd className="tabular-nums">-{formatAmount(order.discount)}</dd>
            </div>
          ) : null}
          <div className="flex items-baseline justify-between">
            <dt className="text-muted-foreground">{strings.adminOrder.total}</dt>
            <dd className="text-lg font-medium tabular-nums">{formatAmount(order.total)}</dd>
          </div>
          <div className="flex justify-between text-muted-foreground">
            <dt>{strings.adminOrder.cost}</dt>
            <dd className="tabular-nums">{formatAmount(cost)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-muted-foreground">{strings.adminOrder.profit}</dt>
            <dd className="tabular-nums">{formatAmount(order.total - cost)}</dd>
          </div>
          {!costKnown ? <p className="pt-1 text-xs text-muted-foreground">{strings.adminOrder.profitWithoutCost}</p> : null}
        </dl>
      </CardContent>
    </Card>
  )
}

const RECEIPT_EXTENSION: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "application/pdf": "pdf" }

function ReceiptsCard({ order }: { order: AdminOrder }) {
  // El mas nuevo arriba: es el que hay que revisar
  const receipts = [...order.receipts].reverse()
  const [preview, setPreview] = useState<{ index: number; receipt: OrderReceipt; url: string } | null>(null)
  const [loadingId, setLoadingId] = useState<number | null>(null)

  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview.url)
  }, [preview])

  const load = async (receipt: OrderReceipt) => {
    setLoadingId(receipt.id)
    try {
      return URL.createObjectURL(await fetchReceipt(order.number, receipt.id))
    } catch {
      toast.error(strings.adminOrder.receiptError)
      return null
    } finally {
      setLoadingId(null)
    }
  }

  const handleView = async (receipt: OrderReceipt, index: number) => {
    const url = await load(receipt)
    if (url) setPreview({ index, receipt, url })
  }

  const handleDownload = async (receipt: OrderReceipt) => {
    const url = await load(receipt)
    if (!url) return
    const link = document.createElement("a")
    link.href = url
    link.download = `comprobante-${order.number}-${receipt.id}.${RECEIPT_EXTENSION[receipt.contentType] ?? "bin"}`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{strings.adminOrder.receiptsTitle}</CardTitle>
      </CardHeader>
      <CardContent>
        {receipts.length === 0 ? (
          <p className="text-sm text-muted-foreground">{strings.adminOrder.noReceipts}</p>
        ) : (
          <ul className="divide-y divide-border">
            {receipts.map((receipt) => {
              const index = order.receipts.indexOf(receipt) + 1
              return (
                <li key={receipt.id} className="flex flex-wrap items-center justify-between gap-2 py-2 first:pt-0 last:pb-0">
                  <span className="text-sm">
                    {strings.adminOrder.receiptLabel(index, formatServerDateTime(receipt.createdAt))}
                    <span className="text-muted-foreground"> · {(RECEIPT_EXTENSION[receipt.contentType] ?? "").toUpperCase()}</span>
                  </span>
                  <span className="flex gap-2">
                    <Button variant="outline" size="sm" disabled={loadingId === receipt.id} onClick={() => handleView(receipt, index)}>
                      <Eye className="mr-1.5 h-4 w-4" />
                      {strings.adminOrder.receiptView}
                    </Button>
                    <Button variant="ghost" size="sm" disabled={loadingId === receipt.id} onClick={() => handleDownload(receipt)}>
                      <Download className="mr-1.5 h-4 w-4" />
                      {strings.adminOrder.receiptDownload}
                    </Button>
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>

      <Dialog open={!!preview} onOpenChange={(open) => !open && setPreview(null)}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>{preview ? strings.adminOrder.receiptPreviewTitle(preview.index) : ""}</DialogTitle>
            <DialogDescription>{preview ? formatServerDateTime(preview.receipt.createdAt) : ""}</DialogDescription>
          </DialogHeader>
          {preview ? (
            preview.receipt.contentType === "application/pdf" ? (
              <iframe src={preview.url} title={strings.adminOrder.receiptPreviewTitle(preview.index)} className="h-[70vh] w-full rounded-md border border-border" />
            ) : (
              <img src={preview.url} alt={strings.adminOrder.receiptPreviewTitle(preview.index)} className="mx-auto max-h-[70vh] rounded-md object-contain" />
            )
          ) : null}
        </DialogContent>
      </Dialog>
    </Card>
  )
}
