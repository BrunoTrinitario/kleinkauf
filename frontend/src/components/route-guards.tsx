import { Navigate, Outlet, useLocation } from "react-router-dom"
import { loginUrl, useAuth } from "@/lib/auth-context"
import type { PermissionArea } from "@/lib/permissions"
import { SUPERADMIN_ROLE } from "@/lib/platform"
import type { UserRole } from "@/lib/types"

/*
Rutas protegidas: envuelven rutas hijas (<Outlet />). La UI oculta lo que no se puede usar; el backend igual valida.
Sin sesion se va al login con la pagina de vuelta; con sesion pero sin el rol o el area, a la home.
Mientras se carga la sesion no se muestra nada (para no redirigir antes de saber quien es).
 */

// Al cerrar la sesion (a mano o por vencimiento) va a donde dijo logout; si no, al login con la pagina de vuelta
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

// Rol activo exacto (ej. el panel de la tienda es solo del ADMINISTRADOR, no por areas)
export function RequireRole({ role }: { role: UserRole }) {
  const { activeRole, isLoading } = useAuth()
  if (isLoading) return null
  return activeRole === role ? <Outlet /> : <Navigate to="/" replace />
}

// Alcanza con una de las areas del rol activo (ej. el panel de productos abre con cualquiera de las suyas)
export function RequireArea({ anyOf }: { anyOf: PermissionArea[] }) {
  const { can, isLoading } = useAuth()
  if (isLoading) return null
  return anyOf.some((area) => can(area)) ? <Outlet /> : <Navigate to="/" replace />
}

// Panel de la plataforma: solo superadmins; cualquier otro (o sin sesion) va al login de la plataforma
export function RequireSuperadmin() {
  const { user, isLoading } = useAuth()
  const login = useLoginRedirect()
  if (isLoading) return null
  const isSuperadmin = !!user?.roles.some((role) => role.type === SUPERADMIN_ROLE)
  return isSuperadmin ? <Outlet /> : <Navigate to={login} replace />
}
