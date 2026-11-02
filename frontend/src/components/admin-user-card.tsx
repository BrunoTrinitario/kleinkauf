import type { RoleDto, User, UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Pencil, Trash2, User as UserIcon, Mail } from "lucide-react"

const roleColors: Record<UserRole, string> = {
  CLIENTE: "bg-muted text-muted-foreground",
  EMPLEADO: "bg-sky-wash text-cyan-edge",
  ADMINISTRADOR: "bg-foreground text-background",
}

const roleLabels: Record<UserRole, string> = {
  CLIENTE: strings.roles.cliente,
  EMPLEADO: strings.roles.empleado,
  ADMINISTRADOR: strings.roles.administrador,
}

// Orden fijo de los badges, del rol con mas permisos al de menos
const ROLE_ORDER: UserRole[] = ["ADMINISTRADOR", "EMPLEADO", "CLIENTE"]

const resolveRoles = (roles: RoleDto[] | undefined): UserRole[] => {
  const types = new Set(roles?.map((role) => role.type))
  return ROLE_ORDER.filter((role) => types.has(role))
}

interface AdminUserCardProps {
  user: User
  onEdit?: (user: User) => void
  onDelete?: (user: User) => void
  disableDelete?: boolean
}

export function AdminUserCard({ user, onEdit, onDelete, disableDelete }: AdminUserCardProps) {
  const roles = resolveRoles(user.roles)

  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 rounded-lg border border-border">
      <div className="flex items-center gap-4">
        <div className="h-10 w-10 rounded-full bg-muted flex items-center justify-center">
          <UserIcon className="h-5 w-5 text-muted-foreground" />
        </div>
        <div>
          <p className="font-medium text-foreground">{user.name}</p>
          <p className="text-sm text-muted-foreground flex items-center gap-1">
            <Mail className="h-3 w-3" />
            {user.email}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <div className="flex flex-wrap gap-1.5">
          {roles.length > 0 ? (
            roles.map((role) => (
              <Badge key={role} className={roleColors[role]}>
                {roleLabels[role]}
              </Badge>
            ))
          ) : (
            <Badge className="bg-muted text-muted-foreground">{strings.roles.noRole}</Badge>
          )}
        </div>
        <Button
          variant="ghost"
          size="icon"
          onClick={() => onEdit?.(user)}
          disabled={!onEdit}
          aria-label={strings.adminUserCard.editAria}
        >
          <Pencil className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="text-destructive hover:text-destructive"
          onClick={() => onDelete?.(user)}
          disabled={!onDelete || disableDelete}
          aria-label={strings.adminUserCard.deleteAria}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
