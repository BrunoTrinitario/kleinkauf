import axios from "axios"
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import { normalizeOrderNumber } from "@/lib/known-orders"
import type { CheckoutInfo, CreatedOrder, NewOrder, Order, OrderSummary, Page, PriceChange } from "@/lib/types"

// Compra online (/api/v1/sales, docs/backend/sales.md). Lo del comprador va por numero + email, sin login
export const salesKeys = {
  all: ["sales"] as const,
  checkout: () => [...salesKeys.all, "checkout"] as const,
  order: (number: string, email: string) => [...salesKeys.all, "order", normalizeOrderNumber(number), email] as const,
  mine: (page: number) => [...salesKeys.all, "mine", page] as const,
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
