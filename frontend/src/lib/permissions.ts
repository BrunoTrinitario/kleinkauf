import type { RoleDto } from "./types"

// Areas de permisos (mascara de bits por rol, ver docs/permisos.md). El ADMINISTRADOR las tiene todas
export const PERMISSIONS = {
  PRODUCTOS: 1,
  PRECIOS: 2,
  STOCK: 4,
  COMBOS: 8,
  USUARIOS: 16,
} as const

export type PermissionArea = keyof typeof PERMISSIONS

// Las que abren el panel de productos: el backend pide cualquiera de ellas para leer con stock exacto
export const PRODUCT_PANEL_AREAS: PermissionArea[] = ["PRODUCTOS", "PRECIOS", "STOCK", "COMBOS"]

/*
Lo que puede hacer el rol activo (el que se elige en "Cambiar rol"). Las rutas y el menu siguen al rol activo, asi
cambiar de rol sirve para ver la app como ese rol. El backend valida con la union de los roles: la UI nunca muestra
de mas, a lo sumo de menos.
 */
export function roleCan(roles: RoleDto[] | undefined, activeRole: string | null, area: PermissionArea): boolean {
  const role = (roles ?? []).find((item) => item.type === activeRole)
  return !!role && (role.permissions & PERMISSIONS[area]) !== 0
}
