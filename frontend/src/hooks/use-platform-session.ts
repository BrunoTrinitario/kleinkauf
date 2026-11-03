import { useCallback } from "react"
import { useAuth } from "@/lib/auth-context"
import { isSessionExpired } from "@/lib/api"
import { notifyError, type ErrorOverrides } from "@/lib/api-error"
import { SUPERADMIN_ROLE } from "@/lib/platform"

/*
Sesion de las paginas de la plataforma. Que haya un superadmin lo controla la ruta (RequireSuperadmin en App.tsx)
y el 401 (token vencido) el cliente HTTP (lib/api.ts): cierra la sesion y vuelve al login.
ready = false mientras no hay un superadmin: las consultas esperan.
 */
export function usePlatformSession() {
  const { user, isLoading } = useAuth()
  const ready = !isLoading && !!user?.roles.some((role) => role.type === SUPERADMIN_ROLE)

  // true si era un 401 (la sesion ya se cerro); si no, muestra el error y devuelve false
  const handleError = useCallback((err: unknown, fallback: string, overrides?: ErrorOverrides) => {
    if (isSessionExpired(err)) {
      return true
    }
    notifyError(err, fallback, overrides)
    return false
  }, [])

  return { user, ready, handleError }
}
