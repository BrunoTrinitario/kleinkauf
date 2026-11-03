import { useCallback, useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { loginUrl, useAuth } from "@/lib/auth-context"
import { isSessionExpired } from "@/lib/api"
import { notifyError, type ErrorOverrides } from "@/lib/api-error"
import { SUPERADMIN_ROLE } from "@/lib/platform"

/*
Sesion de las paginas de la plataforma: manda al login si no hay un superadmin logueado.
El 401 (token vencido) lo maneja el cliente HTTP (lib/api.ts): cierra la sesion y vuelve al login.
ready = false mientras carga o redirige: la pagina no tiene que mostrar nada.
 */
export function usePlatformSession() {
  const navigate = useNavigate()
  const { user, isAuthenticated, isLoading } = useAuth()
  const isSuperadmin = !!user?.roles.some((role) => role.type === SUPERADMIN_ROLE)
  const ready = !isLoading && isAuthenticated && isSuperadmin

  useEffect(() => {
    if (!isLoading && (!isAuthenticated || !isSuperadmin)) {
      navigate(loginUrl())
    }
  }, [isAuthenticated, isLoading, isSuperadmin, navigate])

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
