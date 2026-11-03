import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import type {
  CatalogBundle,
  CatalogCategory,
  CatalogPage,
  CatalogProduct,
  CatalogProductDetail,
  CatalogSort,
} from "@/lib/types"

// Catalogo publico (/api/v1/catalog): sin login. Cualquier cambio de productos invalida "catalog"
export const catalogKeys = {
  all: ["catalog"] as const,
  categories: () => [...catalogKeys.all, "categories"] as const,
  products: (params: CatalogParams) => [...catalogKeys.all, "products", params] as const,
  product: (slug: string) => [...catalogKeys.all, "product", slug] as const,
}

export interface CatalogParams {
  q?: string
  category?: string
  sort?: CatalogSort
  page?: number
}

// Sin categorias el catalogo sigue funcionando, solo sin el filtro: no se avisa el error
export function useCatalogCategories() {
  return useQuery({
    queryKey: catalogKeys.categories(),
    queryFn: () => api.get<CatalogCategory[]>("/catalog/categories").then((response) => response.data),
    staleTime: 5 * 60_000,
  })
}

// Al cambiar de pagina o filtro se sigue mostrando la pagina anterior hasta que llega la nueva
export function useCatalogProducts(params: CatalogParams) {
  return useQuery({
    queryKey: catalogKeys.products(params),
    queryFn: () =>
      api
        .get<CatalogPage>("/catalog/products", {
          params: { q: params.q || undefined, category: params.category || undefined, sort: params.sort, page: params.page },
        })
        .then((response) => response.data),
    placeholderData: keepPreviousData,
    meta: { errorMessage: strings.errors.loadProducts },
  })
}

// Destacados de la home, en el orden que eligio la tienda
export function useCatalogFeatured() {
  return useQuery({
    queryKey: [...catalogKeys.all, "featured"],
    queryFn: () => api.get<CatalogProduct[]>("/catalog/featured").then((response) => response.data),
  })
}

// Combos que se pueden comprar (habilitados, con precio y productos vigentes)
export function useCatalogBundles() {
  return useQuery({
    queryKey: [...catalogKeys.all, "bundles"],
    queryFn: () => api.get<CatalogBundle[]>("/catalog/bundles").then((response) => response.data),
  })
}

// Detalle por slug (metadatos e imagenes en orden): lo piden el modal y la pagina del producto
export function useCatalogProduct(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.product(slug),
    queryFn: () => api.get<CatalogProductDetail>(`/catalog/products/${slug}`).then((response) => response.data),
    enabled,
    staleTime: 5 * 60_000,
  })
}
