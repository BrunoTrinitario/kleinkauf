import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
import type { PeriodGroupBy } from "@/lib/types"
import { strings } from "@/lib/strings"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"

// "2026-10-05" is a date without a time: it is built in local time so it does not shift by a day
export function parsePeriod(period: string): Date {
  const [year, month, day] = period.split("-").map(Number)
  return new Date(year, month - 1, day)
}

const dayMonth = new Intl.DateTimeFormat("es-AR", { day: "2-digit", month: "2-digit" })
const monthYear = new Intl.DateTimeFormat("es-AR", { month: "short", year: "2-digit" })
const fullMonth = new Intl.DateTimeFormat("es-AR", { month: "long", year: "numeric" })
const fullDay = new Intl.DateTimeFormat("es-AR", { day: "numeric", month: "long", year: "numeric" })

// Short axis label
export function formatPeriodTick(period: string, groupBy: PeriodGroupBy): string {
  const date = parsePeriod(period)
  return groupBy === "MONTH" ? monthYear.format(date) : dayMonth.format(date)
}

// Full label (tooltip and table)
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
  // period: first day of the period (yyyy-MM-dd); all of them come, including those at zero
  data: { period: string; value: number }[]
  groupBy: PeriodGroupBy
  // Series name in the tooltip
  label: string
  // Value format on the axis and the tooltip (e.g. pesos); without it, integers
  formatValue?: (value: number) => string
  // Y axis width: an amount in pesos needs more room than a counter
  axisWidth?: number
}

// Bars per period, a single series (the card title names it, no legend needed)
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
