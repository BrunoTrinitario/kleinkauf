import { Navigate, Outlet, useLocation } from "react-router-dom"
import { loginUrl, useAuth } from "@/lib/auth-context"
import type { PermissionArea } from "@/lib/permissions"
import { SUPERADMIN_ROLE } from "@/lib/platform"
import type { UserRole } from "@/lib/types"

/*
Protected routes: they wrap child routes (<Outlet />). The UI hides what cannot be used; the backend validates anyway.
Without a session it goes to login with the return page; with a session but without the role or the area, to the home page.
While the session is loading nothing is shown (so as not to redirect before knowing who it is).
 */

// When the session closes (manually or by expiry) it goes where logout said; otherwise, to login with the return page
function useLoginRedirect() {
  const location = useLocation()
  const { signedOutTo } = useAuth()
  return signedOutTo ?? loginUrl(location.pathname + location.search)
}

export function RequireAuth() {
  const { isAuthenticated, isLoading } = useAuth()
  const login = useLoginRedirect()
  if (isLoading) return null
  return isAuthenticated ? <Outlet /> : <Navigate to={login} replace />
}

// Exact active role (e.g. the store dashboard is ADMINISTRADOR-only, not by areas)
export function RequireRole({ role }: { role: UserRole }) {
  const { activeRole, isLoading } = useAuth()
  if (isLoading) return null
  return activeRole === role ? <Outlet /> : <Navigate to="/" replace />
}

// One of the active role's areas is enough (e.g. the products dashboard opens with any of its own)
export function RequireArea({ anyOf }: { anyOf: PermissionArea[] }) {
  const { can, isLoading } = useAuth()
  if (isLoading) return null
  return anyOf.some((area) => can(area)) ? <Outlet /> : <Navigate to="/" replace />
}

// Platform dashboard: superadmins only; anyone else (or without a session) goes to the platform login
export function RequireSuperadmin() {
  const { user, isLoading } = useAuth()
  const login = useLoginRedirect()
  if (isLoading) return null
  const isSuperadmin = !!user?.roles.some((role) => role.type === SUPERADMIN_ROLE)
  return isSuperadmin ? <Outlet /> : <Navigate to={login} replace />
}
