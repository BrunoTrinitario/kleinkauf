import axios, { AxiosHeaders } from "axios"
import { toast } from "sonner"
import { IS_PLATFORM } from "./platform"
import { strings } from "./strings"
import { reportTenantError } from "./tenant-status"

// Cliente unico para hablarle al gateway. El tenant lo reconoce el gateway por el Origin, que el navegador manda solo
export const API_BASE_URL = import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

declare module "axios" {
  interface AxiosRequestConfig {
    // Un 401 de esta llamada no cierra la sesion ni manda al login (ej. la carga inicial de /auth/me)
    skipSessionExpiry?: boolean
    // Un 401 de esta llamada no intenta renovar la sesion (la propia renovacion, o un reintento que ya la renovo)
    skipRefresh?: boolean
  }
}

export const api = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  timeout: 15000,
})

// Panel de la plataforma (superadmin): mismas reglas, rutas bajo /api/v1/platform
export const PLATFORM_PATH = "/platform"

// Las imagenes se sirven sin tenant y fuera de /api/v1 (ver docs/backend/products.md)
export const productImageUrl = (fileName: string) => `${API_BASE_URL}/products/images/${fileName}`
export const avatarUrl = (fileName: string) => `${API_BASE_URL}/users/avatars/${fileName}`

interface ApiSession {
  getToken: () => string | null
  // Renueva la sesion con el refresh token: el token nuevo, o null si no se pudo (no hay, vencio o ya no vale)
  refresh: () => Promise<string | null>
  // Un 401 de una llamada que llevaba el token y no se pudo renovar: la sesion se termino
  onSessionExpired: () => void
}

let session: ApiSession = {
  getToken: () => null,
  refresh: async () => null,
  onSessionExpired: () => {},
}

// Lo llama AuthProvider: el cliente no sabe de React, solo de donde sacar el token y a quien avisar
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
  // El token de 1 hora vencio: se renueva con el refresh y se repite la llamada una vez
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
  // Rate limit del gateway (ventanas de 1 minuto): un solo aviso aunque fallen varias llamadas juntas.
  // El id es el mismo mensaje que usa notifyError, asi una pantalla que tambien lo avisa no lo repite
  if (axios.isAxiosError(error) && error.response?.status === 429) {
    toast.error(strings.errors.tooManyRequests, { id: strings.errors.tooManyRequests })
  }
  return Promise.reject(error)
})

// true si el error es un 401 que cierra la sesion: la pantalla no tiene que mostrar nada mas.
// Sin token es un 401 del propio pedido (ej. credenciales invalidas en el login) y lo maneja la pantalla
export function isSessionExpired(error: unknown): boolean {
  return (
    axios.isAxiosError(error) &&
    error.response?.status === 401 &&
    !error.config?.skipSessionExpiry &&
    AxiosHeaders.from(error.config?.headers).has("Authorization")
  )
}
