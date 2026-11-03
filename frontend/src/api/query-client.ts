import axios from "axios"
import { QueryCache, QueryClient } from "@tanstack/react-query"
import { notifyError } from "@/lib/api-error"

declare module "@tanstack/react-query" {
  interface Register {
    // errorMessage: el aviso que se muestra si la consulta falla (sin el, la pantalla decide que mostrar)
    queryMeta: { errorMessage?: string }
  }
}

// Un 4xx no cambia por reintentar (permiso, validacion, sesion): solo se reintentan la red caida y los 5xx
const shouldRetry = (failureCount: number, error: unknown) => {
  if (failureCount >= 2) return false
  if (!axios.isAxiosError(error)) return false
  const status = error.response?.status
  return status === undefined || status >= 500
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.errorMessage) {
        notifyError(error, query.meta.errorMessage)
      }
    },
  }),
  defaultOptions: {
    queries: {
      retry: shouldRetry,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
})
