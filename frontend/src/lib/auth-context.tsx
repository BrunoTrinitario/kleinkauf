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
  // Closes the session and goes to redirectTo (by default the store home page or the platform login)
  logout: (redirectTo?: string) => void
  switchRole: (role: UserRole) => void
  // Areas of the active role (see lib/permissions.ts)
  can: (area: PermissionArea) => boolean
  // Destination of a logout in progress (null if none): protected routes go there and not to login
  signedOutTo: string | null
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const AUTH_TOKEN_COOKIE = "auth_token"
// Store refresh token: lasts 7 days and renews the 1-hour one (POST /auth/refresh)
const REFRESH_TOKEN_COOKIE = "auth_refresh"

// Seconds until the JWT expires (exp claim); null if it cannot be read
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

// Each cookie lasts as long as its token: once the token expires there is no point keeping it
const setCookie = (name: string, token: string) => {
  const maxAge = Math.max(secondsUntilExpiry(token) ?? 60 * 60, 0)
  document.cookie = `${name}=${encodeURIComponent(token)}; Path=/; Max-Age=${maxAge}; SameSite=Lax`
}

const getCookie = (cookieName: string) => {
  const cookies = document.cookie.split(";")
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split("=")
    if (name === cookieName) {
      return decodeURIComponent(value ?? "")
    }
  }
  return null
}

const setAuthCookies = (user: LoggedUser) => {
  setCookie(AUTH_TOKEN_COOKIE, user.token)
  if (user.refreshToken) {
    setCookie(REFRESH_TOKEN_COOKIE, user.refreshToken)
  }
}

const getAuthCookie = () => getCookie(AUTH_TOKEN_COOKIE)

const clearAuthCookie = () => {
  document.cookie = `${AUTH_TOKEN_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`
  document.cookie = `${REFRESH_TOKEN_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`
}

// The platform has no /auth/me (that route belongs to the stores): the whole superadmin is stored on login,
// with the refresh token. If the token expires, the first dashboard call renews it
const PLATFORM_USER_KEY = "platform_user"

const savePlatformUser = (user: LoggedUser) => {
  try {
    localStorage.setItem(PLATFORM_USER_KEY, JSON.stringify(user))
  } catch {
    // Without storage the session lasts until reload
  }
}

const loadPlatformUser = (): LoggedUser | null => {
  try {
    const raw = localStorage.getItem(PLATFORM_USER_KEY)
    const user = raw ? (JSON.parse(raw) as LoggedUser) : null
    // With the token expired it is still valid while the refresh token has not expired: it is renewed on the first call
    return user && (!isExpired(user.token) || (!!user.refreshToken && !isExpired(user.refreshToken))) ? user : null
  } catch {
    return null
  }
}

const clearPlatformUser = () => {
  try {
    localStorage.removeItem(PLATFORM_USER_KEY)
  } catch {
    // nothing to delete
  }
}

// Token stored at startup: the cookie one in a store, the superadmin's on the platform
const storedToken = (): string | null => {
  if (IS_PLATFORM) {
    return loadPlatformUser()?.token ?? null
  }
  const token = getAuthCookie()
  return token && !isExpired(token) ? token : null
}

// Stored refresh token (not expired): the cookie one in a store, the superadmin's on the platform
const storedRefreshToken = (): string | null => {
  const token = IS_PLATFORM ? loadPlatformUser()?.refreshToken ?? null : getCookie(REFRESH_TOKEN_COOKIE)
  return token && !isExpired(token) ? token : null
}

const LOGIN_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/auth/login` : "/auth/login"
const REFRESH_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/auth/refresh` : "/auth/refresh"
const LOGOUT_PATH = IS_PLATFORM ? `${PLATFORM_PATH}/auth/logout` : "/auth/logout"

// Login that then goes back to the given page (by default, the current one)
export function loginUrl(next: string = window.location.pathname + window.location.search): string {
  return next === "/" || next.startsWith("/login") ? "/login" : `/login?next=${encodeURIComponent(next)}`
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [user, setUser] = useState<LoggedUser | null>(null)
  const [activeRole, setActiveRole] = useState<UserRole | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  // The HTTP client reads the token from here: it is updated before the state so the next call already uses it
  const tokenRef = useRef<string | null>(storedToken())
  // Renewal in progress: if several calls fail together, they all wait for the same one
  const refreshingRef = useRef<Promise<string | null> | null>(null)
  // Where to go after logging out. Lasts until getting there: protected routes also redirect when
  // left without a user, and in the meantime they go to the same destination (otherwise, the last navigation overrides the other)
  const [signedOutTo, setSignedOutTo] = useState<string | null>(null)
  const location = useLocation()

  // The loaded data belongs to the user leaving: the next one must not see it
  const clearSession = useCallback(() => {
    tokenRef.current = null
    clearAuthCookie()
    clearPlatformUser()
    queryClient.clear()
    setUser(null)
    setActiveRole(null)
  }, [queryClient])

  const logout = useCallback((to: string = IS_PLATFORM ? "/login" : "/") => {
    // Revokes the refresh token in the backend (otherwise it works until it expires). Not awaited: it logs out anyway if it fails
    const refreshToken = storedRefreshToken()
    if (refreshToken) {
      api.post(LOGOUT_PATH, { refreshToken }, { skipRefresh: true, skipSessionExpiry: true }).catch(() => {})
    }
    clearSession()
    setSignedOutTo(to)
  }, [clearSession])

  // Requests new tokens with the stored refresh token. The roles are brought up to date too
  const refreshSession = useCallback((): Promise<string | null> => {
    if (refreshingRef.current) {
      return refreshingRef.current
    }
    const refreshToken = storedRefreshToken()
    if (!refreshToken) {
      return Promise.resolve(null)
    }
    refreshingRef.current = api
      .post<LoggedUser>(REFRESH_PATH, { refreshToken }, { skipRefresh: true, skipSessionExpiry: true })
      .then((response) => {
        const renewed = response.data
        tokenRef.current = renewed.token
        if (IS_PLATFORM) {
          savePlatformUser(renewed)
        } else {
          setAuthCookies(renewed)
        }
        setUser((current) => (current ? { ...current, token: renewed.token, refreshToken: renewed.refreshToken, roles: renewed.roles } : current))
        return renewed.token
      })
      .catch(() => null)
      .finally(() => {
        refreshingRef.current = null
      })
    return refreshingRef.current
  }, [])

  // A 401 from a call with a token that could not be renewed: the session is closed and it goes back to login,
  // which then brings the user back here
  useEffect(() => {
    configureApiSession({
      getToken: () => tokenRef.current,
      refresh: refreshSession,
      onSessionExpired: () => {
        if (!tokenRef.current) {
          return
        }
        logout(loginUrl())
        toast.error(strings.errors.sessionExpired, { id: "session-expired" })
      },
    })
  }, [logout, refreshSession])

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

    // No 1-hour token (it expired) but with a refresh token: it is renewed before loading the user
    if (!tokenRef.current && !storedRefreshToken()) {
      clearAuthCookie()
      setIsLoading(false)
      return
    }

    let isMounted = true

    const loadUser = async () => {
      try {
        const token = tokenRef.current ?? (await refreshSession())
        if (!token) {
          clearAuthCookie()
          return
        }
        // A token that is no longer valid at startup only clears the session: the visitor stays on the page they opened
        const response = await api.get<User>("/auth/me", { skipSessionExpiry: true })
        if (!isMounted) {
          return
        }

        const me = response.data
        const hydratedUser: LoggedUser = {
          id: Number(me.id),
          name: me.name,
          email: me.email,
          // If /auth/me had to renew the session, the current token is the new one
          token: tokenRef.current ?? token,
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
      setAuthCookies(loggedUser)
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
