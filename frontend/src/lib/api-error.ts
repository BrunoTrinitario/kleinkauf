import axios from "axios"
import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { isSessionExpired } from "@/lib/api"
import { IS_PLATFORM } from "@/lib/platform"
import { isTenantUnavailable } from "@/lib/tenant-status"

export interface ErrorOverrides {
  badRequest?: string
  unauthorized?: string
  forbidden?: string
  notFound?: string
  conflict?: string
}

const responseText = (error: unknown): string => {
  if (!axios.isAxiosError(error)) {
    return ""
  }
  const data = error.response?.data
  return typeof data === "string" ? data.toLowerCase() : ""
}

/**
 * Traduce un error de la API a un mensaje para el usuario.
 * `fallback` se usa cuando el estado no tiene un mensaje propio; `overrides`
 * deja que cada pantalla diga algo más específico para un estado.
 */
export function getErrorMessage(
  error: unknown,
  fallback: string,
  overrides: ErrorOverrides = {}
): string {
  if (!axios.isAxiosError(error)) {
    return fallback
  }

  if (!error.response) {
    return error.code === "ECONNABORTED" ? strings.errors.timeout : strings.errors.network
  }

  const text = responseText(error)
  if (text.includes("identifier is already in use")) {
    return strings.errors.identifierInUse
  }
  if (text.includes("identifier is reserved")) {
    return strings.errors.identifierReserved
  }
  if (text.includes("already in use")) {
    return strings.errors.emailInUse
  }

  switch (error.response.status) {
    case 400:
      return overrides.badRequest ?? fallback
    case 401:
      return overrides.unauthorized ?? strings.errors.sessionExpired
    case 403:
      if (text.includes("tenant") && text.includes("disabled")) {
        return strings.errors.tenantDisabled
      }
      return overrides.forbidden ?? strings.errors.forbidden
    case 404:
      if (text.includes("tenant")) {
        return strings.errors.tenantNotFound
      }
      return overrides.notFound ?? fallback
    case 409:
      return overrides.conflict ?? fallback
    case 413:
      return strings.errors.fileTooLarge
    case 429:
      return strings.errors.tooManyRequests
    case 502:
    case 503:
    case 504:
      return strings.errors.unavailable
    default:
      return error.response.status >= 500 ? strings.errors.server : fallback
  }
}

/**
 * Muestra el error como aviso (toast) y devuelve el mensaje.
 * La sesion vencida ya la avisa el cliente HTTP y la tienda no disponible tiene su pagina; el mismo mensaje no se
 * repite (ej. varios 429 seguidos).
 */
export function notifyError(
  error: unknown,
  fallback: string,
  overrides: ErrorOverrides = {}
): string {
  const message = getErrorMessage(error, fallback, overrides)
  if (!isSessionExpired(error) && !(!IS_PLATFORM && isTenantUnavailable(error))) {
    toast.error(message, { id: message })
  }
  return message
}
