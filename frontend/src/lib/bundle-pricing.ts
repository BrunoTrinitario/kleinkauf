import type { BundlePricing, BundlePricingType } from "@/api/bundles"

// Lo que se escribe en el panel: el tipo y un solo campo (precio fijo, porcentaje o monto)
export interface PricingDraft {
  type: BundlePricingType
  value: string
}

export const emptyPricing: PricingDraft = { type: "FIXED", value: "" }

// El borrador a partir de un combo guardado (sin pricingType, de antes: precio fijo)
export function pricingDraftOf(bundle: BundlePricing): PricingDraft {
  const type = bundle.pricingType ?? "FIXED"
  const value = type === "FIXED" ? bundle.price : bundle.discount
  return { type, value: value != null ? String(value) : "" }
}

// Lo que se manda al backend; undefined = no es valido. FIXED acepta vacio (sin precio); los descuentos, no
export function parsePricing(draft: PricingDraft): BundlePricing | undefined {
  const text = draft.value.trim()
  if (draft.type === "FIXED") {
    if (text === "") return { pricingType: "FIXED", price: null, discount: null }
    const price = Number(text)
    return Number.isFinite(price) && price >= 0 ? { pricingType: "FIXED", price, discount: null } : undefined
  }
  const discount = Number(text)
  const valid = text !== "" && Number.isFinite(discount) && discount >= 0 && (draft.type !== "PERCENT" || discount <= 100)
  return valid ? { pricingType: draft.type, price: null, discount } : undefined
}

const round2 = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100

// Precio final, igual que BundleFactory.price en products (para la vista previa; el que vale es el del backend).
// list: suma de los productos, null si alguno no tiene precio
export function bundlePrice(pricing: BundlePricing, list: number | null): number | null {
  if (pricing.pricingType === "FIXED") return pricing.price
  if (pricing.discount == null || list == null) return null
  return pricing.pricingType === "PERCENT"
    ? round2((list * (100 - pricing.discount)) / 100)
    : round2(Math.max(list - pricing.discount, 0))
}
