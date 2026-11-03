import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import type { Page, User, UserRole } from "@/lib/types"

// Usuarios de la tienda (/api/v1/users): solo ADMINISTRADOR
export const userKeys = {
  all: ["users"] as const,
  list: (params: UserListParams) => [...userKeys.all, "list", params] as const,
}

export interface UserListParams {
  q?: string
  role?: UserRole | ""
  page?: number
}

export interface NewUserDto {
  name: string
  email: string
  password: string
  roles: string[]
}

export function useUsers(params: UserListParams, enabled: boolean) {
  return useQuery({
    queryKey: userKeys.list(params),
    queryFn: () =>
      api
        .get<Page<User>>("/users", {
          params: { q: params.q || undefined, role: params.role || undefined, page: params.page },
        })
        .then((response) => response.data),
    enabled,
    placeholderData: keepPreviousData,
    meta: { errorMessage: strings.errors.loadUsers },
  })
}

export function useCreateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: NewUserDto) => api.post<User>("/users", payload).then((response) => response.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}

export function useDeleteUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`/users/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}

/*
Nombre y roles en varias llamadas: el PATCH solo cambia el nombre (el email no se edita) y cada rol se da o se
saca por separado (roleName va como query param). Pase lo que pase se recarga la lista, para no mostrar datos que
no se guardaron si algo fallo a mitad de camino.
 */
export function useSaveUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ previous, next }: { previous: User; next: User }) => {
      if (previous.name !== next.name) {
        await api.patch(`/users/${next.id}`, { name: next.name })
      }
      const before = new Set(previous.roles.map((role) => role.type))
      const after = new Set(next.roles.map((role) => role.type))
      const toAdd = [...after].filter((role) => !before.has(role))
      const toRemove = [...before].filter((role) => !after.has(role))
      await Promise.all(toAdd.map((roleName) => api.post(`/users/${next.id}/roles`, null, { params: { roleName } })))
      await Promise.all(toRemove.map((roleName) => api.delete(`/users/${next.id}/roles`, { params: { roleName } })))
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
}
