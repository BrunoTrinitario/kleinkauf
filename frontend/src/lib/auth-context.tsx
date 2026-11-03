import { createContext, useContext, useState, useCallback, useEffect, useRef, type ReactNode } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import type { LoggedUser, LoginUserDto, User, UserRole } from "./types"
import { strings } from "./strings"
import { IS_PLATFORM } from "./platform"
import { api, configureApiSession, PLATFORM_PATH } from "./api"
import { roleCan, type PermissionArea } from "./permissions"

interface AuthContextType {
  user: LoggedUser | null
  activeRole: UserRole | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (credentials: LoginUserDto) => Promise<LoggedUser>
  register: (email: string, password: string, name: string) => Promise<void>
  // Cierra la sesion y va a redirectTo (por defecto la home de la tienda o el login de la plataforma)
  logout: (redirectTo?: string) => void
  switchRole: (role: UserRole) => void
  // Areas del rol activo (ver lib/permissions.ts)
  can: (area: PermissionArea) => boolean
  // Destino de un cierre de sesion en curso (null si no hay): las rutas protegidas van ahi y no al login
  signedOutTo: string | null
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const AUTH_TOKEN_COOKIE = "auth_token"

// Segundos hasta que vence el JWT (claim exp); null si no se puede leer
const secondsUntilExpiry = (token: string): number | null => {
  try {
    const payload = token.split(".")[1]
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
    const exp = (JSON.parse(json) as { exp?: number }).exp
    return typeof exp === "number" ? Math.floor(exp - Date.now() / 1000) : null
  } catch {
    return null
  }
}

const isExpired = (token: string) => {
  const seconds = secondsUntilExpiry(token)
  return seconds !== null && seconds <= 0
}

// La cookie dura lo mismo que el token: vencido el token no tiene sentido guardarlo
const setAuthCookie = (token: string) => {
  const maxAge = Math.max(secondsUntilExpiry(token) ?? 60 * 60, 0)
  document.cookie = `${AUTH_TOKEN_COOKIE}=${encodeURIComponent(token)}; Path=/; Max-Age=${maxAge}; SameSite=Lax`
}

const getAuthCookie = () => {
  const cookies = document.cookie.split(";")
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split("=")
    if (name === AUTH_TOKEN_COOKIE) {
      return decodeURIComponent(value ?? "")
    }
  }
  return null
}

const clearAuthCookie = () => {
  document.cookie = `${AUTH_TOKEN_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`
}

// La plataforma no tiene /auth/me (esa ruta es de las tiendas): el superadmin se guarda entero al loguearse.
// Si el token vence, la primera llamada al panel devuelve 401 y se cierra la sesion
const PLATFORM_USER_KEY = "platform_user"

const savePlatformUser = (user: LoggedUser) => {
  try {
    localStorage.setItem(PLATFORM_USER_KEY, JSON.stringify(user))
  } catch {
    // Sin storage la sesion dura hasta recargar
  }
}

const loadPlatformUser = (): LoggedUser | null => {
  try {
    const raw = localStorage.getItem(PLATFORM_USER_KEY)
    const user = raw ? (JSON.parse(raw) as LoggedUser) : null
    return user && !isExpired(user.token) ? user : null
  } catch {
    return null
  }
}

const clearPlatformUser = () => {
  try {
    localStorage.removeItem(PLATFORM_USER_KEY)
  } catch {
    // nada que borrar
  }
}

// Token guardado al arrancar: el de la cookie en una tienda, el del superadmin en la plataforma
const storedToken = (): string | null => {
  if (IS_PLATFORM) {
    return loadPlatformUser()?.token ?? null
  }
  const token = getAuthCookie()
  return token && !isExpired(token) ? token : null
}

const LOGIN_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/auth/login` : "/auth/login"

// Login que despues vuelve a la pagina indicada (por defecto, la actual)
export function loginUrl(next: string = window.location.pathname + window.location.search): string {
  return next === "/" || next.startsWith("/login") ? "/login" : `/login?next=${encodeURIComponent(next)}`
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [user, setUser] = useState<LoggedUser | null>(null)
  const [activeRole, setActiveRole] = useState<UserRole | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  // El cliente HTTP lee el token de aca: se actualiza antes que el estado para que la proxima llamada ya lo use
  const tokenRef = useRef<string | null>(storedToken())
  // A donde ir despues de cerrar la sesion. Dura hasta llegar ahi: las rutas protegidas tambien redirigen al
  // quedarse sin usuario, y mientras tanto van al mismo destino (si no, la ultima navegacion pisa a la otra)
  const [signedOutTo, setSignedOutTo] = useState<string | null>(null)
  const location = useLocation()

  // Los datos cargados son del usuario que sale: el proximo no los tiene que ver
  const clearSession = useCallback(() => {
    tokenRef.current = null
    clearAuthCookie()
    clearPlatformUser()
    queryClient.clear()
    setUser(null)
    setActiveRole(null)
  }, [queryClient])

  const logout = useCallback((to: string = IS_PLATFORM ? "/login" : "/") => {
    clearSession()
    setSignedOutTo(to)
  }, [clearSession])

  // Un 401 de una llamada con token: se cierra la sesion y se vuelve al login, que despues trae de vuelta aca
  useEffect(() => {
    configureApiSession({
      getToken: () => tokenRef.current,
      onSessionExpired: () => {
        if (!tokenRef.current) {
          return
        }
        logout(loginUrl())
        toast.error(strings.errors.sessionExpired, { id: "session-expired" })
      },
    })
  }, [logout])

  useEffect(() => {
    if (signedOutTo !== null) {
      navigate(signedOutTo, { replace: true })
    }
  }, [signedOutTo, navigate])

  useEffect(() => {
    if (signedOutTo !== null && location.pathname + location.search === signedOutTo) {
      setSignedOutTo(null)
    }
  }, [signedOutTo, location.pathname, location.search])

  useEffect(() => {
    if (IS_PLATFORM) {
      const stored = loadPlatformUser()
      if (!stored) {
        clearPlatformUser()
      }
      setUser(stored)
      setActiveRole((stored?.roles?.[0]?.type as UserRole) ?? null)
      setIsLoading(false)
      return
    }

    const token = tokenRef.current
    if (!token) {
      clearAuthCookie()
      setIsLoading(false)
      return
    }

    let isMounted = true

    const loadUser = async () => {
      try {
        // Un token que ya no vale al arrancar solo borra la sesion: el visitante sigue en la pagina que abrio
        const response = await api.get<User>("/auth/me", { skipSessionExpiry: true })
        if (!isMounted) {
          return
        }

        const me = response.data
        const hydratedUser: LoggedUser = {
          id: Number(me.id),
          name: me.name,
          email: me.email,
          token,
          roles: me.roles,
        }
        setUser(hydratedUser)
        setActiveRole((me.roles?.[0]?.type as UserRole) ?? null)
      } catch (error) {
        const status = (error as { response?: { status?: number } }).response?.status
        if (status === 401 || status === 403) {
          tokenRef.current = null
          clearAuthCookie()
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadUser()

    return () => {
      isMounted = false
    }
  }, [])

  const startSession = useCallback((loggedUser: LoggedUser) => {
    queryClient.clear()
    setSignedOutTo(null)
    tokenRef.current = loggedUser.token ?? null
    if (IS_PLATFORM) {
      savePlatformUser(loggedUser)
    } else if (loggedUser.token) {
      setAuthCookie(loggedUser.token)
    }
    setUser(loggedUser)
    setActiveRole((loggedUser.roles?.[0]?.type as UserRole) ?? null)
  }, [queryClient])

  const login = useCallback(async (credentials: LoginUserDto): Promise<LoggedUser> => {
    const response = await api.post<LoggedUser>(LOGIN_PATH, credentials)
    startSession(response.data)
    return response.data
  }, [startSession])

  const register = useCallback(async (email: string, password: string, name: string): Promise<void> => {
    const response = await api.post<LoggedUser>("/auth/register", { name, email, password })
    startSession(response.data)
  }, [startSession])

  const switchRole = useCallback((role: UserRole) => {
    if (!user) {
      return
    }

    const hasRole = user.roles.some(userRole => userRole.type === role)
    if (hasRole) {
      setActiveRole(role)
    }
  }, [user])

  const can = useCallback((area: PermissionArea) => roleCan(user?.roles, activeRole, area), [user, activeRole])

  return (
    <AuthContext.Provider value={{ user, activeRole, isAuthenticated: !!user, isLoading, login, register, logout, switchRole, can, signedOutTo }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error(strings.auth.useAuthError)
  }
  return context
}
