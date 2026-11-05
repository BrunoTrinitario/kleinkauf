import type { RoleDto } from "./types"

// Permission areas (bit mask per role, see docs/permisos.md). ADMINISTRADOR has all of them
export const PERMISSIONS = {
  PRODUCTOS: 1,
  PRECIOS: 2,
  STOCK: 4,
  COMBOS: 8,
  USUARIOS: 16,
} as const

export type PermissionArea = keyof typeof PERMISSIONS

// The ones that open the products dashboard: the backend requires any of them to read with exact stock
export const PRODUCT_PANEL_AREAS: PermissionArea[] = ["PRODUCTOS", "PRECIOS", "STOCK", "COMBOS"]

/*
What the active role can do (the one chosen in "Cambiar rol"). Routes and the menu follow the active role, so
switching roles lets you see the app as that role. The backend validates with the union of the roles: the UI never shows
more, at most less.
 */
export function roleCan(roles: RoleDto[] | undefined, activeRole: string | null, area: PermissionArea): boolean {
  const role = (roles ?? []).find((item) => item.type === activeRole)
  return !!role && (role.permissions & PERMISSIONS[area]) !== 0
}
