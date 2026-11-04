import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import type { Page, Product, ProductMetadata } from "@/lib/types"
import { normalizeMetadata } from "@/lib/types"
import { catalogKeys } from "./catalog"

// Dashboard products (/api/v1/products): with exact stock, they require a dashboard area
export const productKeys = {
  all: ["products"] as const,
  list: (params: ProductListParams) => [...productKeys.all, "list", params] as const,
  metadata: (id: string) => [...productKeys.all, id, "metadata"] as const,
  images: (id: string) => [...productKeys.all, id, "images"] as const,
  pricing: (id: string) => [...productKeys.all, id, "pricing"] as const,
  movements: (id: string) => [...productKeys.all, id, "movements"] as const,
  lowStock: () => [...productKeys.all, "low-stock"] as const,
  featured: () => [...productKeys.all, "featured"] as const,
}

export type StockMovementType = "ENTRADA" | "SALIDA" | "AJUSTE"

// quantity is the applied difference: negative on a SALIDA and on an AJUSTE that lowers the stock
export interface StockMovement {
  id: number
  productId: number
  type: StockMovementType
  quantity: number
  stockAfter: number
  reason: string | null
  userId: number | null
  createdAt: string
}

export interface NewStockMovement {
  type: StockMovementType
  // ENTRADA and SALIDA: units (> 0). AJUSTE: the counted stock (>= 0)
  quantity: number
  // Required on AJUSTE
  reason?: string
}

export interface LowStockProduct {
  id: number
  name: string
  stock: number
  minStock: number
}

// Import result: with errors (400) nothing is imported. row 0 = the whole file, 1 = the headers
export interface ImportResult {
  created: number
  updated: number
  errors: { row: number; message: string }[]
}

export interface ProductListParams {
  q?: string
  category?: string
  page?: number
}

export interface ProductImage {
  id: number
  path: string
  order: number
}

export interface ProductPricing {
  price: number | null
  cost: number | null
}

// A product change shows in the dashboard and in the public catalog (categories with their count included)
export function invalidateProducts(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: productKeys.all }),
    queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
  ])
}

export function useAdminProducts(params: ProductListParams, enabled: boolean) {
  return useQuery({
    queryKey: productKeys.list(params),
    queryFn: () =>
      api
        .get<Page<Product>>("/products", {
          params: { q: params.q || undefined, category: params.category || undefined, page: params.page },
        })
        .then((response) => response.data),
    enabled,
    placeholderData: keepPreviousData,
    meta: { errorMessage: strings.errors.loadProducts },
  })
}

// Without metadata the backend responds empty: the defaults are used
export function useProductMetadata(id: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: productKeys.metadata(id ?? ""),
    queryFn: () =>
      api
        .get<ProductMetadata | "">(`/products/${id}/metadata`)
        .then((response) => normalizeMetadata(response.data === "" ? null : response.data)),
    enabled: enabled && !!id,
  })
}

export function useProductImages(id: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: productKeys.images(id ?? ""),
    queryFn: () => api.get<ProductImage[]>(`/products/${id}/images`).then((response) => response.data ?? []),
    enabled: enabled && !!id,
    meta: { errorMessage: strings.errors.loadImages },
  })
}

// The cost does not come in the listing: only with the PRECIOS permission
export function useProductPricing(id: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: productKeys.pricing(id ?? ""),
    queryFn: () => api.get<ProductPricing>(`/products/${id}/pricing`).then((response) => response.data),
    enabled: enabled && !!id,
  })
}

export function useDeleteProduct() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`/products/${id}`),
    onSuccess: () => invalidateProducts(queryClient),
  })
}

// --- Stock (area STOCK) ---

export function useStockMovements(id: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: productKeys.movements(id ?? ""),
    queryFn: () => api.get<StockMovement[]>(`/products/${id}/stock/movements`).then((response) => response.data),
    enabled: enabled && !!id,
  })
}

// Lowest stock to highest. Also provides the menu counter
export function useLowStock(enabled: boolean) {
  return useQuery({
    queryKey: productKeys.lowStock(),
    queryFn: () => api.get<LowStockProduct[]>("/products/stock/low").then((response) => response.data),
    enabled,
  })
}

// Changes the listing stock, the low stock and the catalog availability. 409: the SALIDA leaves the stock negative
export function useRegisterMovement(productId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (movement: NewStockMovement) =>
      api.post<StockMovement>(`/products/${productId}/stock/movements`, movement).then((response) => response.data),
    onSuccess: () => invalidateProducts(queryClient),
  })
}

// --- Featured (PRODUCTOS area) ---

// In the order they are shown on the home page: first by order, those without one last by name
export function useFeaturedProducts(enabled: boolean) {
  return useQuery({
    queryKey: productKeys.featured(),
    queryFn: () => api.get<Product[]>("/products/featured").then((response) => response.data),
    enabled,
  })
}

const putFeatured = (id: string, featured: boolean, order?: number) =>
  api.put(`/products/${id}/featured`, { featured, order })

// A new featured product goes last: its order is the count that already exists
export function useSetFeatured() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, featured, order }: { id: string; featured: boolean; order?: number }) =>
      putFeatured(id, featured, order),
    onSuccess: () => invalidateProducts(queryClient),
  })
}

// The backend does not return the stored order, only the sorted list: 0, 1, 2... is stored in the requested order.
// All of them are sent, not just the ones that moved: one without an order would end up last (unordered ones go after)
export function useReorderFeatured() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (ids: string[]) => {
      await Promise.all(ids.map((id, index) => putFeatured(id, true, index)))
    },
    onSettled: () => invalidateProducts(queryClient),
  })
}

// --- CSV import and export (PRODUCTOS and PRECIOS) ---

// The file comes from the backend (with BOM and ";"): it is downloaded as is, with the name it sends
async function downloadCsv(path: string, fallbackName: string) {
  const response = await api.get<Blob>(path, { responseType: "blob" })
  const disposition = String(response.headers["content-disposition"] ?? "")
  const name = disposition.match(/filename="?([^";]+)"?/)?.[1] ?? fallbackName
  const url = URL.createObjectURL(response.data)
  const link = document.createElement("a")
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}

export const exportProducts = () => downloadCsv("/products/export", "productos.csv")
export const downloadImportTemplate = () => downloadCsv("/products/import/template", "plantilla-productos.csv")

// With errors the backend responds 400 with the same format: it is returned as a result, not as a failure
export function useImportProducts() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (file: File): Promise<ImportResult> => {
      const form = new FormData()
      form.append("file", file)
      try {
        return (await api.post<ImportResult>("/products/import", form)).data
      } catch (error) {
        const data = (error as { response?: { status?: number; data?: ImportResult } }).response
        if (data?.status === 400 && Array.isArray(data.data?.errors)) {
          return data.data
        }
        throw error
      }
    },
    onSuccess: (result) => {
      if (result.errors.length === 0) invalidateProducts(queryClient)
    },
  })
}

export function useDeleteProductImage(productId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (imageId: number) => api.delete(`/products/images/${imageId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.images(productId ?? "") })
      queryClient.invalidateQueries({ queryKey: catalogKeys.all })
    },
  })
}
