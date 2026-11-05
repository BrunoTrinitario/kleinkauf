import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import { catalogKeys } from "./catalog"

// Bundles (/api/v1/bundles): reading requires any dashboard area, changing requires COMBOS
export const bundleKeys = {
  all: ["bundles"] as const,
}

// productid comes in lowercase in the backend response. price: the product's; image: its first image
export interface BundleProduct {
  productid: number
  productName: string
  quantity: number
  price: number | null
  image: string | null
}

// FIXED: stored price. PERCENT / AMOUNT: discount over the sum of the products (the price follows the products)
export type BundlePricingType = "FIXED" | "PERCENT" | "AMOUNT"

// How the price is stored. FIXED uses price (null = no price); PERCENT and AMOUNT use discount
export interface BundlePricing {
  pricingType: BundlePricingType
  price: number | null
  discount: number | null
}

// price: the final price (with the discount applied). listPrice: sum of price x quantity (null if some product has no
// price). available: how many can be put together
export interface Bundle extends BundlePricing {
  id: number
  name: string
  enabled: boolean
  listPrice: number | null
  available: number
  products: BundleProduct[]
}

// Created complete in one go: name, products (at least one) and price
export interface NewBundle extends BundlePricing {
  name: string
  enabled: boolean
  products: { productId: number; quantity: number }[]
}

export function useBundles(enabled: boolean) {
  return useQuery({
    queryKey: bundleKeys.all,
    queryFn: () => api.get<Bundle[]>("/bundles/all").then((response) => response.data),
    enabled,
    meta: { errorMessage: strings.adminBundles.loadError },
  })
}

// A bundle changes the dashboard and what can be bought in the catalog
function useBundleMutation<T>(mutationFn: (variables: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: bundleKeys.all }),
        queryClient.invalidateQueries({ queryKey: catalogKeys.all }),
      ]),
  })
}

export const useCreateBundle = () => useBundleMutation((bundle: NewBundle) => api.post<Bundle>("/bundles", bundle))

export const useSetBundleEnabled = () =>
  useBundleMutation(({ id, enabled }: { id: number; enabled: boolean }) =>
    api.put<Bundle>(`/bundles/${id}/enabled`, { enabled })
  )

export const useSetBundlePrice = () =>
  useBundleMutation(({ id, ...pricing }: { id: number } & BundlePricing) => api.put(`/bundles/${id}/price`, pricing))

// Adds the product or changes its quantity
export const useSetBundleProduct = () =>
  useBundleMutation(({ id, productId, quantity }: { id: number; productId: number; quantity: number }) =>
    api.put(`/bundles/${id}/product`, { productId, quantity })
  )

export const useRemoveBundleProduct = () =>
  useBundleMutation(({ id, productId }: { id: number; productId: number }) =>
    api.delete(`/bundles/${id}/product`, { data: { productId } })
  )

export const useDeleteBundle = () => useBundleMutation((id: number) => api.delete(`/bundles/${id}`))
