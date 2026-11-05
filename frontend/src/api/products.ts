import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { downloadCsv } from "@/lib/download"
import { strings } from "@/lib/strings"
import type { Page, Product, ProductMetadata } from "@/lib/types"
import { normalizeMetadata } from "@/lib/types"
import { catalogKeys } from "./catalog"

// Productos del panel (/api/v1/products): con stock exacto, piden un area del panel
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

// quantity es la diferencia aplicada: negativa en una SALIDA y en un AJUSTE que baja
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
  // ENTRADA y SALIDA: unidades (> 0). AJUSTE: el stock contado (>= 0)
  quantity: number
  // Obligatorio en AJUSTE
  reason?: string
}

export interface LowStockProduct {
  id: number
  name: string
  stock: number
  minStock: number
}

// Resultado de importar: con errores (400) no se importa nada. row 0 = el archivo entero, 1 = los encabezados
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

// Un cambio de productos se ve en el panel y en el catalogo publico (categorias con su cantidad incluidas)
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

// Sin metadatos el backend responde vacio: se usan los valores por defecto
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

// El costo no viene en el listado: solo con el permiso PRECIOS
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

// Del menor stock al mayor. Tambien da el contador del menu
export function useLowStock(enabled: boolean) {
  return useQuery({
    queryKey: productKeys.lowStock(),
    queryFn: () => api.get<LowStockProduct[]>("/products/stock/low").then((response) => response.data),
    enabled,
  })
}

// Cambia el stock del listado, el stock bajo y la disponibilidad del catalogo. 409: la salida deja el stock negativo
export function useRegisterMovement(productId: string | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (movement: NewStockMovement) =>
      api.post<StockMovement>(`/products/${productId}/stock/movements`, movement).then((response) => response.data),
    onSuccess: () => invalidateProducts(queryClient),
  })
}

// --- Destacados (area PRODUCTOS) ---

// En el orden en que se muestran en la home: primero por orden, los que no tienen al final por nombre
export function useFeaturedProducts(enabled: boolean) {
  return useQuery({
    queryKey: productKeys.featured(),
    queryFn: () => api.get<Product[]>("/products/featured").then((response) => response.data),
    enabled,
  })
}

const putFeatured = (id: string, featured: boolean, order?: number) =>
  api.put(`/products/${id}/featured`, { featured, order })

// Un destacado nuevo va al final: su orden es la cantidad que ya hay
export function useSetFeatured() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, featured, order }: { id: string; featured: boolean; order?: number }) =>
      putFeatured(id, featured, order),
    onSuccess: () => invalidateProducts(queryClient),
  })
}

// El backend no devuelve el orden guardado, solo la lista ordenada: se guarda 0, 1, 2... en el orden pedido.
// Van todos, no solo los que se movieron: uno sin orden quedaria al final (los sin orden van despues)
export function useReorderFeatured() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (ids: string[]) => {
      await Promise.all(ids.map((id, index) => putFeatured(id, true, index)))
    },
    onSettled: () => invalidateProducts(queryClient),
  })
}

// --- Importar y exportar CSV (PRODUCTOS y PRECIOS) ---

export const exportProducts = () => downloadCsv("/products/export", "productos.csv")
export const downloadImportTemplate = () => downloadCsv("/products/import/template", "plantilla-productos.csv")

// Con errores el backend responde 400 con el mismo formato: se devuelve como resultado, no como falla
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
