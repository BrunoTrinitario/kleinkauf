import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { User } from "@/lib/types"

// Account of the user logged in to the store (/api/v1/auth)
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

// Password undefined = not changed. The email cannot be changed
export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { name: string; password?: string }) => api.patch("/auth/me", payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.me }),
  })
}

/*
Password recovery (no session). Requesting the email responds the same whether the email exists or not. The email link goes to
/reset-password?token=: valid for 15 minutes and only once (401 if it expired or was already used)
 */
export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: (email: string) => api.post("/auth/forgot-password", { email }),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ token, newPassword }: { token: string; newPassword: string }) =>
      // A 401 means the link expired or was used, not the session: the open session is neither renewed nor closed
      api.post(`/auth/forgot-password/${encodeURIComponent(token)}`, { newPassword }, { skipRefresh: true, skipSessionExpiry: true }),
  })
}

// 409 if it is the store's last ADMINISTRADOR
export function useDeleteAccount() {
  return useMutation({
    mutationFn: () => api.delete("/auth/me"),
  })
}
