import { useSyncExternalStore } from "react"
import axios from "axios"

/*
Estado de la tienda que se abrio: el gateway corta cualquier request de una tienda que no existe (404) o que esta
deshabilitada (403), con un texto propio (ver docs/backend/gateway.md). Cuando pasa, la app muestra una pagina
en lugar de las rutas: ninguna pantalla de esa tienda puede andar.
 */
export type TenantStatus = "ok" | "not-found" | "disabled"

// Los textos literales del gateway (ErrorMessages): un 404/403 de otra cosa no cuenta
const NOT_FOUND = "tenant was not found"
const DISABLED = "tenant is disabled"

let status: TenantStatus = "ok"
const listeners = new Set<() => void>()

const setStatus = (next: TenantStatus) => {
  if (status === next) return
  status = next
  listeners.forEach((listener) => listener())
}

// "ok" si el error no es de la tienda (ni siquiera un error HTTP)
function tenantStatusOf(error: unknown): TenantStatus {
  if (!axios.isAxiosError(error) || !error.response) return "ok"
  const body = typeof error.response.data === "string" ? error.response.data.toLowerCase() : ""
  if (error.response.status === 404 && body.includes(NOT_FOUND)) return "not-found"
  if (error.response.status === 403 && body.includes(DISABLED)) return "disabled"
  return "ok"
}

// Lo llama el cliente HTTP con cada error (solo en las tiendas: la plataforma no tiene tienda)
export function reportTenantError(error: unknown) {
  const next = tenantStatusOf(error)
  if (next !== "ok") {
    setStatus(next)
  }
}

// La pagina de tienda no disponible ya lo explica: las pantallas no tienen que avisarlo otra vez
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
