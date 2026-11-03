import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { User } from "@/lib/types"

// Cuenta del usuario logueado en la tienda (/api/v1/auth)
export const accountKeys = {
  me: ["me"] as const,
}

export function useMe(enabled: boolean) {
  return useQuery({
    queryKey: accountKeys.me,
    queryFn: () => api.get<User>("/auth/me").then((response) => response.data),
    enabled,
  })
}

// Contrasena undefined = no se cambia. El email no se puede cambiar
export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { name: string; password?: string }) => api.patch("/auth/me", payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.me }),
  })
}

// 409 si es el ultimo ADMINISTRADOR de la tienda
export function useDeleteAccount() {
  return useMutation({
    mutationFn: () => api.delete("/auth/me"),
  })
}
