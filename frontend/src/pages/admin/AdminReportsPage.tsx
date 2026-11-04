import type { ReactNode } from "react"
import { useSearchParams } from "react-router-dom"
import { AlertTriangle } from "lucide-react"
import { strings } from "@/lib/strings"
import { formatAmount } from "@/lib/utils"
import { PAYMENT_METHODS, type OrderChannel, type PaymentMethod } from "@/lib/types"
import type { SalesTotals } from "@/api/dashboard"
import { useSalesRanking, useSalesReport, type RankingSort, type SalesReportFilters } from "@/api/sales"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { FilterSelect } from "@/components/list-toolbar"
import { PeriodBarChart } from "@/components/platform-registrations-chart"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"

const MAX_DAYS = 366
const compactMoney = new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS", notation: "compact", maximumFractionDigits: 1 })

const pad = (n: number) => String(n).padStart(2, "0")
const isoDate = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
const daysBetween = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1

// Por defecto, los ultimos 30 dias hasta hoy (lo mismo que hace el backend sin fechas)
function defaultRange() {
  const today = new Date()
  const from = new Date(today)
  from.setDate(today.getDate() - 29)
  return { from: isoDate(from), to: isoDate(today) }
}

// Reportes de ventas (/admin/reports, solo ADMINISTRADOR). Los filtros van en la URL para compartir o volver
export default function AdminReportsPage() {
  const [params, setParams] = useSearchParams()
  const defaults = defaultRange()
  const filters: SalesReportFilters = {
    from: params.get("desde") || defaults.from,
    to: params.get("hasta") || defaults.to,
    groupBy: (params.get("agrupar") as SalesReportFilters["groupBy"] | null) ?? "day",
    channel: (params.get("canal") as OrderChannel | null) ?? "",
    paymentMethod: (params.get("pago") as PaymentMethod | null) ?? "",
  }
  const sort = (params.get("orden") as RankingSort | null) ?? "units"

  const update = (changes: Record<string, string>) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        return next
      },
      { replace: true }
    )

  const days = daysBetween(filters.from, filters.to)
  const rangeError = days < 1 ? strings.salesReports.rangeInvalid : days > MAX_DAYS ? strings.salesReports.rangeTooLong : null
  const report = useSalesReport(filters, !rangeError)
  const ranking = useSalesRanking({ from: filters.from, to: filters.to, channel: filters.channel, sort }, !rangeError)
  const r = strings.salesReports
  const data = report.data

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader title={r.title} highlight={r.highlight} subtitle={r.subtitle} backTo="/admin/orders" />

        <div className="flex flex-wrap items-end gap-3">
          <LabeledControl label={r.from}>
            <Input type="date" value={filters.from} max={filters.to} onChange={(event) => update({ desde: event.target.value })} className="h-9 w-40" />
          </LabeledControl>
          <LabeledControl label={r.to}>
            <Input type="date" value={filters.to} min={filters.from} onChange={(event) => update({ hasta: event.target.value })} className="h-9 w-40" />
          </LabeledControl>
          <LabeledControl label={r.groupBy}>
            <FilterSelect value={filters.groupBy} onChange={(value) => update({ agrupar: value === "day" ? "" : value })} label={r.groupBy}>
              {(["day", "week", "month"] as const).map((value) => (
                <option key={value} value={value}>
                  {r.groups[value]}
                </option>
              ))}
            </FilterSelect>
          </LabeledControl>
          <LabeledControl label={strings.adminOrders.channelFilter}>
            <FilterSelect value={filters.channel} onChange={(value) => update({ canal: value })} label={strings.adminOrders.channelFilter}>
              <option value="">{strings.adminOrders.allChannels}</option>
              {(["ONLINE", "LOCAL"] as const).map((channel) => (
                <option key={channel} value={channel}>
                  {strings.admin.sales.channels[channel]}
                </option>
              ))}
            </FilterSelect>
          </LabeledControl>
          <LabeledControl label={strings.adminOrders.paymentFilter}>
            <FilterSelect value={filters.paymentMethod} onChange={(value) => update({ pago: value })} label={strings.adminOrders.paymentFilter}>
              <option value="">{strings.adminOrders.allPayments}</option>
              {PAYMENT_METHODS.map((method) => (
                <option key={method} value={method}>
                  {strings.admin.sales.payments[method]}
                </option>
              ))}
            </FilterSelect>
          </LabeledControl>
        </div>

        {rangeError ? (
          <p className="text-sm text-destructive">{rangeError}</p>
        ) : (
          <>
            <Card className={report.isFetching && data ? "opacity-70" : undefined}>
              <CardContent className="space-y-6">
                {data ? (
                  <>
                    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6">
                      <StatTile label={r.revenue} value={formatAmount(data.totals.revenue)} />
                      <StatTile label={r.orders} value={data.totals.orders} />
                      <StatTile label={r.units} value={data.totals.units} />
                      <StatTile
                        label={r.averageTicket}
                        value={formatAmount(data.totals.orders > 0 ? data.totals.revenue / data.totals.orders : 0)}
                      />
                      <StatTile label={r.cost} value={formatAmount(data.totals.cost)} />
                      <StatTile label={r.profit} value={formatAmount(data.totals.profit)} />
                    </div>
                    {data.totals.discounts > 0 ? (
                      <p className="text-sm text-muted-foreground">{strings.admin.sales.discounts(formatAmount(data.totals.discounts))}</p>
                    ) : null}
                    {data.totals.unitsWithoutCost > 0 ? (
                      <p className="flex items-start gap-2 text-sm text-destructive">
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                        {strings.admin.sales.withoutCost(data.totals.unitsWithoutCost)}
                      </p>
                    ) : null}
                    {data.totals.orders === 0 ? <p className="text-sm text-muted-foreground">{r.noSales}</p> : null}
                    <PeriodBarChart
                      data={data.periods.map((period) => ({ period: period.start, value: period.totals.revenue }))}
                      groupBy={data.groupBy}
                      label={r.chartLabel}
                      formatValue={(value) => compactMoney.format(value)}
                      axisWidth={64}
                    />
                  </>
                ) : report.isError ? (
                  <p className="text-sm text-destructive">{r.loadError}</p>
                ) : (
                  <p className="text-sm text-muted-foreground">{r.loading}</p>
                )}
              </CardContent>
            </Card>

            {data && data.totals.orders > 0 ? (
              <div className="grid gap-6 lg:grid-cols-2">
                <BreakdownCard title={r.byChannel} rows={data.byChannel} labels={strings.admin.sales.channels} />
                <BreakdownCard title={r.byPayment} rows={data.byPaymentMethod} labels={strings.admin.sales.payments} />
              </div>
            ) : null}

            <Card>
              <CardHeader className="flex flex-col gap-3 space-y-0 sm:flex-row sm:items-start sm:justify-between">
                <div className="space-y-1.5">
                  <CardTitle>{r.ranking}</CardTitle>
                  <CardDescription>{r.rankingHelp}</CardDescription>
                </div>
                <FilterSelect value={sort} onChange={(value) => update({ orden: value === "units" ? "" : value })} label={r.sortBy}>
                  {(["units", "revenue", "profit"] as const).map((value) => (
                    <option key={value} value={value}>
                      {r.sortBy}: {r.sorts[value]}
                    </option>
                  ))}
                </FilterSelect>
              </CardHeader>
              <CardContent>
                {ranking.data ? (
                  ranking.data.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{r.rankingEmpty}</p>
                  ) : (
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted-foreground">
                            <th className="py-2 pr-2 font-normal">#</th>
                            <th className="py-2 pr-4 font-normal">{r.columns.product}</th>
                            <th className="py-2 pr-4 text-right font-normal">{r.columns.units}</th>
                            <th className="py-2 pr-4 text-right font-normal">{r.columns.revenue}</th>
                            <th className="py-2 pr-4 text-right font-normal">{r.columns.cost}</th>
                            <th className="py-2 text-right font-normal">{r.columns.profit}</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                          {ranking.data.map((row, index) => (
                            <tr key={`${row.type}-${row.id}`}>
                              <td className="py-2 pr-2 tabular-nums text-muted-foreground">{index + 1}</td>
                              <td className="py-2 pr-4">
                                {row.name}
                                {row.type === "BUNDLE" ? <span className="text-muted-foreground"> ({strings.cart.bundle})</span> : null}
                              </td>
                              <td className="py-2 pr-4 text-right tabular-nums">{row.units}</td>
                              <td className="py-2 pr-4 text-right tabular-nums">{formatAmount(row.revenue)}</td>
                              <td className="py-2 pr-4 text-right tabular-nums text-muted-foreground">{formatAmount(row.cost)}</td>
                              <td className="py-2 text-right tabular-nums">
                                {formatAmount(row.profit)}
                                {row.unitsWithoutCost > 0 ? (
                                  <span className="block text-xs text-destructive">{r.withoutCostShort(row.unitsWithoutCost)}</span>
                                ) : null}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )
                ) : ranking.isError ? (
                  <p className="text-sm text-destructive">{r.loadError}</p>
                ) : (
                  <p className="text-sm text-muted-foreground">{r.loading}</p>
                )}
              </CardContent>
            </Card>
          </>
        )}
      </main>
    </div>
  )
}

function LabeledControl({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-1 text-xs text-muted-foreground">
      {label}
      {children}
    </label>
  )
}

function StatTile({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-xl font-medium tabular-nums">{value}</p>
    </div>
  )
}

function BreakdownCard({ title, rows, labels }: { title: string; rows: { key: string; totals: SalesTotals }[]; labels: Record<string, string> }) {
  const r = strings.salesReports
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs uppercase tracking-wider text-muted-foreground">
              <th className="py-2 pr-4 font-normal" />
              <th className="py-2 pr-4 text-right font-normal">{r.columns.orders}</th>
              <th className="py-2 pr-4 text-right font-normal">{r.columns.units}</th>
              <th className="py-2 pr-4 text-right font-normal">{r.columns.revenue}</th>
              <th className="py-2 text-right font-normal">{r.columns.profit}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {rows.map((row) => (
              <tr key={row.key}>
                <td className="py-2 pr-4">{labels[row.key] ?? row.key}</td>
                <td className="py-2 pr-4 text-right tabular-nums">{row.totals.orders}</td>
                <td className="py-2 pr-4 text-right tabular-nums">{row.totals.units}</td>
                <td className="py-2 pr-4 text-right tabular-nums">{formatAmount(row.totals.revenue)}</td>
                <td className="py-2 text-right tabular-nums">{formatAmount(row.totals.profit)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </CardContent>
    </Card>
  )
}
