import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import type { PeriodRange } from "@/lib/periods"
import type { PeriodGroupBy } from "@/lib/types"
import type { StockMovementType } from "./products"
import { productKeys } from "./products"
import { userKeys } from "./users"

/*
Resumen del panel de la tienda (solo ADMINISTRADOR, docs/panel-admin.md). Cada servicio arma su parte y cada bloque
carga por separado: si uno falla, el resto del panel sigue. Las keys cuelgan de cada dominio, asi un cambio de
productos, usuarios o ventas tambien actualiza el resumen.
 */
export const salesKeys = {
  all: ["sales"] as const,
}

export interface ProductsDashboard {
  products: {
    total: number
    byCategory: { category: string | null; count: number }[]
    withoutPrice: number
    withoutImages: number
    featured: number
  }
  stock: {
    low: number
    outOfStock: number
    inventoryAtCost: number
    inventoryAtPrice: number
    excludedWithoutCost: number
    excludedWithoutPrice: number
    recentMovements: {
      id: number
      productId: number
      productName: string
      type: StockMovementType
      quantity: number
      stockAfter: number
      reason: string | null
      reference: string | null
      userId: number | null
      createdAt: string
    }[]
  }
}

export interface UsersDashboard {
  total: number
  active: number
  activeWindowDays: number
  byRole: Record<string, number>
  registrations: { from: string; to: string; groupBy: PeriodGroupBy; periods: { period: string; count: number }[] }
}

// unitsWithoutCost > 0: la ganancia es mas alta de lo real (hay que avisarlo)
export interface SalesTotals {
  orders: number
  units: number
  revenue: number
  discounts: number
  cost: number
  profit: number
  unitsWithoutCost: number
}

export interface SalesSummary {
  from: string
  to: string
  groupBy: PeriodGroupBy
  totals: SalesTotals
  periods: { start: string; totals: SalesTotals }[]
  byChannel: { key: string; totals: SalesTotals }[]
  byPaymentMethod: { key: string; totals: SalesTotals }[]
}

export interface SalesTopProduct {
  type: "PRODUCT" | "BUNDLE"
  id: number
  name: string
  units: number
  revenue: number
  profit: number
}

export function useProductsDashboard(enabled: boolean) {
  return useQuery({
    queryKey: [...productKeys.all, "dashboard"],
    queryFn: () => api.get<ProductsDashboard>("/products/dashboard").then((response) => response.data),
    enabled,
  })
}

export function useUsersDashboard(range: PeriodRange, enabled: boolean) {
  return useQuery({
    queryKey: [...userKeys.all, "dashboard", range],
    queryFn: () => api.get<UsersDashboard>("/users/dashboard", { params: range }).then((response) => response.data),
    enabled,
  })
}

// sales espera groupBy en minuscula y los dias de la tienda (Argentina)
const salesParams = (range: PeriodRange) => ({ from: range.from, to: range.to, groupBy: range.groupBy.toLowerCase() })

export function useSalesSummary(range: PeriodRange, enabled: boolean) {
  return useQuery({
    queryKey: [...salesKeys.all, "summary", range],
    queryFn: () =>
      api.get<SalesSummary>("/sales/admin/reports/summary", { params: salesParams(range) }).then((response) => response.data),
    enabled,
  })
}

export function useSalesTopProducts(range: PeriodRange, enabled: boolean) {
  return useQuery({
    queryKey: [...salesKeys.all, "top", range],
    queryFn: () =>
      api
        .get<{ items: SalesTopProduct[] }>("/sales/admin/reports/products", {
          params: { from: range.from, to: range.to, sort: "units", limit: 5 },
        })
        .then((response) => response.data.items),
    enabled,
  })
}
