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

// Public catalog (/api/v1/catalog): no login. Any product change invalidates "catalog"
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

// Without categories the catalog keeps working, just without the filter: the error is not shown
export function useCatalogCategories() {
  return useQuery({
    queryKey: catalogKeys.categories(),
    queryFn: () => api.get<CatalogCategory[]>("/catalog/categories").then((response) => response.data),
    staleTime: 5 * 60_000,
  })
}

// When changing page or filter the previous page keeps showing until the new one arrives
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

// Home page featured products, in the order the store chose
export function useCatalogFeatured() {
  return useQuery({
    queryKey: [...catalogKeys.all, "featured"],
    queryFn: () => api.get<CatalogProduct[]>("/catalog/featured").then((response) => response.data),
  })
}

// Bundles that can be bought (enabled, with a price and available products)
export function useCatalogBundles() {
  return useQuery({
    queryKey: [...catalogKeys.all, "bundles"],
    queryFn: () => api.get<CatalogBundle[]>("/catalog/bundles").then((response) => response.data),
  })
}

// Detail by slug (metadata and images in order): requested by the modal and the product page
export function useCatalogProduct(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.product(slug),
    queryFn: () => api.get<CatalogProductDetail>(`/catalog/products/${slug}`).then((response) => response.data),
    enabled,
    staleTime: 5 * 60_000,
  })
}
