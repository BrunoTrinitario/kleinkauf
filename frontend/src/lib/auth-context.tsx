
import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from "react"
import axios from "axios"
import type { LoggedUser, LoginUserDto, User, UserRole } from "./types"
import { strings } from "./strings"
import { IS_PLATFORM } from "./platform"

interface AuthContextType {
  user: LoggedUser | null
  activeRole: UserRole | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (credentials: LoginUserDto) => Promise<LoggedUser>
  register: (email: string, password: string, name: string) => Promise<void>
  logout: () => void
  switchRole: (role: UserRole) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const API_BASE_URL = import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

const AUTH_TOKEN_COOKIE = "auth_token"

const setAuthCookie = (token: string) => {
  const maxAge = 60 * 60 * 24 * 7
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
// Si el token vence, la primera llamada al panel devuelve 401 y la pagina cierra la sesion
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
    return raw ? (JSON.parse(raw) as LoggedUser) : null
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

const LOGIN_PATH = IS_PLATFORM ? "/api/v1/platform/auth/login" : "/api/v1/auth/login"

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<LoggedUser | null>(null)
  const [activeRole, setActiveRole] = useState<UserRole | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (IS_PLATFORM) {
      const stored = loadPlatformUser()
      setUser(stored)
      setActiveRole((stored?.roles?.[0]?.type as UserRole) ?? null)
      setIsLoading(false)
      return
    }

    const apiBaseUrl = API_BASE_URL
    const token = getAuthCookie()
    if (!token) {
      setIsLoading(false)
      return
    }

    let isMounted = true

    const loadUser = async () => {
      try {
        const response = await axios.get(`${apiBaseUrl}/api/v1/auth/me`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        })
        if (!isMounted) {
          return
        }

        const me = response.data as User
        const hydratedUser: LoggedUser = {
          id: Number(me.id),
          name: me.name,
          email: me.email,
          token,
          roles: me.roles,
        }
        setUser(hydratedUser)
        setActiveRole((me.roles?.[0]?.type as UserRole) ?? null)
        setIsLoading(false)
      } catch (error) {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (status === 401 || status === 403) {
          clearAuthCookie()
          if (isMounted) {
            setUser(null)
            setActiveRole(null)
          }
          window.location.href = "/"
        }
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

  const login = useCallback(async (credentials: LoginUserDto): Promise<LoggedUser> => {
    const response = await axios.post<LoggedUser>(`${API_BASE_URL}${LOGIN_PATH}`, credentials)
    if (IS_PLATFORM) {
      savePlatformUser(response.data)
    } else if (response.data.token) {
      setAuthCookie(response.data.token)
    }
    setUser(response.data)
    setActiveRole((response.data.roles?.[0]?.type as UserRole) ?? null)
    return response.data
  }, [])

  const register = useCallback(async (email: string, password: string, name: string): Promise<void> => {
    const response = await axios.post<LoggedUser>(`${API_BASE_URL}/api/v1/auth/register`, {
      name,
      email,
      password,
    })
    if (response.data.token) {
      setAuthCookie(response.data.token)
    }
    setUser(response.data)
    setActiveRole((response.data.roles?.[0]?.type as UserRole) ?? null)
  }, [])

  const logout = useCallback(() => {
    clearAuthCookie()
    clearPlatformUser()
    setUser(null)
    setActiveRole(null)
  }, [])

  const switchRole = useCallback((role: UserRole) => {
    if (!user) {
      return
    }

    const hasRole = user.roles.some(userRole => userRole.type === role)
    if (hasRole) {
      setActiveRole(role)
    }
  }, [user])

  return (
    <AuthContext.Provider value={{ user, activeRole, isAuthenticated: !!user, isLoading, login, register, logout, switchRole }}>
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
