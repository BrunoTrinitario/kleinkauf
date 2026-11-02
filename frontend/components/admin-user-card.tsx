import type { RoleDto, User, UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Pencil, Trash2, User as UserIcon, Mail } from "lucide-react"

const roleColors: Record<UserRole, string> = {
  CLIENTE: "bg-muted text-muted-foreground",
  EMPLEADO: "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
  ADMINISTRADOR: "bg-foreground text-background",
}

const roleLabels: Record<UserRole, string> = {
  CLIENTE: strings.roles.cliente,
  EMPLEADO: strings.roles.empleado,
  ADMINISTRADOR: strings.roles.administrador,
}

const resolvePrimaryRole = (roles: RoleDto[] | undefined): UserRole | null => {
  const roleType = roles?.[0]?.type
  if (roleType === "CLIENTE" || roleType === "EMPLEADO" || roleType === "ADMINISTRADOR") {
    return roleType
  }
  return null
}

interface AdminUserCardProps {
  user: User
  onEdit?: (user: User) => void
  onDelete?: (user: User) => void
  disableDelete?: boolean
}

export function AdminUserCard({ user, onEdit, onDelete, disableDelete }: AdminUserCardProps) {
  const primaryRole = resolvePrimaryRole(user.roles)

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
        <Badge className={primaryRole ? roleColors[primaryRole] : "bg-muted text-muted-foreground"}>
          {primaryRole ? roleLabels[primaryRole] : strings.roles.noRole}
        </Badge>
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
