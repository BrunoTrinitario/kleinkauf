import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { api, PLATFORM_PATH } from "@/lib/api"
import { downloadCsv } from "@/lib/download"
import { IS_PLATFORM } from "@/lib/platform"
import type { Page } from "@/lib/types"

/*
Observability and audit (kleinkauf-observability, docs/backend/observability.md).
- Store (/api/v1/observability, ADMINISTRADOR only): the store's audit, today's usage and connected users.
- Platform (/api/v1/platform/observability, SUPERADMIN only): health, traffic and the platform's audit.
The past is loaded over REST and what is new arrives over the WebSocket (hooks/use-live-channels.ts).
 */

// --- Audit ---

export type AuditAction =
  | "CREATE"
  | "UPDATE"
  | "DELETE"
  | "READ"
  | "STATUS_CHANGE"
  | "IMPORT"
  | "ACTIVATE"
  | "DEACTIVATE"

export interface AuditEvent {
  id: string
  tenant: string
  at: string
  service: string | null
  // null: self-service store sign-up (no user)
  actor: { userId: number | null; name: string | null; role: string | null } | null
  ip: string | null
  traceId: string | null
  action: AuditAction
  entity: string
  entityId: string | null
  changes: Record<string, { from: string | null; to: string | null }>
}

// from and to: days (yyyy-MM-dd) in the viewer's time; sent as the start of that day with its zone
export interface AuditFilters {
  from?: string
  to?: string
  userId?: string
  entity?: string
  entityId?: string
  action?: string
  page?: number
}

const AUDIT_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/observability/audit` : "/observability/store/audit"

function auditParams(filters: AuditFilters) {
  const startOf = (day: string) => new Date(`${day}T00:00:00`)
  const nextDay = (day: string) => {
    const date = startOf(day)
    date.setDate(date.getDate() + 1)
    return date
  }
  return {
    from: filters.from ? startOf(filters.from).toISOString() : undefined,
    // to is exclusive: the whole day lasts until the start of the next one
    to: filters.to ? nextDay(filters.to).toISOString() : undefined,
    userId: filters.userId || undefined,
    entity: filters.entity || undefined,
    entityId: IS_PLATFORM ? filters.entityId || undefined : undefined,
    action: filters.action || undefined,
  }
}

export const observabilityKeys = {
  all: ["observability"] as const,
  audit: () => [...observabilityKeys.all, "audit"] as const,
  auditList: (filters: AuditFilters) => [...observabilityKeys.audit(), filters] as const,
  storeUsage: () => [...observabilityKeys.all, "store-usage"] as const,
  storeUsers: () => [...observabilityKeys.all, "store-users"] as const,
  overview: () => [...observabilityKeys.all, "overview"] as const,
}

export const AUDIT_PAGE_SIZE = 20

export function useAudit(filters: AuditFilters, enabled = true) {
  return useQuery({
    queryKey: observabilityKeys.auditList(filters),
    queryFn: () =>
      api
        .get<Page<AuditEvent>>(AUDIT_PATH, {
          params: { ...auditParams(filters), page: filters.page ?? 0, size: AUDIT_PAGE_SIZE },
        })
        .then((response) => response.data),
    enabled,
    placeholderData: keepPreviousData,
  })
}

// The backend builds the CSV (up to 50,000 events; with more it answers 400 and the dates have to be narrowed)
export const exportAudit = (filters: AuditFilters) =>
  downloadCsv(`${AUDIT_PATH}/export`, "auditoria.csv", auditParams(filters))

// --- Store: today's usage and connected users ---

export interface StoreUsage {
  at: string | null
  date: string
  catalogVisits: number
  topProducts: { slug: string; views: number }[]
  // null until the collector's second reading
  traffic: { requestsPerSecond: number; errors4xxPerSecond: number; errors5xxPerSecond: number } | null
}

// total and users: logged in (customers too). visitors: anonymous visitors, only how many
export interface StoreUsers {
  total: number
  users: { userId: number; name: string | null; lastActivity: string }[]
  visitors: number
}

export function useStoreUsage(enabled = true) {
  return useQuery({
    queryKey: observabilityKeys.storeUsage(),
    queryFn: () => api.get<StoreUsage>("/observability/store/usage").then((response) => response.data),
    enabled,
  })
}

export function useStoreUsers(enabled = true) {
  return useQuery({
    queryKey: observabilityKeys.storeUsers(),
    queryFn: () => api.get<StoreUsers>("/observability/store/users").then((response) => response.data),
    enabled,
  })
}

// What store.activity sends: { type, at, ...data }
export type StoreActivity =
  | {
      type: "order.created" | "order.receipt-uploaded"
      at: string
      number: string
      channel: "ONLINE" | "LOCAL"
      status: string
      total: number
      buyerName: string | null
    }
  | {
      type: "stock.movement"
      at: string
      productId: number
      productName: string
      movementType: string
      quantity: number
      stockAfter: number
      reason: string | null
      userId: number | null
    }
  | { type: "stock.low"; at: string; productId: number; productName: string; stock: number; minStock: number }

// --- Platform: health and traffic ---

export type ServiceStatus = "UP" | "DEGRADED" | "DOWN"

export interface ServiceHealth {
  name: string
  status: ServiceStatus
  instances: { id: string; status: string; components: Record<string, string>; error: string | null }[]
  openCircuitBreakers: string[]
}

// Rates are null until the second reading; percentiles, when there were no requests
export interface ServiceTraffic {
  name: string
  instances: number
  requestsPerSecond: number | null
  errors5xxPerSecond: number | null
  errors4xxPerSecond: number | null
  rateLimitedPerSecond: number | null
  p50Ms: number | null
  p95Ms: number | null
  p99Ms: number | null
  heapUsedBytes: number | null
  heapMaxBytes: number | null
  cpu: number | null
  threads: number | null
  dbConnectionsActive: number | null
  dbConnectionsMax: number | null
}

// total: logged in; visitors: anonymous visitors of the stores
export interface ConnectedUsers {
  total: number
  visitors: number
  byTenant: { tenant: string; users: number; visitors: number }[]
}

// What platform.traffic sends on each reading (10 s)
export interface TrafficUpdate {
  at: string
  traffic: ServiceTraffic[]
  connectedUsers: ConnectedUsers | null
}

export interface PlatformOverview extends TrafficUpdate {
  // null: the collector has not made any reading yet
  at: string
  services: ServiceHealth[]
}

export interface BusinessEvent {
  type: "tenant.created" | "tenant.enabled" | "tenant.disabled" | "tenant.deleted"
  at: string
  id: number
  identifier: string
  schema: string
}

export function usePlatformOverview(enabled = true) {
  return useQuery({
    queryKey: observabilityKeys.overview(),
    queryFn: () =>
      api.get<PlatformOverview>(`${PLATFORM_PATH}/observability/overview`).then((response) => response.data),
    enabled,
  })
}
