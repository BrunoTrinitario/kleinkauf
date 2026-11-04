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

/*
Recupero de contrasena (sin sesion). Pedir el mail responde lo mismo exista o no el email. El link del mail lleva a
/reset-password?token=: vale 15 minutos y una sola vez (401 si vencio o ya se uso)
 */
export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: (email: string) => api.post("/auth/forgot-password", { email }),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      // Un 401 es el link vencido o usado, no la sesion: no se renueva ni se cierra la sesion abierta
      api.post(`/auth/forgot-password/${encodeURIComponent(token)}`, { newPassword }, { skipRefresh: true, skipSessionExpiry: true }),
  })
}

// 409 si es el ultimo ADMINISTRADOR de la tienda
export function useDeleteAccount() {
  return useMutation({
    mutationFn: () => api.delete("/auth/me"),
  })
}
