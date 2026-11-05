import axios from "axios"
import { QueryCache, QueryClient } from "@tanstack/react-query"
import { notifyError } from "@/lib/api-error"

declare module "@tanstack/react-query" {
  interface Register {
    // errorMessage: the notice shown if the query fails (without it, the screen decides what to show)
    queryMeta: { errorMessage?: string }
  }
}

// A 4xx does not change by retrying (permission, validation, session): only network failures and 5xx are retried
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
