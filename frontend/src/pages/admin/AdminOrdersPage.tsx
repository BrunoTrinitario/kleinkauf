import { useEffect, useState } from "react"
import { Link, useLocation, useSearchParams } from "react-router-dom"
import { AlertTriangle, Landmark } from "lucide-react"
import { strings } from "@/lib/strings"
import { formatAmount, formatServerDateTime } from "@/lib/utils"
import type { AdminOrderFilters, OrderChannel, OrderStatus } from "@/lib/types"
import { useAdminOrders, useCheckoutInfo, usePendingReviewCount } from "@/api/sales"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "@/components/list-toolbar"
import { OrderStatusBadge } from "@/components/order-status-badge"
import { PaginationControls } from "@/components/pagination-controls"
import { SalesSettingsDialog } from "@/components/sales-settings-dialog"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"

const STATUSES = Object.keys(strings.order.status) as OrderStatus[]
const CHANNELS: OrderChannel[] = ["ONLINE", "LOCAL"]

// Lista de pedidos del panel de ventas (solo ADMINISTRADOR). Los filtros van en la URL: "atras" desde el detalle
// vuelve a la misma lista
export default function AdminOrdersPage() {
  const [params, setParams] = useSearchParams()
  const [searchInput, setSearchInput] = useState(params.get("q") ?? "")
  const search = useDebouncedValue(searchInput.trim())
  // Desde el checkout ("Cargar datos de cobro") se llega con el dialogo abierto
  const location = useLocation()
  const [settingsOpen, setSettingsOpen] = useState(
    () => !!(location.state as { openSettings?: boolean } | null)?.openSettings
  )

  const filters: AdminOrderFilters = {
    status: (params.get("estado") as OrderStatus | null) ?? "",
    channel: (params.get("canal") as OrderChannel | null) ?? "",
    q: params.get("q") ?? "",
    from: params.get("desde") ?? "",
    to: params.get("hasta") ?? "",
    page: Math.max(0, Number(params.get("pagina") ?? 1) - 1) || 0,
  }

  // Cambia un filtro y vuelve a la primera pagina (salvo que se cambie la pagina)
  const update = (changes: Record<string, string>) => {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        if (!("pagina" in changes)) next.delete("pagina")
        return next
      },
      { replace: true }
    )
  }

  useEffect(() => {
    if (search !== (params.get("q") ?? "")) update({ q: search })
  }, [search])

  const orders = useAdminOrders(filters)
  const pendingReview = usePendingReviewCount(true).data ?? 0
  const checkout = useCheckoutInfo()
  const hasFilters = !!(filters.status || filters.channel || filters.q || filters.from || filters.to)

  const clearFilters = () => {
    setSearchInput("")
    setParams(new URLSearchParams(), { replace: true })
  }

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader
          title={strings.adminOrders.title}
          highlight={strings.adminOrders.highlight}
          subtitle={strings.adminOrders.subtitle}
          backTo="/admin"
          action={
            <Button variant="outline" onClick={() => setSettingsOpen(true)}>
              <Landmark className="mr-2 h-4 w-4" />
              {strings.adminOrders.settings}
            </Button>
          }
        />

        {checkout.data && !checkout.data.paymentConfigured ? (
          <div className="flex flex-col gap-3 rounded-lg border border-destructive/40 bg-card p-4 sm:flex-row sm:items-center sm:justify-between" role="alert">
            <div className="flex gap-3">
              <AlertTriangle className="h-5 w-5 shrink-0 text-destructive" />
              <p className="text-sm">{strings.adminOrders.paymentMissing}</p>
            </div>
            <Button size="sm" onClick={() => setSettingsOpen(true)} className="shrink-0">
              {strings.adminOrders.paymentMissingAction}
            </Button>
          </div>
        ) : null}

        <div className="space-y-3">
          <ListToolbar>
            <SearchField value={searchInput} onChange={setSearchInput} placeholder={strings.adminOrders.search} />
            <FilterGroup>
              <FilterSelect
                value={filters.status ?? ""}
                onChange={(value) => update({ estado: value })}
                label={strings.adminOrders.statusFilter}
              >
                <option value="">{strings.adminOrders.allStatuses}</option>
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {strings.order.status[status]}
                  </option>
                ))}
              </FilterSelect>
              <FilterSelect
                value={filters.channel ?? ""}
                onChange={(value) => update({ canal: value })}
                label={strings.adminOrders.channelFilter}
              >
                <option value="">{strings.adminOrders.allChannels}</option>
                {CHANNELS.map((channel) => (
                  <option key={channel} value={channel}>
                    {strings.admin.sales.channels[channel]}
                  </option>
                ))}
              </FilterSelect>
            </FilterGroup>
          </ListToolbar>

          <div className="flex flex-wrap items-end gap-3">
            <DateField label={strings.adminOrders.from} value={filters.from ?? ""} max={filters.to || undefined} onChange={(value) => update({ desde: value })} />
            <DateField label={strings.adminOrders.to} value={filters.to ?? ""} min={filters.from || undefined} onChange={(value) => update({ hasta: value })} />
            {pendingReview > 0 && filters.status !== "PAGO_EN_REVISION" ? (
              <Button variant="outline" size="sm" onClick={() => update({ estado: "PAGO_EN_REVISION" })}>
                <span className="mr-2 inline-block h-2 w-2 rounded-full bg-destructive" />
                {strings.adminOrders.toReview(pendingReview)}
              </Button>
            ) : null}
            {hasFilters ? (
              <Button variant="ghost" size="sm" onClick={clearFilters}>
                {strings.adminOrders.clearFilters}
              </Button>
            ) : null}
          </div>
        </div>

        {orders.isPending ? (
          <p className="text-muted-foreground">{strings.adminOrders.loading}</p>
        ) : orders.isError || !orders.data ? (
          <p className="text-destructive">{strings.adminOrders.loadError}</p>
        ) : orders.data.items.length === 0 ? (
          <p className="text-muted-foreground">{hasFilters ? strings.adminOrders.empty : strings.adminOrders.emptyStore}</p>
        ) : (
          <>
            <Card className={`p-0 gap-0 overflow-hidden ${orders.isFetching ? "opacity-70" : ""}`}>
              <div className="hidden md:grid grid-cols-[8rem_9rem_minmax(0,1fr)_6rem_10rem_8rem] gap-4 border-b border-border px-4 py-2 text-xs uppercase tracking-wider text-muted-foreground">
                <span>{strings.adminOrders.columns.number}</span>
                <span>{strings.adminOrders.columns.date}</span>
                <span>{strings.adminOrders.columns.buyer}</span>
                <span>{strings.adminOrders.columns.channel}</span>
                <span>{strings.adminOrders.columns.status}</span>
                <span className="text-right">{strings.adminOrders.columns.total}</span>
              </div>
              <ul className="divide-y divide-border">
                {orders.data.items.map((order) => (
                  <li key={order.number}>
                    <Link
                      to={`/admin/orders/${order.number}`}
                      className="grid gap-1 px-4 py-3 hover:bg-muted/50 focus-visible:bg-muted/50 focus-visible:outline-none md:grid-cols-[8rem_9rem_minmax(0,1fr)_6rem_10rem_8rem] md:items-center md:gap-4"
                    >
                      <span className="flex items-center justify-between gap-2 md:block">
                        <span className="font-mono font-medium">{order.number}</span>
                        <span className="md:hidden">
                          <OrderStatusBadge status={order.status} />
                        </span>
                      </span>
                      <span className="text-sm text-muted-foreground">{formatServerDateTime(order.createdAt)}</span>
                      <span className="min-w-0 text-sm">
                        <span className="block truncate">{order.buyerName || strings.adminOrders.consumer}</span>
                        {order.buyerEmail ? (
                          <span className="block truncate text-muted-foreground">{order.buyerEmail}</span>
                        ) : null}
                      </span>
                      <span className="text-sm text-muted-foreground">
                        {strings.admin.sales.channels[order.channel]}
                        {order.stockReview ? (
                          <span className="ml-2 inline-flex items-center gap-1 text-destructive" title={strings.adminOrders.stockReviewHelp}>
                            <AlertTriangle className="h-3.5 w-3.5" />
                            <span className="sr-only">{strings.adminOrders.stockReview}</span>
                          </span>
                        ) : null}
                      </span>
                      <span className="hidden md:block">
                        <OrderStatusBadge status={order.status} />
                      </span>
                      <span className="flex items-baseline justify-between gap-2 md:block md:text-right">
                        <span className="text-sm text-muted-foreground md:hidden">{strings.adminOrders.units(order.units)}</span>
                        <span className="font-medium tabular-nums">{formatAmount(order.total)}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
            <PaginationControls
              page={orders.data.page}
              totalPages={orders.data.totalPages}
              disabled={orders.isFetching}
              onPageChange={(page) => update({ pagina: page > 0 ? String(page + 1) : "" })}
            />
          </>
        )}
      </main>
      <SalesSettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} />
    </div>
  )
}

function DateField({
  label,
  value,
  min,
  max,
  onChange,
}: {
  label: string
  value: string
  min?: string
  max?: string
  onChange: (value: string) => void
}) {
  return (
    <label className="grid gap-1 text-xs text-muted-foreground">
      {label}
      <Input type="date" value={value} min={min} max={max} onChange={(event) => onChange(event.target.value)} className="h-9 w-40" />
    </label>
  )
}
