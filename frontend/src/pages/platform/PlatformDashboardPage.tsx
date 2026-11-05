import { useMemo, useState } from "react"
import { usePlatformDashboard, usePlatformProductsUsage, usePlatformUsersUsage } from "@/api/platform"
import { strings } from "@/lib/strings"
import type { TenantUsersUsage } from "@/lib/types"
import { rangeParams, type RangeKey } from "@/lib/periods"
import { usePlatformSession } from "@/hooks/use-platform-session"
import { PageHeader } from "@/components/page-header"
import { FilterSelect } from "@/components/list-toolbar"
import { PlatformHeader } from "@/components/platform-header"
import { PlatformRegistrationsChart, formatPeriodLabel } from "@/components/platform-registrations-chart"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Ban, CircleCheck, Store, Trash2 } from "lucide-react"

type UsageSort = "products" | "users" | "activeUsers" | "lastActivity" | "createdAt"

// Usage table row: the users data plus the products data (null if products did not answer)
interface UsageRow extends TenantUsersUsage {
  products: number | null
  bundles: number | null
}

const dateTime = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" })
const date = new Intl.DateTimeFormat("es-AR", { dateStyle: "short" })

// Descending; no value (null) last; ties by identifier
function compareUsage(sort: UsageSort) {
  const value = (row: UsageRow): number | null => {
    switch (sort) {
      case "products":
        return row.products
      case "users":
        return row.users
      case "activeUsers":
        return row.activeUsers
      case "lastActivity":
        return row.lastActivity ? new Date(row.lastActivity).getTime() : null
      case "createdAt":
        return new Date(row.createdAt).getTime()
    }
  }
  return (a: UsageRow, b: UsageRow) => {
    const va = value(a)
    const vb = value(b)
    if (va !== vb) {
      if (va === null) return 1
      if (vb === null) return -1
      return vb - va
    }
    return a.identifier.localeCompare(b.identifier)
  }
}

export default function PlatformDashboardPage() {
  const { ready } = usePlatformSession()
  const [rangeKey, setRangeKey] = useState<RangeKey>("days")
  const [usageSort, setUsageSort] = useState<UsageSort>("products")
  const dashboard = usePlatformDashboard(rangeParams(rangeKey), ready).data ?? null

  // Each service builds its part: if products fails, the table still shows without products or bundles
  // (the users error is reported; the products one only shows in the table)
  const usersUsageQuery = usePlatformUsersUsage(ready)
  const productsUsageQuery = usePlatformProductsUsage(ready)
  const usersUsage = usersUsageQuery.data ?? (usersUsageQuery.isError ? [] : null)
  const productsUsage = productsUsageQuery.data ?? null
  const productsFailed = productsUsageQuery.isError

  const usageRows = useMemo<UsageRow[]>(() => {
    const bySchema = new Map((productsUsage ?? []).map((usage) => [usage.schema, usage]))
    return (usersUsage ?? [])
      .map((usage) => {
        const products = bySchema.get(usage.schema)
        // Without a schema in products (the store was never opened) it counts as 0
        return {
          ...usage,
          products: productsFailed ? null : (products?.products ?? 0),
          bundles: productsFailed ? null : (products?.bundles ?? 0),
        }
      })
      .sort(compareUsage(usageSort))
  }, [usersUsage, productsUsage, productsFailed, usageSort])

  if (!ready) {
    return null
  }

  const tenants = dashboard?.tenants
  const registrations = dashboard?.registrations
  const registrationsTotal = registrations?.periods.reduce((sum, period) => sum + period.count, 0) ?? 0
  const tiles = [
    { label: strings.platformDashboard.totalTenants, value: tenants?.total, icon: Store, hint: strings.platformDashboard.totalHint },
    { label: strings.platformDashboard.enabled, value: tenants?.enabled, icon: CircleCheck },
    { label: strings.platformDashboard.disabled, value: tenants?.disabled, icon: Ban },
    { label: strings.platformDashboard.deleted, value: tenants?.deleted, icon: Trash2 },
  ]

  return (
    <div className="min-h-screen bg-background">
      <PlatformHeader />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader
          title={strings.platformDashboard.title}
          highlight={strings.platformDashboard.highlight}
          subtitle={strings.platformDashboard.subtitle}
        />

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {tiles.map((tile) => (
            <Card key={tile.label} className="gap-2">
              <CardHeader className="flex flex-row items-center justify-between pb-0">
                <CardTitle className="text-sm font-medium text-muted-foreground">{tile.label}</CardTitle>
                <tile.icon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-medium">{tile.value ?? "-"}</div>
                {tile.hint ? <p className="text-xs text-muted-foreground mt-1">{tile.hint}</p> : null}
              </CardContent>
            </Card>
          ))}
        </div>

        <Card>
          <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="space-y-1.5">
              <CardTitle>{strings.platformDashboard.registrationsTitle}</CardTitle>
              <CardDescription>
                {registrations ? `${strings.platformDashboard.registrationsTotal(registrationsTotal)}. ` : ""}
                {strings.platformDashboard.registrationsDescription}
              </CardDescription>
            </div>
            <FilterSelect
              value={rangeKey}
              onChange={(value) => setRangeKey(value as RangeKey)}
              label={strings.platformDashboard.rangeLabel}
            >
              <option value="days">{strings.platformDashboard.ranges.days}</option>
              <option value="weeks">{strings.platformDashboard.ranges.weeks}</option>
              <option value="months">{strings.platformDashboard.ranges.months}</option>
            </FilterSelect>
          </CardHeader>
          <CardContent className="space-y-4">
            {registrations ? (
              <>
                <PlatformRegistrationsChart periods={registrations.periods} groupBy={registrations.groupBy} />
                <details className="text-sm">
                  <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
                    {strings.platformDashboard.showTable}
                  </summary>
                  <Table className="mt-2">
                    <TableHeader>
                      <TableRow>
                        <TableHead>{strings.platformDashboard.periodColumn}</TableHead>
                        <TableHead className="text-right">{strings.platformDashboard.countColumn}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {registrations.periods.map((period) => (
                        <TableRow key={period.period}>
                          <TableCell>{formatPeriodLabel(period.period, registrations.groupBy)}</TableCell>
                          <TableCell className="text-right tabular-nums">{period.count}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </details>
              </>
            ) : (
              <p className="text-sm text-muted-foreground h-56">{strings.platformDashboard.loading}</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="space-y-1.5">
              <CardTitle>{strings.platformDashboard.usageTitle}</CardTitle>
              <CardDescription>{strings.platformDashboard.usageDescription}</CardDescription>
            </div>
            <FilterSelect
              value={usageSort}
              onChange={(value) => setUsageSort(value as UsageSort)}
              label={strings.platformDashboard.sortLabel}
            >
              {(Object.keys(strings.platformDashboard.sorts) as UsageSort[]).map((key) => (
                <option key={key} value={key}>
                  {strings.platformDashboard.sortLabel}: {strings.platformDashboard.sorts[key]}
                </option>
              ))}
            </FilterSelect>
          </CardHeader>
          <CardContent className="space-y-3 px-3 sm:px-6">
            {productsFailed ? (
              <p className="text-sm text-muted-foreground">{strings.platformDashboard.productsUnavailable}</p>
            ) : null}
            {usersUsage === null ? (
              <p className="text-sm text-muted-foreground">{strings.platformDashboard.loading}</p>
            ) : usageRows.length === 0 ? (
              <p className="text-sm text-muted-foreground">{strings.platformDashboard.emptyUsage}</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{strings.platformDashboard.store}</TableHead>
                    <TableHead className="text-right">{strings.platformDashboard.products}</TableHead>
                    <TableHead className="text-right">{strings.platformDashboard.bundles}</TableHead>
                    <TableHead className="text-right">{strings.platformDashboard.users}</TableHead>
                    <TableHead className="text-right">{strings.platformDashboard.activeUsers}</TableHead>
                    <TableHead>{strings.platformDashboard.lastActivity}</TableHead>
                    <TableHead>{strings.platformDashboard.createdAt}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {usageRows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="font-medium">
                        <span className="flex items-center gap-2">
                          {row.identifier}
                          {!row.enabled ? (
                            <Badge className="bg-muted text-muted-foreground">{strings.platformDashboard.disabledBadge}</Badge>
                          ) : null}
                        </span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{row.products ?? "-"}</TableCell>
                      <TableCell className="text-right tabular-nums">{row.bundles ?? "-"}</TableCell>
                      <TableCell className="text-right tabular-nums">{row.users}</TableCell>
                      <TableCell className="text-right tabular-nums">{row.activeUsers}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {row.lastActivity ? dateTime.format(new Date(row.lastActivity)) : strings.platformDashboard.never}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{date.format(new Date(row.createdAt))}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
