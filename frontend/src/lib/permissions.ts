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

// Con varios roles vale la union de sus mascaras (igual que en el backend).
// La UI lo usa para no mostrar lo que no se puede usar; el backend igual valida
export function hasPermission(roles: RoleDto[] | undefined, area: PermissionArea): boolean {
  return (roles ?? []).some((role) => (role.permissions & PERMISSIONS[area]) !== 0)
}
