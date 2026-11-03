import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api, PLATFORM_PATH } from "@/lib/api"
import { strings } from "@/lib/strings"
import type {
  NewTenantDto,
  PeriodGroupBy,
  PlatformDashboard,
  PlatformTenant,
  Superadmin,
  TenantProductsUsage,
  TenantUsersUsage,
} from "@/lib/types"

// Panel de la plataforma (/api/v1/platform): solo SUPERADMIN, sin tienda
export const platformKeys = {
  all: ["platform"] as const,
  dashboard: (range: PeriodRange) => [...platformKeys.all, "dashboard", range] as const,
  usersUsage: () => [...platformKeys.all, "usage", "users"] as const,
  productsUsage: () => [...platformKeys.all, "usage", "products"] as const,
  tenants: () => [...platformKeys.all, "tenants"] as const,
  superadmins: () => [...platformKeys.all, "superadmins"] as const,
}

export interface PeriodRange {
  from: string
  to: string
  groupBy: PeriodGroupBy
}

export function usePlatformDashboard(range: PeriodRange, enabled: boolean) {
  return useQuery({
    queryKey: platformKeys.dashboard(range),
    queryFn: () =>
      api.get<PlatformDashboard>(`${PLATFORM_PATH}/admin/dashboard`, { params: range }).then((response) => response.data),
    enabled,
    meta: { errorMessage: strings.errors.loadDashboard },
  })
}

// Cada servicio arma su parte del uso por tienda: si products falla, la tabla sale igual sin sus columnas
export function usePlatformUsersUsage(enabled: boolean) {
  return useQuery({
    queryKey: platformKeys.usersUsage(),
    queryFn: () => api.get<TenantUsersUsage[]>(`${PLATFORM_PATH}/admin/usage`).then((response) => response.data),
    enabled,
    meta: { errorMessage: strings.errors.loadDashboard },
  })
}

export function usePlatformProductsUsage(enabled: boolean) {
  return useQuery({
    queryKey: platformKeys.productsUsage(),
    queryFn: () => api.get<TenantProductsUsage[]>(`${PLATFORM_PATH}/products/usage`).then((response) => response.data),
    enabled,
  })
}

export function usePlatformTenants(enabled: boolean) {
  return useQuery({
    queryKey: platformKeys.tenants(),
    queryFn: () => api.get<PlatformTenant[]>(`${PLATFORM_PATH}/admin/tenants`).then((response) => response.data),
    enabled,
    meta: { errorMessage: strings.errors.loadTenants },
  })
}

// Un alta, baja o cambio de estado tambien cambia el resumen y el uso por tienda
const invalidatePlatform = (queryClient: ReturnType<typeof useQueryClient>) =>
  queryClient.invalidateQueries({ queryKey: platformKeys.all })

export function useSetTenantEnabled() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      api.put<PlatformTenant>(`${PLATFORM_PATH}/admin/tenants/${id}/enabled`, { enabled }).then((response) => response.data),
    onSuccess: () => invalidatePlatform(queryClient),
  })
}

export function useDeleteTenant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api.delete(`${PLATFORM_PATH}/admin/tenants/${id}`),
    onSuccess: () => invalidatePlatform(queryClient),
  })
}

export function useCreateTenant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: NewTenantDto) =>
      api.post<PlatformTenant>(`${PLATFORM_PATH}/admin/tenants`, payload).then((response) => response.data),
    onSuccess: () => invalidatePlatform(queryClient),
  })
}

export function useSuperadmins(enabled: boolean) {
  return useQuery({
    queryKey: platformKeys.superadmins(),
    queryFn: () => api.get<Superadmin[]>(`${PLATFORM_PATH}/admin/superadmins`).then((response) => response.data),
    enabled,
    meta: { errorMessage: strings.errors.loadSuperadmins },
  })
}

// 409: es el ultimo superadmin activo
export function useSetSuperadminActive() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      api.put<Superadmin>(`${PLATFORM_PATH}/admin/superadmins/${id}/active`, { active }).then((response) => response.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: platformKeys.superadmins() }),
  })
}

export function useCreateSuperadmin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: { name: string; email: string; password: string }) =>
      api.post<Superadmin>(`${PLATFORM_PATH}/admin/superadmins`, payload).then((response) => response.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: platformKeys.superadmins() }),
  })
}
