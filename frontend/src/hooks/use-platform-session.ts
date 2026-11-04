import { useCallback } from "react"
import { useAuth } from "@/lib/auth-context"
import { isSessionExpired } from "@/lib/api"
import { notifyError, type ErrorOverrides } from "@/lib/api-error"
import { SUPERADMIN_ROLE } from "@/lib/platform"

/*
Session of the platform pages. Whether there is a superadmin is checked by the route (RequireSuperadmin in App.tsx)
and the 401 (expired token) by the HTTP client (lib/api.ts): it closes the session and goes back to login.
ready = false while there is no superadmin: the queries wait.
 */
export function usePlatformSession() {
  const { user, isLoading } = useAuth()
  const ready = !isLoading && !!user?.roles.some((role) => role.type === SUPERADMIN_ROLE)

  // true if it was a 401 (the session is already closed); otherwise, shows the error and returns false
  const handleError = useCallback((err: unknown, fallback: string, overrides?: ErrorOverrides) => {
    if (isSessionExpired(err)) {
      return true
    }
    notifyError(err, fallback, overrides)
    return false
  }, [])

  return { user, ready, handleError }
}
