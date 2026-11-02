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

export interface Product {
  id: string
  name: string
  description: string
  category: string
  stock: number
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

export const defaultProductMetadata: ProductMetadata = {
  material: "Unknown",
  color: "Unknown",
  style: "Unknown",
  height: 0,
  width: 0,
  length: 0,
  weight: 0,
}

