import type { BundlePricing, BundlePricingType } from "@/api/bundles"

// What is typed in the dashboard: the type and a single field (fixed price, percentage or amount)
export interface PricingDraft {
  type: BundlePricingType
  value: string
}

export const emptyPricing: PricingDraft = { type: "FIXED", value: "" }

// The draft from a saved bundle (without pricingType, from before: fixed price)
export function pricingDraftOf(bundle: BundlePricing): PricingDraft {
  const type = bundle.pricingType ?? "FIXED"
  const value = type === "FIXED" ? bundle.price : bundle.discount
  return { type, value: value != null ? String(value) : "" }
}

// What is sent to the backend; undefined = not valid. FIXED accepts empty (no price); discounts do not
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

// Final price, same as BundleFactory.price in products (for the preview; the backend's is the one that counts).
// list: sum of the products, null if any has no price
export function bundlePrice(pricing: BundlePricing, list: number | null): number | null {
  if (pricing.pricingType === "FIXED") return pricing.price
  if (pricing.discount == null || list == null) return null
  return pricing.pricingType === "PERCENT"
    ? round2((list * (100 - pricing.discount)) / 100)
    : round2(Math.max(list - pricing.discount, 0))
}
