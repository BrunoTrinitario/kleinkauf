export type UserRole = "ADMINISTRADOR" | "CLIENTE" | "EMPLEADO"

export interface RoleDto {
  type: string
  permissions: number
}

export interface LoginUserDto {
  email: string
  password: string
}

// token: the session one (1 hour). refreshToken: renews it without asking for the password again (7 days)
export interface LoggedUser {
  name: string
  id: number
  email: string
  token: string
  refreshToken?: string
  roles: RoleDto[]
}

export interface User {
  id: number
  email: string
  name: string
  roles: RoleDto[]
  avatar?: string | null
}

export interface ProfileFormState {
  id: number
  name: string
  email: string
  password: string
}

// Store as seen from the platform dashboard (/api/v1/platform/admin/tenants)
export interface PlatformTenant {
  id: number
  identifier: string
  schema: string
  url: string
  configId: string | null
  enabled: boolean
  createdAt: string
}

export interface NewTenantDto {
  identifier: string
  ownerName: string
  ownerEmail: string
  ownerPassword: string
}

// Platform dashboard (GET /api/v1/platform/admin/dashboard). period = first day of the period
export type PeriodGroupBy = "DAY" | "WEEK" | "MONTH"

export interface PlatformDashboard {
  tenants: { total: number; enabled: number; disabled: number; deleted: number }
  registrations: {
    from: string
    to: string
    groupBy: PeriodGroupBy
    periods: { period: string; count: number }[]
  }
}

// Per-store usage: users (/platform/admin/usage) and products (/platform/products/usage), joined by schema
export interface TenantUsersUsage {
  id: number
  identifier: string
  schema: string
  enabled: boolean
  createdAt: string
  users: number
  activeUsers: number
  lastActivity: string | null
}

export interface TenantProductsUsage {
  schema: string
  products: number
  bundles: number
}

export interface Superadmin {
  id: number
  name: string
  email: string
  active: boolean
  createdAt: string
  lastLogin: string | null
}

// Bug report someone sends from a store's footer (POST /api/v1/bug-reports)
export interface NewBugReport {
  description: string
  email?: string
  pageUrl: string
}

// Report in the platform dashboard. store/storeUrl: null if it was sent without a store.
// userId/reporterName: the user if they had an open session. resolvedAt: null = pending
export interface BugReport {
  id: number
  store: string | null
  storeUrl: string | null
  userId: number | null
  reporterName: string | null
  email: string | null
  description: string
  pageUrl: string | null
  userAgent: string | null
  createdAt: string
  resolvedAt: string | null
}

export type BugReportStatus = "pending" | "resolved" | "all"

// pending: how many remain unresolved in total, whatever the filter
export interface BugReportPage extends Page<BugReport> {
  pending: number
}

// Dashboard product (GET /api/v1/products): with exact stock
export interface Product {
  id: string
  name: string
  slug?: string
  description: string
  category: string
  stock: number
  minStock: number
  price: number | null
  featured?: boolean
  images: string[]
}

export interface ProductMetadata {
  material: string
  color: string
  style: string
  height: number
  width: number
  length: number
  weight: number
}

// Public catalog (/api/v1/catalog): availability instead of stock, price null = price on request
export type Availability = "DISPONIBLE" | "POCAS_UNIDADES" | "AGOTADO"

export type CatalogSort = "name" | "price_asc" | "price_desc"

export interface CatalogProduct {
  // Used by the cart to build the order (the link and the page go by slug)
  id: number
  slug: string
  name: string
  category: string
  description: string
  price: number | null
  availability: Availability
  featured: boolean
  images: string[]
}

export interface CatalogProductDetail extends CatalogProduct {
  metadata: ProductMetadata | null
}

// A page of a backend listing (catalog, dashboard users and products). page starts at 0
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type CatalogPage = Page<CatalogProduct>

// Public catalog bundle (/catalog/bundles): listPrice is the sum of its products (to show the savings),
// null if any has no price. image: the first image of the first product that has one
export interface CatalogBundle {
  id: number
  name: string
  price: number
  listPrice: number | null
  availability: Availability
  image: string | null
  products: CatalogBundleProduct[]
}

// Product inside a bundle. price: the standalone product's (null = price on request); image: its first image.
// Optional because a catalog cached before they were added may not include them (lasts up to an hour)
export interface CatalogBundleProduct {
  slug: string
  name: string
  quantity: number
  category?: string | null
  description?: string | null
  price?: number | null
  image?: string | null
}

export interface CatalogCategory {
  name: string
  products: number
}

// Product without metadata: empty fields (not an "Unknown" that would be stored as real data)
export const defaultProductMetadata: ProductMetadata = {
  material: "",
  color: "",
  style: "",
  height: 0,
  width: 0,
  length: 0,
  weight: 0,
}

// Fills with empty values whatever comes as null (old database rows)
export function normalizeMetadata(data: Partial<ProductMetadata> | null | undefined): ProductMetadata {
  return {
    material: data?.material ?? "",
    color: data?.color ?? "",
    style: data?.style ?? "",
    height: data?.height ?? 0,
    width: data?.width ?? 0,
    length: data?.length ?? 0,
    weight: data?.weight ?? 0,
  }
}


// Sales (/api/v1/sales, docs/backend/sales.md). Dates arrive without a zone (server time): formatServerDateTime
export type OrderItemType = "PRODUCT" | "BUNDLE"

export type OrderStatus =
  | "PENDIENTE_PAGO"
  | "PAGO_EN_REVISION"
  | "RECHAZADO"
  | "PAGADO"
  | "EN_PREPARACION"
  | "LISTO"
  | "ENTREGADO"
  | "CANCELADO"
  | "VENCIDO"

export type DeliveryType = "RETIRO" | "ENVIO"

export type OrderChannel = "ONLINE" | "LOCAL"

export type PaymentMethod = "EFECTIVO" | "TRANSFERENCIA" | "DEBITO" | "CREDITO" | "OTRO"

export const PAYMENT_METHODS: PaymentMethod[] = ["EFECTIVO", "TRANSFERENCIA", "DEBITO", "CREDITO", "OTRO"]

// Transfer details. alias or cbu may come as null
export interface PaymentDetails {
  alias: string | null
  cbu: string | null
  holder: string | null
  bank: string | null
}

// GET /sales/checkout: without paymentConfigured nothing can be bought
export interface CheckoutInfo {
  paymentConfigured: boolean
  payment: PaymentDetails | null
  expirationHours: number
  deliveryTypes: DeliveryType[]
  maxLines: number
  maxQuantity: number
}

export interface Buyer {
  name: string
  email: string
  phone: string
}

export interface Address {
  street: string
  number: string
  apartment?: string | null
  city: string
  province: string
  postalCode: string
}

export interface NewOrder {
  items: { type: OrderItemType; id: number; quantity: number; price: number }[]
  buyer: Buyer
  deliveryType: DeliveryType
  address?: Address
  notes?: string
}

// 201 de POST /sales/orders
export interface CreatedOrder {
  number: string
  status: OrderStatus
  total: number
  expiresAt: string | null
  payment: PaymentDetails | null
}

// 409 from POST /sales/orders when some price changed (the order is not created)
export interface PriceChange {
  type: OrderItemType
  id: number
  name: string
  expectedPrice: number
  currentPrice: number | null
}

export interface OrderHistoryEntry {
  status: OrderStatus
  createdAt: string
  reason: string | null
}

// The order as seen by the buyer (lookup, receipt and cancel). payment only while the receipt can be uploaded
export interface Order {
  number: string
  status: OrderStatus
  createdAt: string
  expiresAt: string | null
  buyer: Buyer
  deliveryType: DeliveryType
  address: Address | null
  notes: string | null
  items: { type: OrderItemType; id: number; name: string; quantity: number; unitPrice: number; subtotal: number }[]
  discount: number
  total: number
  payment: PaymentDetails | null
  history: OrderHistoryEntry[]
  receipts: number
}

// Row of "my orders" (and of the sales dashboard list)
export interface OrderSummary {
  number: string
  status: OrderStatus
  channel: OrderChannel
  paymentMethod: PaymentMethod | null
  buyerName: string
  buyerEmail: string
  units: number
  total: number
  stockReview: boolean
  createdAt: string
}

// Sales dashboard (ADMINISTRADOR only). List price null = the product was "a consultar"; cost null = not set
export interface AdminOrderItem {
  type: OrderItemType
  id: number
  name: string
  quantity: number
  listPrice: number | null
  unitPrice: number
  unitCost: number | null
  subtotal: number
}

// userId null = the guest buyer or the system (expiry)
export interface AdminOrderHistoryEntry extends OrderHistoryEntry {
  userId: number | null
}

export interface OrderReceipt {
  id: number
  contentType: string
  size: number
  createdAt: string
}

// buyer may come empty in an in-store sale (final consumer)
export interface AdminOrder {
  number: string
  status: OrderStatus
  channel: OrderChannel
  paymentMethod: PaymentMethod | null
  buyer: Partial<Buyer> | null
  userId: number | null
  deliveryType: DeliveryType
  address: Address | null
  notes: string | null
  items: AdminOrderItem[]
  discount: number
  total: number
  stockReview: boolean
  expiresAt: string | null
  createdAt: string
  updatedAt: string
  history: AdminOrderHistoryEntry[]
  receipts: OrderReceipt[]
}

// Filters of GET /sales/admin/orders. from / to: yyyy-MM-dd (store days, inclusive)
export interface AdminOrderFilters {
  status?: OrderStatus | ""
  channel?: OrderChannel | ""
  paymentMethod?: PaymentMethod | ""
  // true = only the in-store sales made without enough stock
  stockReview?: boolean
  q?: string
  from?: string
  to?: string
  page: number
}

// Store settings (GET /catalog/store, public; GET/PUT /store/settings, ADMINISTRADOR).
// name is never null (without a custom name it is derived from the identifier); customName is the one the admin set.
// icon: file under /products/images/ or null
// The store's social networks: the profile link of each one, or null if it does not have it
export interface SocialLinks {
  instagram: string | null
  x: string | null
  facebook: string | null
  tiktok: string | null
}

export interface StoreConfig {
  name: string
  customName: string | null
  theme: string
  icon: string | null
  showIconInHeader: boolean
  showIconAsFavicon: boolean
  socials: SocialLinks
}

// socials: what the administrator typed (the link or just the user); "" removes the network
export interface UpdateStoreConfig {
  name: string | null
  theme: string
  showIconInHeader: boolean
  showIconAsFavicon: boolean
  socials: Record<keyof SocialLinks, string>
}

// Payment collection details. Empty ones are stored as null; expirationHours null = 48
export interface SalesSettings {
  alias: string | null
  cbu: string | null
  holder: string | null
  bank: string | null
  expirationHours: number | null
}

// POST /sales/admin/orders: in-store sale already charged. price: price charged (null = the current one; required if the
// product is "a consultar"). discount: amount in pesos over the subtotal. buyer optional (final consumer)
export interface NewLocalSale {
  items: { type: OrderItemType; id: number; quantity: number; price: number | null }[]
  discount: number | null
  paymentMethod: PaymentMethod
  status: "PAGADO" | "ENTREGADO"
  buyer: Partial<Buyer> | null
  deliveryType: DeliveryType
  address?: Address
  notes?: string
}
