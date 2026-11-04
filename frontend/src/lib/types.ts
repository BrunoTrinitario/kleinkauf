export type UserRole = "ADMINISTRADOR" | "CLIENTE" | "EMPLEADO"

export interface RoleDto {
  type: string
  permissions: number
}

export interface LoginUserDto {
  email: string
  password: string
}

export interface LoggedUser {
  name: string
  id: number
  email: string
  token: string
  roles: RoleDto[]
}

export interface User {
  id: number
  email: string
  name: string
  roles: RoleDto[]
  avatar?: string
}

export interface ProfileFormState {
  id: number
  name: string
  email: string
  password: string
}

// Tienda vista desde el panel de la plataforma (/api/v1/platform/admin/tenants)
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

// Panel de la plataforma (GET /api/v1/platform/admin/dashboard). period = primer dia del periodo
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

// Uso por tienda: users (/platform/admin/usage) y products (/platform/products/usage), juntos por schema
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

// Producto del panel (GET /api/v1/products): con stock exacto
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

// Catalogo publico (/api/v1/catalog): disponibilidad en vez de stock, price null = consultar
export type Availability = "DISPONIBLE" | "POCAS_UNIDADES" | "AGOTADO"

export type CatalogSort = "name" | "price_asc" | "price_desc"

export interface CatalogProduct {
  // Lo usa el carrito para armar el pedido (el link y la pagina van por slug)
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

// Pagina de un listado del backend (catalogo, usuarios y productos del panel). page empieza en 0
export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type CatalogPage = Page<CatalogProduct>

// Combo del catalogo publico (/catalog/bundles): listPrice es la suma de sus productos (para mostrar el ahorro),
// null si alguno no tiene precio. image: la primera imagen del primer producto que tenga
export interface CatalogBundle {
  id: number
  name: string
  price: number
  listPrice: number | null
  availability: Availability
  image: string | null
  products: CatalogBundleProduct[]
}

// Producto dentro de un combo. price: el del producto suelto (null = consultar); image: su primera imagen.
// Opcionales porque un catalogo cacheado antes de sumarlos puede no traerlos (dura hasta una hora)
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

// Producto sin metadatos cargados: campos vacios (no un "Unknown" que se guardaria como dato real)
export const defaultProductMetadata: ProductMetadata = {
  material: "",
  color: "",
  style: "",
  height: 0,
  width: 0,
  length: 0,
  weight: 0,
}

// Completa con vacio lo que venga en null (filas viejas de la base)
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


// Ventas (/api/v1/sales, docs/backend/sales.md). Las fechas llegan sin zona (hora del servidor): formatServerDateTime
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

// Datos para transferir. alias o cbu pueden venir en null
export interface PaymentDetails {
  alias: string | null
  cbu: string | null
  holder: string | null
  bank: string | null
}

// GET /sales/checkout: sin paymentConfigured no se puede comprar
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

// 409 de POST /sales/orders cuando cambio algun precio (el pedido no se crea)
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

// El pedido visto por el comprador (lookup, comprobante y cancelar). payment solo mientras se puede subir el comprobante
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

// Renglon de "mis pedidos" (y de la lista del panel de ventas)
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

// Panel de ventas (solo ADMINISTRADOR). Precio de lista null = el producto era "a consultar"; costo null = sin cargar
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

// userId null = el comprador invitado o el sistema (vencimiento)
export interface AdminOrderHistoryEntry extends OrderHistoryEntry {
  userId: number | null
}

export interface OrderReceipt {
  id: number
  contentType: string
  size: number
  createdAt: string
}

// buyer puede venir vacio en una venta del local (consumidor final)
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

// Filtros de GET /sales/admin/orders. from / to: yyyy-MM-dd (dias de la tienda, inclusive)
export interface AdminOrderFilters {
  status?: OrderStatus | ""
  channel?: OrderChannel | ""
  q?: string
  from?: string
  to?: string
  page: number
}

// Datos para cobrar. Los vacios se guardan como null; expirationHours null = 48
export interface SalesSettings {
  alias: string | null
  cbu: string | null
  holder: string | null
  bank: string | null
  expirationHours: number | null
}
