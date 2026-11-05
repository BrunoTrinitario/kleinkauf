import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { IS_PLATFORM } from "@/lib/platform"
import type { StoreConfig, UpdateStoreConfig } from "@/lib/types"

// Store settings: name, color theme and icon. Every page reads them (/catalog/store, public) and the ADMINISTRADOR
// changes them (/store/settings). The platform is not a store, so it does not request them
export const storeKeys = {
  config: ["store", "config"] as const,
}

export function useStoreConfig() {
  return useQuery({
    queryKey: storeKeys.config,
    queryFn: () => api.get<StoreConfig>("/catalog/store").then((response) => response.data),
    enabled: !IS_PLATFORM,
    staleTime: 5 * 60_000,
  })
}

// Every change responds with the new settings: they go straight into the public query cache
function useStoreMutation<T>(mutationFn: (value: T) => Promise<StoreConfig>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (config) => queryClient.setQueryData(storeKeys.config, config),
  })
}

export function useUpdateStoreConfig() {
  return useStoreMutation((config: UpdateStoreConfig) =>
    api.put<StoreConfig>("/store/settings", config).then((response) => response.data)
  )
}

// Up to 1 MB, JPG, PNG, WEBP or GIF (products validates it by content)
export function useUploadStoreIcon() {
  return useStoreMutation((file: File) => {
    const form = new FormData()
    form.append("file", file)
    return api.post<StoreConfig>("/store/settings/icon", form).then((response) => response.data)
  })
}

export function useDeleteStoreIcon() {
  return useStoreMutation(() => api.delete<StoreConfig>("/store/settings/icon").then((response) => response.data))
}
