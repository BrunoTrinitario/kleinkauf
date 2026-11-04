import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { strings } from "@/lib/strings"
import { catalogKeys } from "./catalog"

// Combos (/api/v1/bundles): leer pide cualquier area del panel, cambiar pide COMBOS
export const bundleKeys = {
  all: ["bundles"] as const,
}

// productid va en minuscula en la respuesta del backend. price: el del producto; image: su primera imagen
export interface BundleProduct {
  productid: number
  productName: string
  quantity: number
  price: number | null
  image: string | null
}

// FIXED: precio guardado. PERCENT / AMOUNT: descuento sobre la suma de los productos (el precio sigue a los productos)
export type BundlePricingType = "FIXED" | "PERCENT" | "AMOUNT"

// Como se guarda el precio. FIXED usa price (null = sin precio); PERCENT y AMOUNT usan discount
export interface BundlePricing {
  pricingType: BundlePricingType
  price: number | null
  discount: number | null
}

// price: el precio final (con el descuento aplicado). listPrice: suma de precio x cantidad (null si algun producto no
// tiene precio). available: cuantos se pueden armar
export interface Bundle extends BundlePricing {
  id: number
  name: string
  enabled: boolean
  listPrice: number | null
  available: number
  products: BundleProduct[]
}

// Se crea completo de una vez: nombre, productos (al menos uno) y precio
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

// Un combo cambia el panel y lo que se puede comprar en el catalogo
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

// Agrega el producto o cambia su cantidad
export const useSetBundleProduct = () =>
  useBundleMutation(({ id, productId, quantity }: { id: number; productId: number; quantity: number }) =>
    api.put(`/bundles/${id}/product`, { productId, quantity })
  )

export const useRemoveBundleProduct = () =>
  useBundleMutation(({ id, productId }: { id: number; productId: number }) =>
    api.delete(`/bundles/${id}/product`, { data: { productId } })
  )

export const useDeleteBundle = () => useBundleMutation((id: number) => api.delete(`/bundles/${id}`))
