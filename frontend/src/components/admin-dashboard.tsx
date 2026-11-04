import { useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { rangeParams, type RangeKey } from "@/lib/periods"
import { formatPrice, formatServerDateTime } from "@/lib/utils"
import {
  useProductsDashboard,
  useSalesSummary,
  useSalesTopProducts,
  useUsersDashboard,
  type SalesTotals,
} from "@/api/dashboard"
import { FilterSelect } from "@/components/list-toolbar"
import { PeriodBarChart } from "@/components/platform-registrations-chart"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { AlertTriangle } from "lucide-react"

const money = (value: number) => formatPrice(value, "")
const compactMoney = new Intl.NumberFormat("es-AR", {
  style: "currency",
  currency: "ARS",
  notation: "compact",
  maximumFractionDigits: 1,
})

function StatTile({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-xl font-medium tabular-nums">{value}</p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}

// Cada bloque carga por separado: mientras carga o si falla, solo ese bloque lo muestra
function BlockState({ isPending, isError }: { isPending: boolean; isError: boolean }) {
  if (isError) return <p className="text-sm text-destructive">{strings.admin.blockError}</p>
  if (isPending) return <p className="text-sm text-muted-foreground">{strings.admin.loading}</p>
  return null
}

// Resumen del panel de la tienda (solo ADMINISTRADOR): ventas, productos, stock y usuarios
export function AdminDashboard() {
  const { user } = useAuth()
  const enabled = !!user?.token
  const [rangeKey, setRangeKey] = useState<RangeKey>("days")
  const range = rangeParams(rangeKey)

  return (
    <div className="space-y-6">
      <div className="flex justify-end">
        <FilterSelect
          value={rangeKey}
          onChange={(value) => setRangeKey(value as RangeKey)}
          label={strings.admin.rangeLabel}
        >
          <option value="days">{strings.platformDashboard.ranges.days}</option>
          <option value="weeks">{strings.platformDashboard.ranges.weeks}</option>
          <option value="months">{strings.platformDashboard.ranges.months}</option>
        </FilterSelect>
      </div>

      <SalesBlock rangeKey={rangeKey} enabled={enabled} />

      <div className="grid gap-6 lg:grid-cols-2">
        <ProductsBlock enabled={enabled} />
        <StockBlock enabled={enabled} />
      </div>

      <UsersBlock range={range} enabled={enabled} />
    </div>
  )
}

function TotalsList({ title, rows, labels }: { title: string; rows: { key: string; totals: SalesTotals }[]; labels: Record<string, string> }) {
  if (rows.length === 0) return null
  return (
    <div className="space-y-2">
      <h4 className="text-sm font-medium">{title}</h4>
      <ul className="space-y-1 text-sm">
        {rows.map((row) => (
          <li key={row.key} className="flex justify-between gap-3">
            <span className="text-muted-foreground">{labels[row.key] ?? row.key}</span>
            <span className="tabular-nums">{money(row.totals.revenue)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function SalesBlock({ rangeKey, enabled }: { rangeKey: RangeKey; enabled: boolean }) {
  const range = rangeParams(rangeKey)
  const summary = useSalesSummary(range, enabled)
  const top = useSalesTopProducts(range, enabled)
  const data = summary.data
  const s = strings.admin.sales

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
        <div className="space-y-1.5">
          <CardTitle>{s.title}</CardTitle>
          <CardDescription>{s.description}</CardDescription>
        </div>
        <Link to="/admin/reports" className="shrink-0 text-sm text-muted-foreground hover:text-foreground">
          {s.reports}
        </Link>
      </CardHeader>
      <CardContent className="space-y-6">
        {data ? (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatTile
                label={s.revenue}
                value={money(data.totals.revenue)}
                hint={data.totals.discounts > 0 ? s.discounts(money(data.totals.discounts)) : undefined}
              />
              <StatTile label={s.orders} value={data.totals.orders} />
              <StatTile label={s.units} value={data.totals.units} />
              <StatTile label={s.profit} value={money(data.totals.profit)} />
            </div>
            {data.totals.unitsWithoutCost > 0 ? (
              <p className="flex items-start gap-2 text-sm text-destructive">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                {s.withoutCost(data.totals.unitsWithoutCost)}
              </p>
            ) : null}
            <PeriodBarChart
              data={data.periods.map((period) => ({ period: period.start, value: period.totals.revenue }))}
              groupBy={data.groupBy}
              label={s.chartLabel}
              formatValue={(value) => compactMoney.format(value)}
              axisWidth={64}
            />
            <div className="grid gap-6 sm:grid-cols-3">
              <TotalsList title={s.byChannel} rows={data.byChannel} labels={s.channels} />
              <TotalsList title={s.byPayment} rows={data.byPaymentMethod} labels={s.payments} />
              <div className="space-y-2">
                <h4 className="text-sm font-medium">{s.top}</h4>
                {top.data && top.data.length > 0 ? (
                  <ol className="space-y-1 text-sm">
                    {top.data.map((item) => (
                      <li key={`${item.type}-${item.id}`} className="flex justify-between gap-3">
                        <span className="truncate">{item.name}</span>
                        <span className="shrink-0 tabular-nums text-muted-foreground">{s.unitsCount(item.units)}</span>
                      </li>
                    ))}
                  </ol>
                ) : top.isPending ? null : (
                  <p className="text-sm text-muted-foreground">{s.topEmpty}</p>
                )}
              </div>
            </div>
          </>
        ) : (
          <BlockState isPending={summary.isPending} isError={summary.isError} />
        )}
      </CardContent>
    </Card>
  )
}

function ProductsBlock({ enabled }: { enabled: boolean }) {
  const dashboard = useProductsDashboard(enabled)
  const products = dashboard.data?.products
  const p = strings.admin.products

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle>{p.title}</CardTitle>
        <Link to="/admin/products" className="text-sm text-muted-foreground hover:text-foreground">
          {p.manage}
        </Link>
      </CardHeader>
      <CardContent className="space-y-4">
        {products ? (
          <>
            <div className="grid grid-cols-2 gap-3">
              <StatTile label={p.total} value={products.total} />
              <StatTile label={p.featured} value={products.featured} />
              <StatTile label={p.withoutPrice} value={products.withoutPrice} />
              <StatTile label={p.withoutImages} value={products.withoutImages} />
            </div>
            {products.byCategory.length > 0 ? (
              <div className="space-y-2">
                <h4 className="text-sm font-medium">{p.byCategory}</h4>
                <ul className="space-y-1 text-sm">
                  {products.byCategory.slice(0, 6).map((item) => (
                    <li key={item.category ?? ""} className="flex justify-between gap-3">
                      <span className="truncate text-muted-foreground">{item.category ?? p.noCategory}</span>
                      <span className="tabular-nums">{item.count}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </>
        ) : (
          <BlockState isPending={dashboard.isPending} isError={dashboard.isError} />
        )}
      </CardContent>
    </Card>
  )
}

function StockBlock({ enabled }: { enabled: boolean }) {
  // La misma consulta que el bloque de productos (una sola request al backend)
  const dashboard = useProductsDashboard(enabled)
  const stock = dashboard.data?.stock
  const st = strings.admin.stock

  return (
    <Card>
      <CardHeader>
        <CardTitle>{st.title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {stock ? (
          <>
            <div className="grid grid-cols-2 gap-3">
              <StatTile label={st.low} value={stock.low} />
              <StatTile label={st.outOfStock} value={stock.outOfStock} />
              <StatTile label={st.atCost} value={money(stock.inventoryAtCost)} />
              <StatTile label={st.atPrice} value={money(stock.inventoryAtPrice)} />
            </div>
            {stock.excludedWithoutCost > 0 || stock.excludedWithoutPrice > 0 ? (
              <p className="text-xs text-muted-foreground">
                {st.excluded(stock.excludedWithoutCost, stock.excludedWithoutPrice)}
              </p>
            ) : null}
            <div className="space-y-2">
              <h4 className="text-sm font-medium">{st.recent}</h4>
              {stock.recentMovements.length === 0 ? (
                <p className="text-sm text-muted-foreground">{st.recentEmpty}</p>
              ) : (
                <ul className="space-y-1 text-sm">
                  {stock.recentMovements.map((movement) => (
                    <li key={movement.id} className="flex items-baseline justify-between gap-3">
                      <span className="min-w-0 truncate">
                        {movement.productName}{" "}
                        <span className={movement.quantity < 0 ? "text-destructive" : "text-cyan-edge"}>
                          {movement.quantity > 0 ? `+${movement.quantity}` : movement.quantity}
                        </span>
                      </span>
                      <span className="shrink-0 text-xs text-muted-foreground">
                        {formatServerDateTime(movement.createdAt)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </>
        ) : (
          <BlockState isPending={dashboard.isPending} isError={dashboard.isError} />
        )}
      </CardContent>
    </Card>
  )
}

function UsersBlock({ range, enabled }: { range: ReturnType<typeof rangeParams>; enabled: boolean }) {
  const dashboard = useUsersDashboard(range, enabled)
  const data = dashboard.data
  const u = strings.admin.users

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle>{u.title}</CardTitle>
        <Link to="/admin/users" className="text-sm text-muted-foreground hover:text-foreground">
          {u.manage}
        </Link>
      </CardHeader>
      <CardContent className="space-y-4">
        {data ? (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
              <StatTile label={u.total} value={data.total} />
              <StatTile label={u.active(data.activeWindowDays)} value={data.active} />
              <StatTile label={strings.roles.administrador} value={data.byRole.ADMINISTRADOR ?? 0} />
              <StatTile label={strings.roles.empleado} value={data.byRole.EMPLEADO ?? 0} />
              <StatTile label={strings.roles.cliente} value={data.byRole.CLIENTE ?? 0} />
            </div>
            <div className="space-y-2">
              <h4 className="text-sm font-medium">{u.registrations}</h4>
              <PeriodBarChart
                data={data.registrations.periods.map((period) => ({ period: period.period, value: period.count }))}
                groupBy={data.registrations.groupBy}
                label={u.chartLabel}
              />
            </div>
          </>
        ) : (
          <BlockState isPending={dashboard.isPending} isError={dashboard.isError} />
        )}
      </CardContent>
    </Card>
  )
}
