import { useSyncExternalStore } from "react"
import axios from "axios"

/*
Status of the opened store: the gateway cuts off any request for a store that does not exist (404) or is
disabled (403), with its own text (see docs/backend/gateway.md). When that happens, the app shows a page
instead of the routes: no screen of that store can work.
 */
export type TenantStatus = "ok" | "not-found" | "disabled"

// The gateway's literal texts (ErrorMessages): a 404/403 from something else does not count
const NOT_FOUND = "tenant was not found"
const DISABLED = "tenant is disabled"

let status: TenantStatus = "ok"
const listeners = new Set<() => void>()

const setStatus = (next: TenantStatus) => {
  if (status === next) return
  status = next
  listeners.forEach((listener) => listener())
}

// "ok" if the error is not about the store (not even an HTTP error)
function tenantStatusOf(error: unknown): TenantStatus {
  if (!axios.isAxiosError(error) || !error.response) return "ok"
  const body = typeof error.response.data === "string" ? error.response.data.toLowerCase() : ""
  if (error.response.status === 404 && body.includes(NOT_FOUND)) return "not-found"
  if (error.response.status === 403 && body.includes(DISABLED)) return "disabled"
  return "ok"
}

// Called by the HTTP client on every error (only in stores: the platform has no store)
export function reportTenantError(error: unknown) {
  const next = tenantStatusOf(error)
  if (next !== "ok") {
    setStatus(next)
  }
}

// The store unavailable page already explains it: screens do not have to report it again
export function isTenantUnavailable(error: unknown): boolean {
  return tenantStatusOf(error) !== "ok"
}

export function useTenantStatus(): TenantStatus {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => status
  )
}
