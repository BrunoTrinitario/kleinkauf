import { useState } from "react"
import { Link } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"
import { Activity, ChevronDown, ScrollText, Server, TriangleAlert, Users } from "lucide-react"
import {
  observabilityKeys,
  usePlatformOverview,
  type BusinessEvent,
  type PlatformOverview,
  type ServiceHealth,
  type ServiceTraffic,
  type TrafficUpdate,
} from "@/api/observability"
import { platformKeys } from "@/api/platform"
import { useLiveChannels } from "@/hooks/use-live-channels"
import { usePlatformSession } from "@/hooks/use-platform-session"
import { strings } from "@/lib/strings"
import { cn } from "@/lib/utils"
import { PlatformHeader } from "@/components/platform-header"
import { PageHeader } from "@/components/page-header"
import { LiveStatusBadge, formatRate, timeAgo } from "@/components/live-status"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const h = strings.platformHealth
const CHANNELS = ["platform.health", "platform.traffic", "platform.business"]
// 60 lecturas de 10 s: los ultimos 10 minutos (el backend no guarda historial)
const HISTORY_MAX = 60
const STATUS_DOT: Record<string, string> = { UP: "bg-emerald-500", DEGRADED: "bg-amber-500", DOWN: "bg-red-500" }
const clock = new Intl.DateTimeFormat("es-AR", { timeStyle: "medium" })

interface Point {
  at: string
  requests: number
  errors: number
}

const sum = (traffic: ServiceTraffic[], pick: (row: ServiceTraffic) => number | null) =>
  traffic.reduce((total, row) => total + (pick(row) ?? 0), 0)
const hasRates = (traffic: ServiceTraffic[]) => traffic.some((row) => row.requestsPerSecond !== null)

/*
Estado de la plataforma (solo SUPERADMIN): salud de los servicios, trafico y recursos, conectados y movimientos de
tiendas. Al abrir se pide el overview y despues lo actualiza el WS (platform.health cuando cambia algo,
platform.traffic cada 10 s). El grafico junta las lecturas desde que se abrio la pagina.
 */
export default function PlatformHealthPage() {
  const { ready } = usePlatformSession()
  const queryClient = useQueryClient()
  const overview = usePlatformOverview(ready)
  const [history, setHistory] = useState<Point[]>([])
  const [business, setBusiness] = useState<BusinessEvent[]>([])

  const status = useLiveChannels(
    CHANNELS,
    (channel, data) => {
      if (channel === "platform.health") {
        const services = data as ServiceHealth[]
        queryClient.setQueryData<PlatformOverview>(observabilityKeys.overview(), (current) =>
          current ? { ...current, services } : current
        )
      } else if (channel === "platform.traffic") {
        const update = data as TrafficUpdate
        queryClient.setQueryData<PlatformOverview>(observabilityKeys.overview(), (current) =>
          current ? { ...current, ...update } : current
        )
        if (hasRates(update.traffic)) {
          setHistory((current) =>
            [
              ...current,
              {
                at: update.at,
                requests: sum(update.traffic, (row) => row.requestsPerSecond),
                errors: sum(update.traffic, (row) => row.errors5xxPerSecond),
              },
            ].slice(-HISTORY_MAX)
          )
        }
      } else if (channel === "platform.business") {
        const event = data as BusinessEvent
        setBusiness((current) => [event, ...current].slice(0, 20))
        toast(h.business[event.type]?.(event.identifier) ?? event.type)
        queryClient.invalidateQueries({ queryKey: platformKeys.all })
      }
    },
    ready
  )

  if (!ready) {
    return null
  }

  const data = overview.data
  const services = data?.services ?? []
  const traffic = data?.traffic ?? []
  const up = services.filter((service) => service.status === "UP").length
  const degraded = services.filter((service) => service.status === "DEGRADED").length
  const down = services.filter((service) => service.status === "DOWN").length
  const rates = hasRates(traffic)
  const connected = data?.connectedUsers

  return (
    <div className="min-h-screen bg-background">
      <PlatformHeader />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader
          title={h.title}
          highlight={h.highlight}
          subtitle={h.subtitle}
          action={
            <div className="flex flex-wrap items-center gap-3">
              <LiveStatusBadge status={status} />
              <Button variant="outline" asChild>
                <Link to="/auditoria">
                  <ScrollText className="mr-2 h-4 w-4" />
                  {h.auditLink}
                </Link>
              </Button>
            </div>
          }
        />

        {overview.isError ? <p className="text-destructive">{h.loadError}</p> : null}
        {data && !data.at ? <p className="text-muted-foreground">{h.noReading}</p> : null}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <Tile
            label={h.services}
            value={data?.at ? `${up}/${services.length}` : undefined}
            icon={Server}
            hint={data?.at ? h.servicesHint(degraded, down) : undefined}
            alert={down + degraded > 0}
          />
          <Tile
            label={h.requests}
            value={rates ? strings.storeActivity.perSecond(formatRate(sum(traffic, (row) => row.requestsPerSecond))) : undefined}
            icon={Activity}
          />
          <Tile
            label={h.errors5xx}
            value={rates ? strings.storeActivity.perSecond(formatRate(sum(traffic, (row) => row.errors5xxPerSecond))) : undefined}
            icon={TriangleAlert}
            hint={h.errors5xxHint}
            alert={rates && sum(traffic, (row) => row.errors5xxPerSecond) > 0}
          />
          <Tile
            label={h.connected}
            value={connected?.total}
            icon={Users}
            hint={connected ? h.connectedHint(connected.byTenant.length) : undefined}
          />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>{h.healthTitle}</CardTitle>
            <CardDescription>{h.healthDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {services.map((service) => (
                <ServiceCard key={service.name} service={service} />
              ))}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{h.chartTitle}</CardTitle>
            <CardDescription>{h.chartDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            {history.length < 2 ? (
              <p className="flex h-56 items-center justify-center text-sm text-muted-foreground">{h.chartWaiting}</p>
            ) : (
              <TrafficChart points={history} />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{h.trafficTitle}</CardTitle>
            <CardDescription>{h.trafficDescription}</CardDescription>
          </CardHeader>
          <CardContent className="px-3 sm:px-6">
            <TrafficTable traffic={traffic} />
          </CardContent>
        </Card>

        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>{h.connectedTitle}</CardTitle>
              <CardDescription>{h.connectedDescription}</CardDescription>
            </CardHeader>
            <CardContent>
              {!connected || connected.byTenant.length === 0 ? (
                <p className="text-sm text-muted-foreground">{h.connectedEmpty}</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {connected.byTenant.map((row) => (
                    <li key={row.tenant} className="flex justify-between gap-3">
                      <span className="truncate">{row.tenant === "public" ? h.platformTenant : row.tenant}</span>
                      <span className="tabular-nums text-muted-foreground">{row.users}</span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>{h.businessTitle}</CardTitle>
              <CardDescription>{h.businessDescription}</CardDescription>
            </CardHeader>
            <CardContent>
              {business.length === 0 ? (
                <p className="text-sm text-muted-foreground">{h.businessEmpty}</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {business.map((event) => (
                    <li key={`${event.type}-${event.id}-${event.at}`} className="flex justify-between gap-3">
                      <span className="truncate">{h.business[event.type]?.(event.identifier) ?? event.type}</span>
                      <span className="shrink-0 text-muted-foreground">{timeAgo(event.at)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  )
}

function Tile({
  label,
  value,
  icon: Icon,
  hint,
  alert,
}: {
  label: string
  value: string | number | undefined
  icon: typeof Users
  hint?: string
  alert?: boolean
}) {
  return (
    <Card className={cn("gap-2", alert && "border-destructive/40")}>
      <CardHeader className="flex flex-row items-center justify-between pb-0">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className={cn("h-4 w-4 text-muted-foreground", alert && "text-destructive")} />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-medium tabular-nums">{value ?? "-"}</div>
        {hint ? <p className="text-xs text-muted-foreground mt-1">{hint}</p> : null}
      </CardContent>
    </Card>
  )
}

function ServiceCard({ service }: { service: ServiceHealth }) {
  const [open, setOpen] = useState(service.status !== "UP")
  return (
    <div className={cn("rounded-lg border border-border p-3", service.status !== "UP" && "border-destructive/40")}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="flex w-full items-center justify-between gap-3 text-left"
      >
        <span className="min-w-0">
          <span className="flex items-center gap-2">
            <span className={cn("h-2.5 w-2.5 shrink-0 rounded-full", STATUS_DOT[service.status] ?? "bg-muted-foreground")} />
            <span className="truncate text-sm font-medium">{service.name.replace(/-microservice$|-service$/, "")}</span>
          </span>
          <span className="mt-1 block text-xs text-muted-foreground">
            {h.status[service.status] ?? service.status} ·{" "}
            {service.instances.length === 0 ? h.noInstances : h.instances(service.instances.length)}
          </span>
        </span>
        <ChevronDown className={cn("h-4 w-4 shrink-0 text-muted-foreground transition-transform", open && "rotate-180")} />
      </button>
      {open ? (
        <div className="mt-3 space-y-3 border-t border-border pt-3 text-xs">
          {service.instances.map((instance) => (
            <div key={instance.id} className="space-y-1">
              <p className="flex items-center gap-2 font-mono text-muted-foreground">
                <span className={cn("h-2 w-2 shrink-0 rounded-full", STATUS_DOT[instance.status] ?? "bg-muted-foreground")} />
                <span className="truncate" title={instance.id}>
                  {instance.id}
                </span>
              </p>
              {instance.error ? <p className="text-destructive">{instance.error}</p> : null}
              <div className="flex flex-wrap gap-1.5">
                {Object.entries(instance.components ?? {}).map(([component, state]) => (
                  <span
                    key={component}
                    className={cn(
                      "rounded border px-1.5 py-0.5",
                      state === "UP" ? "border-border text-muted-foreground" : "border-destructive/50 text-destructive"
                    )}
                  >
                    {component}
                  </span>
                ))}
              </div>
            </div>
          ))}
          {service.openCircuitBreakers.length > 0 ? (
            <p className="text-destructive">
              {h.openBreakers}: {service.openCircuitBreakers.join(", ")}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

const ms = (value: number | null) => (value === null ? "-" : `${Math.round(value)} ms`)
const mb = (value: number | null) => (value === null ? "-" : `${Math.round(value / 1024 / 1024)} MB`)

function TrafficTable({ traffic }: { traffic: ServiceTraffic[] }) {
  const c = h.columns
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{c.service}</TableHead>
          <TableHead className="text-right">{c.requests}</TableHead>
          <TableHead className="text-right">{c.errors4xx}</TableHead>
          <TableHead className="text-right">{c.errors5xx}</TableHead>
          <TableHead className="text-right">{c.rateLimited}</TableHead>
          <TableHead className="text-right">{c.p95}</TableHead>
          <TableHead className="text-right">{c.p99}</TableHead>
          <TableHead className="text-right">{c.heap}</TableHead>
          <TableHead className="text-right">{c.cpu}</TableHead>
          <TableHead className="text-right">{c.db}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {traffic.map((row) => (
          <TableRow key={row.name}>
            <TableCell className="font-medium">{row.name.replace(/-microservice$|-service$/, "")}</TableCell>
            <TableCell className="text-right tabular-nums">{formatRate(row.requestsPerSecond)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatRate(row.errors4xxPerSecond)}</TableCell>
            <TableCell className={cn("text-right tabular-nums", (row.errors5xxPerSecond ?? 0) > 0 && "text-destructive font-medium")}>
              {formatRate(row.errors5xxPerSecond)}
            </TableCell>
            <TableCell className="text-right tabular-nums">{formatRate(row.rateLimitedPerSecond)}</TableCell>
            <TableCell className="text-right tabular-nums">{ms(row.p95Ms)}</TableCell>
            <TableCell className="text-right tabular-nums">{ms(row.p99Ms)}</TableCell>
            <TableCell className="text-right tabular-nums text-muted-foreground">
              {mb(row.heapUsedBytes)}
              {row.heapMaxBytes ? ` / ${mb(row.heapMaxBytes)}` : ""}
            </TableCell>
            <TableCell className="text-right tabular-nums">{row.cpu === null ? "-" : `${Math.round(row.cpu * 100)}%`}</TableCell>
            <TableCell className="text-right tabular-nums text-muted-foreground">
              {row.dbConnectionsActive === null ? "-" : Math.round(row.dbConnectionsActive)}
              {row.dbConnectionsMax ? ` / ${Math.round(row.dbConnectionsMax)}` : ""}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function TrafficChart({ points }: { points: Point[] }) {
  const config = {
    requests: { label: h.chartRequests, color: "var(--primary)" },
    errors: { label: h.chartErrors, color: "var(--destructive)" },
  } satisfies ChartConfig
  return (
    <ChartContainer config={config} className="aspect-auto h-56 w-full">
      <LineChart data={points} margin={{ top: 8, right: 4, left: -20, bottom: 0 }}>
        <CartesianGrid vertical={false} strokeOpacity={0.5} />
        <XAxis
          dataKey="at"
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          minTickGap={32}
          tickFormatter={(value: string) => clock.format(new Date(value))}
        />
        <YAxis tickLine={false} axisLine={false} width={40} tickFormatter={(value: number) => formatRate(value)} />
        <ChartTooltip
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => clock.format(new Date(String(payload?.[0]?.payload?.at ?? "")))}
            />
          }
        />
        <Line dataKey="requests" type="monotone" stroke="var(--color-requests)" strokeWidth={2} dot={false} isAnimationActive={false} />
        <Line dataKey="errors" type="monotone" stroke="var(--color-errors)" strokeWidth={2} dot={false} isAnimationActive={false} />
      </LineChart>
    </ChartContainer>
  )
}
