import axios from "axios"
import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import { normalizeOrderNumber } from "@/lib/known-orders"
import type {
  AdminOrder,
  AdminOrderFilters,
  CheckoutInfo,
  CreatedOrder,
  NewOrder,
  NewLocalSale,
  OrderChannel,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PriceChange,
  PaymentMethod,
  SalesSettings,
  User,
} from "@/lib/types"
import type { SalesSummary } from "@/api/dashboard"

// A ranking row: revenue is before the order discount; profit is higher than the real one if unitsWithoutCost > 0
export interface SalesRankingRow {
  type: "PRODUCT" | "BUNDLE"
  id: number
  name: string
  units: number
  revenue: number
  cost: number
  profit: number
  unitsWithoutCost: number
}

// Online purchase (/api/v1/sales, docs/backend/sales.md). Buyer operations go by number + email, without login
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

// Transfer details, deadline and limits. paymentConfigured false = the store does not sell online yet
export function useCheckoutInfo() {
  return useQuery({
    queryKey: salesKeys.checkout(),
    queryFn: () => api.get<CheckoutInfo>("/sales/checkout").then((response) => response.data),
    staleTime: 5 * 60_000,
  })
}

// 409 with JSON items = prices changed (the order was not created); 409 with text = no stock, not available or no price
export function useCreateOrder() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (order: NewOrder) => api.post<CreatedOrder>("/sales/orders", order).then((response) => response.data),
    // My orders and the catalog availability change (the order reserves stock)
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

// By POST so the email does not end up in the URL. It is requested again every time the screen opens (the store
// changes the status), but not when returning to the tab: rate limit of 10 per minute, shared with cancelling
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

// The receipt and cancel responses are the updated order: it is left in the lookup's cache.
// returnsStock: cancelling returns the stock, so the catalog availability changes
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

// Moves to PAGO_EN_REVISION. JPG, PNG or PDF up to 5 MB (the backend checks the content)
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

// Only while the payment has not been validated. Returns the stock
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

// Orders of the logged-in user, newest first
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
Sales dashboard (ADMINISTRADOR only, docs/ventas.md). The keys hang from ["sales"], so a status change also
updates the dashboard overview (/admin) and "my orders".
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
            paymentMethod: filters.paymentMethod || undefined,
            stockReview: filters.stockReview || undefined,
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

// Receipts to review: the menu counter. Checked again every minute
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
Validate, reject, advance or cancel. reason is required for RECHAZADO and CANCELADO.
409: the change does not apply or someone changed it in the meantime. 503: products did not answer (the order did not change)
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
      // Cancelling returns stock and validating adds to sold: the catalog and the dashboard stock change
      queryClient.invalidateQueries({ queryKey: ["catalog"] })
      queryClient.invalidateQueries({ queryKey: ["products"] })
    },
  })
}

// The receipt requires the token: it is downloaded as a blob and shown or downloaded from a local URL
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

// Changes what the checkout shows (transfer details and deadline)
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

/*
In-store sale (already charged). Reserves the stock like a sale: if it is short, products records an adjustment and the order
gets stockReview. 400: invalid data or "a consultar" products without a price. 409: an item does not exist or is not
available. 503: products did not answer
 */
export function useCreateLocalSale() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (sale: NewLocalSale) =>
      api.post<AdminOrder>("/sales/admin/orders", sale).then((response) => response.data),
    onSuccess: (order) => {
      queryClient.setQueryData(salesKeys.adminOrder(order.number), order)
      queryClient.invalidateQueries({ queryKey: salesKeys.all })
      queryClient.invalidateQueries({ queryKey: ["catalog"] })
      queryClient.invalidateQueries({ queryKey: ["products"] })
      queryClient.invalidateQueries({ queryKey: ["bundles"] })
    },
  })
}

// Reports: store days (yyyy-MM-dd, inclusive), up to 366. Empty = all channels / payment methods
export interface SalesReportFilters {
  from: string
  to: string
  groupBy: "day" | "week" | "month"
  channel: OrderChannel | ""
  paymentMethod: PaymentMethod | ""
}

export function useSalesReport(filters: SalesReportFilters, enabled = true) {
  return useQuery({
    queryKey: [...salesKeys.admin(), "report", filters],
    queryFn: () =>
      api
        .get<SalesSummary>("/sales/admin/reports/summary", {
          params: {
            from: filters.from,
            to: filters.to,
            groupBy: filters.groupBy,
            channel: filters.channel || undefined,
            paymentMethod: filters.paymentMethod || undefined,
          },
        })
        .then((response) => response.data),
    enabled,
    placeholderData: keepPreviousData,
  })
}

export type RankingSort = "units" | "revenue" | "profit"

// Ranking revenue: line subtotals, before the order discount
export function useSalesRanking(
  filters: { from: string; to: string; channel: OrderChannel | ""; sort: RankingSort },
  enabled = true
) {
  return useQuery({
    queryKey: [...salesKeys.admin(), "ranking", filters],
    queryFn: () =>
      api
        .get<{ items: SalesRankingRow[] }>("/sales/admin/reports/products", {
          params: {
            from: filters.from,
            to: filters.to,
            channel: filters.channel || undefined,
            sort: filters.sort,
            limit: 20,
          },
        })
        .then((response) => response.data.items),
    enabled,
    placeholderData: keepPreviousData,
  })
}

// Names of the history users (GET /users/{id}, ADMINISTRADOR only). A deleted user ends up without a name
export function useUserNames(ids: number[]): Record<number, string | undefined> {
  const unique = [...new Set(ids)]
  const results = useQueries({
    queries: unique.map((id) => ({
      queryKey: ["users", "name", id],
      queryFn: () => api.get<User>(`/users/${id}`).then((response) => response.data.name),
      staleTime: 10 * 60_000,
      retry: false,
    })),
  })
  return Object.fromEntries(unique.map((id, index) => [id, results[index]?.data]))
}
