import { useState } from "react"
import { Link, useSearchParams } from "react-router-dom"
import axios from "axios"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { ChevronDown, Download, X } from "lucide-react"
import { exportAudit, observabilityKeys, useAudit, type AuditEvent, type AuditFilters } from "@/api/observability"
import { useLiveChannels } from "@/hooks/use-live-channels"
import { IS_PLATFORM } from "@/lib/platform"
import { strings } from "@/lib/strings"
import { cn } from "@/lib/utils"
import { FilterSelect } from "@/components/list-toolbar"
import { LiveStatusBadge } from "@/components/live-status"
import { PaginationControls } from "@/components/pagination-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"

const a = strings.audit
const dateTime = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "medium" })
const ACTIONS = Object.keys(a.actions)

// What is audited on each side (docs/features/audit.md#what-is-recorded)
const STORE_ENTITIES = [
  "Product",
  "ProductPricing",
  "ProductExport",
  "ProductImage",
  "ProductMetadata",
  "StockMovement",
  "Bundle",
  "BundleProduct",
  "Order",
  "PaymentReceipt",
  "SalesSettings",
  "User",
  "Role",
  "RolePermission",
]
const PLATFORM_ENTITIES = ["Tenant", "Superadmin", "User", "Role"]

const GRID = "md:grid-cols-[10rem_minmax(0,12rem)_9rem_minmax(0,1fr)_1.5rem]"

/*
Audit history (the store's or the platform's, depending on where it is opened). Filters go in the URL, as in the
orders list. What is new arrives over the WS (store.audit / platform.audit): the first page reloads by itself; the
others show how many new events there are.
 */
export function AuditLog({ enabled }: { enabled: boolean }) {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [newEvents, setNewEvents] = useState(0)
  const [exporting, setExporting] = useState(false)

  const filters: AuditFilters = {
    from: params.get("desde") ?? "",
    to: params.get("hasta") ?? "",
    userId: params.get("usuario") ?? "",
    entity: params.get("que") ?? "",
    entityId: params.get("id") ?? "",
    action: params.get("accion") ?? "",
    page: Math.max(0, Number(params.get("pagina") ?? 1) - 1) || 0,
  }
  const userName = params.get("nombre") ?? ""
  const hasFilters = !!(filters.from || filters.to || filters.userId || filters.entity || filters.entityId || filters.action)
  const entities = IS_PLATFORM ? PLATFORM_ENTITIES : STORE_ENTITIES

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

  const audit = useAudit(filters, enabled)

  const status = useLiveChannels(
    [IS_PLATFORM ? "platform.audit" : "store.audit"],
    () => {
      if (filters.page === 0) {
        queryClient.invalidateQueries({ queryKey: observabilityKeys.audit() })
      } else {
        setNewEvents((count) => count + 1)
      }
    },
    enabled
  )

  const showNew = () => {
    setNewEvents(0)
    update({ pagina: "" })
    queryClient.invalidateQueries({ queryKey: observabilityKeys.audit() })
  }

  const download = async () => {
    setExporting(true)
    try {
      await exportAudit(filters)
    } catch (error) {
      toast.error(axios.isAxiosError(error) && error.response?.status === 400 ? a.exportTooMany : a.exportError)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <DateField label={a.from} value={filters.from ?? ""} max={filters.to || undefined} onChange={(value) => update({ desde: value })} />
        <DateField label={a.to} value={filters.to ?? ""} min={filters.from || undefined} onChange={(value) => update({ hasta: value })} />
        <label className="grid gap-1 text-xs text-muted-foreground">
          {a.entityFilter}
          <FilterSelect value={filters.entity ?? ""} onChange={(value) => update({ que: value, id: "" })} label={a.entityFilter}>
            <option value="">{a.allEntities}</option>
            {entities.map((entity) => (
              <option key={entity} value={entity}>
                {a.entities[entity] ?? entity}
              </option>
            ))}
          </FilterSelect>
        </label>
        {IS_PLATFORM && filters.entity ? (
          <label className="grid gap-1 text-xs text-muted-foreground">
            {a.entityIdFilter}
            <Input
              value={filters.entityId ?? ""}
              placeholder={a.entityIdPlaceholder}
              onChange={(event) => update({ id: event.target.value.trim() })}
              className="h-9 w-24"
            />
          </label>
        ) : null}
        <label className="grid gap-1 text-xs text-muted-foreground">
          {a.actionFilter}
          <FilterSelect value={filters.action ?? ""} onChange={(value) => update({ accion: value })} label={a.actionFilter}>
            <option value="">{a.allActions}</option>
            {ACTIONS.map((action) => (
              <option key={action} value={action}>
                {a.actions[action]}
              </option>
            ))}
          </FilterSelect>
        </label>
        {filters.userId ? (
          <Badge variant="outline" className="h-9 gap-1 px-3">
            {a.byUser(userName || `#${filters.userId}`)}
            <button
              type="button"
              aria-label={a.clearUser}
              title={a.clearUser}
              onClick={() => update({ usuario: "", nombre: "" })}
              className="ml-1 rounded-sm text-muted-foreground hover:text-foreground"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </Badge>
        ) : null}
        {hasFilters ? (
          <Button variant="ghost" size="sm" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
            {a.clearFilters}
          </Button>
        ) : null}
        <div className="ml-auto flex items-center gap-3">
          <LiveStatusBadge status={status} />
          <Button variant="outline" size="sm" onClick={download} disabled={exporting}>
            <Download className="mr-2 h-4 w-4" />
            {exporting ? a.exporting : a.export}
          </Button>
        </div>
      </div>

      {newEvents > 0 ? (
        <div className="flex items-center justify-between gap-3 rounded-lg border border-primary/40 bg-card px-4 py-2 text-sm" role="status">
          <span>{a.newEvents(newEvents)}</span>
          <Button size="sm" variant="ghost" onClick={showNew}>
            {a.showNew}
          </Button>
        </div>
      ) : null}

      {audit.isPending ? (
        <p className="text-muted-foreground">{a.loading}</p>
      ) : audit.isError || !audit.data ? (
        <p className="text-destructive">{a.loadError}</p>
      ) : audit.data.items.length === 0 ? (
        <p className="text-muted-foreground">{hasFilters ? a.empty : a.emptyAll}</p>
      ) : (
        <>
          <Card className={cn("p-0 gap-0 overflow-hidden", audit.isFetching && "opacity-70")}>
            <div className={cn("hidden md:grid gap-4 border-b border-border px-4 py-2 text-xs uppercase tracking-wider text-muted-foreground", GRID)}>
              <span>{a.columns.date}</span>
              <span>{a.columns.who}</span>
              <span>{a.columns.action}</span>
              <span>{a.columns.what}</span>
              <span />
            </div>
            <ul className="divide-y divide-border">
              {audit.data.items.map((event) => (
                <AuditRow
                  key={event.id}
                  event={event}
                  onFilterUser={(userId, name) => update({ usuario: String(userId), nombre: name })}
                />
              ))}
            </ul>
          </Card>
          <PaginationControls
            page={audit.data.page}
            totalPages={audit.data.totalPages}
            disabled={audit.isFetching}
            onPageChange={(page) => update({ pagina: page > 0 ? String(page + 1) : "" })}
          />
        </>
      )}
    </div>
  )
}

function AuditRow({ event, onFilterUser }: { event: AuditEvent; onFilterUser: (userId: number, name: string) => void }) {
  const [open, setOpen] = useState(false)
  const changes = Object.entries(event.changes ?? {})
  const actor = event.actor
  const actorName = actor ? actor.name ?? (actor.userId ? `#${actor.userId}` : a.system) : a.selfService

  return (
    <li>
      <div className={cn("grid gap-1 px-4 py-3 md:items-center md:gap-4", GRID)}>
        <span className="text-sm text-muted-foreground tabular-nums">{dateTime.format(new Date(event.at))}</span>
        <span className="min-w-0 text-sm">
          {actor?.userId ? (
            <button
              type="button"
              onClick={() => onFilterUser(actor.userId as number, actorName)}
              title={a.filterByUser(actorName)}
              className="block max-w-full truncate text-left hover:underline"
            >
              {actorName}
            </button>
          ) : (
            <span className="block truncate text-muted-foreground">{actorName}</span>
          )}
          {actor?.role ? (
            <span className="block text-[11px] uppercase tracking-wider text-muted-foreground">{actor.role}</span>
          ) : null}
        </span>
        <span>
          <Badge variant={event.action === "DELETE" ? "destructive" : event.action === "READ" ? "outline" : "secondary"}>
            {a.actions[event.action] ?? event.action}
          </Badge>
        </span>
        <span className="min-w-0 text-sm">
          <span className="font-medium">{a.entities[event.entity] ?? event.entity}</span>
          {event.entityId ? <span className="ml-2 font-mono text-muted-foreground">{event.entityId}</span> : null}
          {!open && changes.length > 0 ? (
            <span className="block truncate text-muted-foreground">{changes.map(([field]) => field).join(", ")}</span>
          ) : null}
        </span>
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-label={a.columns.detail}
          className="justify-self-start rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground md:justify-self-end"
        >
          <ChevronDown className={cn("h-4 w-4 transition-transform", open && "rotate-180")} />
        </button>
      </div>
      {open ? (
        <div className="space-y-3 border-t border-border bg-muted/40 px-4 py-3 text-sm">
          {changes.length === 0 ? (
            <p className="text-muted-foreground">{a.noChanges}</p>
          ) : (
            <dl className="grid gap-x-4 gap-y-1 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)]">
              {changes.map(([field, change]) => (
                <div key={field} className="contents">
                  <dt className="truncate font-mono text-muted-foreground">{field}</dt>
                  <dd className="min-w-0 break-words">
                    <span className="text-muted-foreground line-through decoration-muted-foreground/50">
                      {change?.from ?? a.emptyValue}
                    </span>
                    <span className="mx-2 text-muted-foreground">→</span>
                    <span>{change?.to ?? a.emptyValue}</span>
                  </dd>
                </div>
              ))}
            </dl>
          )}
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted-foreground">
            {event.service ? <span>{a.service}: {event.service}</span> : null}
            {event.ip ? <span>{a.ip}: {event.ip}</span> : null}
            {event.traceId ? (
              <span>
                {a.trace}: <span className="font-mono">{event.traceId}</span>
              </span>
            ) : null}
            {!IS_PLATFORM && event.entity === "Order" && event.entityId ? (
              <Link to={`/admin/orders/${event.entityId}`} className="text-foreground hover:underline">
                {a.viewOrder}
              </Link>
            ) : null}
          </div>
        </div>
      ) : null}
    </li>
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
