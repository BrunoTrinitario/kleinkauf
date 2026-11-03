import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
import type { PeriodGroupBy } from "@/lib/types"
import { strings } from "@/lib/strings"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"

// "2026-10-05" es una fecha sin hora: se arma en hora local para que no se corra un dia
export function parsePeriod(period: string): Date {
  const [year, month, day] = period.split("-").map(Number)
  return new Date(year, month - 1, day)
}

const dayMonth = new Intl.DateTimeFormat("es-AR", { day: "2-digit", month: "2-digit" })
const monthYear = new Intl.DateTimeFormat("es-AR", { month: "short", year: "2-digit" })
const fullMonth = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" })
const fullDay = new Intl.DateTimeFormat("es-AR", { day: "numeric", month: "long", year: "numeric" })

// Etiqueta corta del eje
export function formatPeriodTick(period: string, groupBy: PeriodGroupBy): string {
  const date = parsePeriod(period)
  return groupBy === "MONTH" ? monthYear.format(date) : dayMonth.format(date)
}

// Etiqueta completa (tooltip y tabla)
export function formatPeriodLabel(period: string, groupBy: PeriodGroupBy): string {
  const date = parsePeriod(period)
  switch (groupBy) {
    case "MONTH":
      return fullMonth.format(date)
    case "WEEK":
      return strings.platformDashboard.weekOf(fullDay.format(date))
    default:
      return fullDay.format(date)
  }
}

interface PeriodBarChartProps {
  // period: primer dia del periodo (yyyy-MM-dd); vienen todos, tambien los que estan en cero
  data: { period: string; value: number }[]
  groupBy: PeriodGroupBy
  // Nombre de la serie en el tooltip
  label: string
  // Formato del valor en el eje y el tooltip (ej. pesos); sin el, enteros
  formatValue?: (value: number) => string
  // Ancho del eje Y: un monto en pesos necesita mas lugar que un contador
  axisWidth?: number
}

// Barras por periodo, una sola serie (el titulo de la card la nombra, no hace falta leyenda)
export function PeriodBarChart({ data, groupBy, label, formatValue, axisWidth = 40 }: PeriodBarChartProps) {
  const config = { value: { label, color: "var(--primary)" } } satisfies ChartConfig
  return (
    <ChartContainer config={config} className="aspect-auto h-56 w-full">
      <BarChart data={data} margin={{ top: 8, right: 4, left: -20, bottom: 0 }} barCategoryGap={2}>
        <CartesianGrid vertical={false} strokeOpacity={0.5} />
        <XAxis
          dataKey="period"
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          minTickGap={16}
          tickFormatter={(value: string) => formatPeriodTick(value, groupBy)}
        />
        <YAxis
          allowDecimals={false}
          tickLine={false}
          axisLine={false}
          width={axisWidth}
          tickFormatter={formatValue ? (value: number) => formatValue(value) : undefined}
        />
        <ChartTooltip
          cursor={{ fill: "var(--muted)" }}
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => formatPeriodLabel(String(payload?.[0]?.payload?.period ?? ""), groupBy)}
              formatter={
                formatValue
                  ? (value) => (
                      <span className="flex w-full justify-between gap-4">
                        <span className="text-muted-foreground">{label}</span>
                        <span className="font-mono font-medium tabular-nums">{formatValue(Number(value))}</span>
                      </span>
                    )
                  : undefined
              }
            />
          }
        />
        <Bar dataKey="value" fill="var(--color-value)" radius={[4, 4, 0, 0]} maxBarSize={32} />
      </BarChart>
    </ChartContainer>
  )
}

interface PlatformRegistrationsChartProps {
  periods: { period: string; count: number }[]
  groupBy: PeriodGroupBy
}

export function PlatformRegistrationsChart({ periods, groupBy }: PlatformRegistrationsChartProps) {
  return (
    <PeriodBarChart
      data={periods.map((item) => ({ period: item.period, value: item.count }))}
      groupBy={groupBy}
      label={strings.platformDashboard.seriesLabel}
    />
  )
}
