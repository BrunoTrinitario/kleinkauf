import axios, { AxiosHeaders } from "axios"
import { toast } from "sonner"
import { IS_PLATFORM } from "./platform"
import { strings } from "./strings"
import { reportTenantError } from "./tenant-status"

// Single client to talk to the gateway. The gateway recognizes the tenant by the Origin, which the browser sends on its own
export const API_BASE_URL = import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

declare module "axios" {
  interface AxiosRequestConfig {
    // A 401 from this call does not close the session or go to login (e.g. the initial /auth/me load)
    skipSessionExpiry?: boolean
    // A 401 from this call does not try to renew the session (the renewal itself, or a retry that already renewed it)
    skipRefresh?: boolean
  }
}

export const api = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  timeout: 15000,
})

// Platform dashboard (superadmin): same rules, routes under /api/v1/platform
export const PLATFORM_PATH = "/platform"

// Images are served without a tenant and outside /api/v1 (see docs/backend/products.md)
export const productImageUrl = (fileName: string) => `${API_BASE_URL}/products/images/${fileName}`
export const avatarUrl = (fileName: string) => `${API_BASE_URL}/users/avatars/${fileName}`

interface ApiSession {
  getToken: () => string | null
  // Renews the session with the refresh token: the new token, or null if it could not (there is none, it expired or is no longer valid)
  refresh: () => Promise<string | null>
  // A 401 from a call that carried the token and could not be renewed: the session is over
  onSessionExpired: () => void
}

let session: ApiSession = {
  getToken: () => null,
  refresh: async () => null,
  onSessionExpired: () => {},
}

// Called by AuthProvider: the client knows nothing about React, only where to get the token and whom to notify
export function configureApiSession(next: ApiSession) {
  session = next
}

api.interceptors.request.use((config) => {
  const token = session.getToken()
  if (token) {
    const headers = AxiosHeaders.from(config.headers)
    if (!headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${token}`)
    }
    config.headers = headers
  }
  return config
})

api.interceptors.response.use(undefined, async (error) => {
  // The 1-hour token expired: it is renewed with the refresh token and the call is repeated once
  const config = axios.isAxiosError(error) ? error.config : undefined
  if (
    config &&
    error.response?.status === 401 &&
    !config.skipRefresh &&
    AxiosHeaders.from(config.headers).has("Authorization")
  ) {
    const token = await session.refresh()
    if (token) {
      const headers = AxiosHeaders.from(config.headers)
      headers.set("Authorization", `Bearer ${token}`)
      return api({ ...config, headers, skipRefresh: true })
    }
  }
  if (isSessionExpired(error)) {
    session.onSessionExpired()
  }
  if (!IS_PLATFORM) {
    reportTenantError(error)
  }
  // Gateway rate limit (1-minute windows): a single notice even if several calls fail together.
  // The id is the same message notifyError uses, so a screen that also reports it does not repeat it
  if (axios.isAxiosError(error) && error.response?.status === 429) {
    toast.error(strings.errors.tooManyRequests, { id: strings.errors.tooManyRequests })
  }
  return Promise.reject(error)
})

// true if the error is a 401 that closes the session: the screen does not have to show anything else.
// Without a token it is a 401 of the request itself (e.g. invalid credentials on login) and the screen handles it
export function isSessionExpired(error: unknown): boolean {
  return (
    axios.isAxiosError(error) &&
    error.response?.status === 401 &&
    !error.config?.skipSessionExpiry &&
    AxiosHeaders.from(error.config?.headers).has("Authorization")
  )
}
