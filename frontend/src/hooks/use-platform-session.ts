import { useCallback, useEffect, useMemo } from "react"
import { useNavigate } from "react-router-dom"
import axios from "axios"
import { toast } from "sonner"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { notifyError, type ErrorOverrides } from "@/lib/api-error"
import { SUPERADMIN_ROLE } from "@/lib/platform"

export const PLATFORM_API = `${import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"}/api/v1/platform`

/*
Sesion de las paginas de la plataforma: manda al login si no hay un superadmin logueado y
cierra la sesion con el primer 401 (la sesion se guarda sin validar: ver auth-context).
ready = false mientras carga o redirige: la pagina no tiene que mostrar nada.
 */
export function usePlatformSession() {
  const navigate = useNavigate()
  const { user, isAuthenticated, isLoading, logout } = useAuth()
  const isSuperadmin = !!user?.roles.some((role) => role.type === SUPERADMIN_ROLE)
  const ready = !isLoading && isAuthenticated && isSuperadmin

  useEffect(() => {
    if (!isLoading && (!isAuthenticated || !isSuperadmin)) {
      navigate("/login")
    }
  }, [isAuthenticated, isLoading, isSuperadmin, navigate])

  const headers = useMemo(() => ({ Authorization: `Bearer ${user?.token}` }), [user?.token])

  // true si era un 401 (la sesion ya se cerro); si no, muestra el error y devuelve false
  const handleError = useCallback(
    (err: unknown, fallback: string, overrides?: ErrorOverrides) => {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        toast.error(strings.errors.sessionExpired)
        logout()
        return true
      }
      notifyError(err, fallback, overrides)
      return false
    },
    [logout]
  )

  return { user, ready, headers, handleError }
}
