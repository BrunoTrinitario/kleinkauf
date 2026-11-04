import axios from "axios"
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import { normalizeOrderNumber } from "@/lib/known-orders"
import type {
  AdminOrder,
  AdminOrderFilters,
  CheckoutInfo,
  CreatedOrder,
  NewOrder,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PriceChange,
  SalesSettings,
} from "@/lib/types"

// Compra online (/api/v1/sales, docs/backend/sales.md). Lo del comprador va por numero + email, sin login
export const salesKeys = {
  all: ["sales"] as const,
  checkout: () => [...salesKeys.all, "checkout"] as const,
  order: (number: string, email: string) => [...salesKeys.all, "order", normalizeOrderNumber(number), email] as const,
  mine: (page: number) => [...salesKeys.all, "mine", page] as const,
  admin: () => [...salesKeys.all, "admin"] as const,
  adminList: (filters: AdminOrderFilters) => [...salesKeys.admin(), "list", filters] as const,
  adminOrder: (number: string) => [...salesKeys.admin(), "order", normalizeOrderNumber(number)] as const,
  pendingReview: () => [...salesKeys.admin(), "pending-review"] as const,
  settings: () => [...salesKeys.admin(), "settings"] as const,
}

// Datos para transferir, plazo y topes. paymentConfigured false = la tienda todavia no vende online
export function useCheckoutInfo() {
  return useQuery({
    queryKey: salesKeys.checkout(),
    queryFn: () => api.get<CheckoutInfo>("/sales/checkout").then((response) => response.data),
    staleTime: 5 * 60_000,
  })
}

// 409 con JSON items = cambiaron precios (el pedido no se creo); 409 con texto = sin stock, no disponible o sin precio
export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (order: NewOrder) => api.post<CreatedOrder>("/sales/orders", order).then((response) => response.data),
    // Mis pedidos y la disponibilidad del catalogo cambian (el pedido reserva stock)
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...salesKeys.all, "mine"] })
      queryClient.invalidateQueries({ queryKey: ["catalog"] })
    },
  })
}

export function priceChangesOf(error: unknown): PriceChange[] | null {
  if (!axios.isAxiosError(error) || error.response?.status !== 409) return null
  const data = error.response.data as { items?: unknown } | undefined
  return data && typeof data === "object" && Array.isArray(data.items) ? (data.items as PriceChange[]) : null
}

// Por POST para que el email no quede en la URL. Se vuelve a pedir cada vez que se abre la pantalla (el estado lo
// cambia la tienda), pero no al volver a la pestana: rate limit de 10 por minuto, compartido con cancelar
export function useOrderLookup(number: string, email: string | null) {
  return useQuery({
    queryKey: salesKeys.order(number, email ?? ""),
    queryFn: () =>
      api
        .post<Order>("/sales/orders/lookup", { number: normalizeOrderNumber(number), email })
        .then((response) => response.data),
    enabled: !!number && !!email,
    staleTime: 0,
  })
}

// Las respuestas de comprobante y cancelar son el pedido actualizado: se deja en la cache de la consulta.
// returnsStock: cancelar devuelve el stock, asi que cambia la disponibilidad del catalogo
function useOrderUpdate<TVariables>(
  mutationFn: (variables: TVariables) => Promise<Order>,
  email: string,
  returnsStock = false
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (order) => {
      queryClient.setQueryData(salesKeys.order(order.number, email), order)
      queryClient.invalidateQueries({ queryKey: [...salesKeys.all, "mine"] })
      if (returnsStock) queryClient.invalidateQueries({ queryKey: ["catalog"] })
    },
  })
}

// Pasa a PAGO_EN_REVISION. JPG, PNG o PDF de hasta 5 MB (el backend mira el contenido)
export function useUploadReceipt(number: string, email: string) {
  return useOrderUpdate((file: File) => {
    const form = new FormData()
    form.append("email", email)
    form.append("file", file)
    return api
      .post<Order>(`/sales/orders/${encodeURIComponent(normalizeOrderNumber(number))}/receipt`, form)
      .then((response) => response.data)
  }, email)
}

// Solo mientras no se valido el pago. Devuelve el stock
export function useCancelOrder(number: string, email: string) {
  return useOrderUpdate<void>(
    () =>
      api
        .post<Order>(`/sales/orders/${encodeURIComponent(normalizeOrderNumber(number))}/cancel`, { email })
        .then((response) => response.data),
    email,
    true
  )
}

// Pedidos del usuario logueado, mas nuevos primero
export function useMyOrders(page: number, enabled: boolean) {
  return useQuery({
    queryKey: salesKeys.mine(page),
    queryFn: () =>
      api.get<Page<OrderSummary>>("/sales/orders/mine", { params: { page, size: 10 } }).then((response) => response.data),
    enabled,
    placeholderData: keepPreviousData,
    meta: { errorMessage: strings.myOrders.loadError },
  })
}

/*
Panel de ventas (solo ADMINISTRADOR, docs/ventas.md). Las keys cuelgan de ["sales"], asi un cambio de estado tambien
actualiza el resumen del panel (/admin) y "mis pedidos".
 */

const ADMIN_PAGE_SIZE = 20

export function useAdminOrders(filters: AdminOrderFilters) {
  return useQuery({
    queryKey: salesKeys.adminList(filters),
    queryFn: () =>
      api
        .get<Page<OrderSummary>>("/sales/admin/orders", {
          params: {
            status: filters.status || undefined,
            channel: filters.channel || undefined,
            q: filters.q || undefined,
            from: filters.from || undefined,
            to: filters.to || undefined,
            page: filters.page,
            size: ADMIN_PAGE_SIZE,
          },
        })
        .then((response) => response.data),
    placeholderData: keepPreviousData,
  })
}

// Comprobantes por revisar: el contador del menu. Se vuelve a mirar cada minuto
export function usePendingReviewCount(enabled: boolean) {
  return useQuery({
    queryKey: salesKeys.pendingReview(),
    queryFn: () =>
      api
        .get<Page<OrderSummary>>("/sales/admin/orders", { params: { status: "PAGO_EN_REVISION", size: 1 } })
        .then((response) => response.data.totalItems),
    enabled,
    refetchInterval: 60_000,
  })
}

export function useAdminOrder(number: string) {
  return useQuery({
    queryKey: salesKeys.adminOrder(number),
    queryFn: () =>
      api
        .get<AdminOrder>(`/sales/admin/orders/${encodeURIComponent(normalizeOrderNumber(number))}`)
        .then((response) => response.data),
    enabled: !!number,
  })
}

/*
Validar, rechazar, avanzar o cancelar. reason es obligatorio para RECHAZADO y CANCELADO.
409: el cambio no corresponde o alguien lo cambio mientras tanto. 503: products no respondio (el pedido no cambio)
 */
export function useChangeOrderStatus(number: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ status, reason }: { status: OrderStatus; reason?: string }) =>
      api
        .put<AdminOrder>(`/sales/admin/orders/${encodeURIComponent(normalizeOrderNumber(number))}/status`, {
          status,
          reason: reason?.trim() || null,
        })
        .then((response) => response.data),
    onSuccess: (order) => {
      queryClient.setQueryData(salesKeys.adminOrder(order.number), order)
      queryClient.invalidateQueries({ queryKey: salesKeys.all })
      // Cancelar devuelve stock y validar suma vendidos: cambia el catalogo y el stock del panel
      queryClient.invalidateQueries({ queryKey: ["catalog"] })
      queryClient.invalidateQueries({ queryKey: ["products"] })
    },
  })
}

// El comprobante pide el token: se baja como blob y se muestra o descarga desde una URL local
export async function fetchReceipt(number: string, id: number): Promise<Blob> {
  const response = await api.get<Blob>(
    `/sales/admin/orders/${encodeURIComponent(normalizeOrderNumber(number))}/receipts/${id}`,
    { responseType: "blob" }
  )
  return response.data
}

export function useSalesSettings(enabled = true) {
  return useQuery({
    queryKey: salesKeys.settings(),
    queryFn: () => api.get<SalesSettings>("/sales/admin/settings").then((response) => response.data),
    enabled,
  })
}

// Cambia lo que ve el checkout (datos para transferir y plazo)
export function useUpdateSalesSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (settings: SalesSettings) =>
      api.put<SalesSettings>("/sales/admin/settings", settings).then((response) => response.data),
    onSuccess: (settings) => {
      queryClient.setQueryData(salesKeys.settings(), settings)
      queryClient.invalidateQueries({ queryKey: salesKeys.checkout() })
    },
  })
}
