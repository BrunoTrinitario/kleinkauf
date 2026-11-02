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

