import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import type { Page, User, UserRole } from "@/lib/types"

// Store users (/api/v1/users): ADMINISTRADOR only
export const userKeys = {
  all: ["users"] as const,
  list: (params: UserListParams) => [...userKeys.all, "list", params] as const,
  permissions: () => [...userKeys.all, "permissions"] as const,
}

// Area mask of each store role (see docs/features/permissions.md)
export interface RolePermissions {
  role: UserRole
  permissions: number
}

export function useRolePermissions(enabled: boolean) {
  return useQuery({
    queryKey: userKeys.permissions(),
    queryFn: () => api.get<RolePermissions[]>("/users/roles/permissions").then((response) => response.data),
    enabled,
    meta: { errorMessage: strings.adminPermissions.loadError },
  })
}

// Only EMPLEADO and CLIENTE, without USUARIOS (400). products applies it within 1 minute (roles cache).
// The roles in the user list carry the mask: they are reloaded too
export function useSetRolePermissions() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ role, permissions }: RolePermissions) =>
      api.put<RolePermissions>(`/users/roles/${role}/permissions`, { permissions }).then((response) => response.data),
    onSettled: () => queryClient.invalidateQueries({ queryKey: userKeys.all }),
  })
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
Name and roles in several calls: the PATCH only changes the name (the email cannot be edited) and each role is granted or
removed separately (roleName goes as a query param). Whatever happens the list is reloaded, so as not to show data that
was not saved if something failed halfway.
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
