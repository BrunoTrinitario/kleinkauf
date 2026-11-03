import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
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
