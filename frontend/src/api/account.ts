import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { User } from "@/lib/types"

// Cuenta del usuario logueado en la tienda (/api/v1/auth)
export const accountKeys = {
  me: ["me"] as const,
}

// The header also reads it (for the photo): it is cached for a minute instead of reloading on every page
export function useMe(enabled: boolean) {
  return useQuery({
    queryKey: accountKeys.me,
    queryFn: () => api.get<User>("/auth/me").then((response) => response.data),
    enabled,
    staleTime: 60_000,
  })
}

// Profile photo: PNG, JPG, WEBP or GIF up to 1 MB (users checks the content). The response is the updated user
export function useUploadAvatar() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append("file", file)
      return api.post<User>("/auth/me/avatar", form).then((response) => response.data)
    },
    onSuccess: (user) => queryClient.setQueryData(accountKeys.me, user),
  })
}

export function useDeleteAvatar() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.delete<User>("/auth/me/avatar").then((response) => response.data),
    onSuccess: (user) => queryClient.setQueryData(accountKeys.me, user),
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
