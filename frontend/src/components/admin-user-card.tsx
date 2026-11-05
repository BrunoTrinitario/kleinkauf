import type { RoleDto, User, UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Pencil, Trash2, Mail } from "lucide-react"
import { UserAvatar } from "@/components/user-avatar"

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

// Fixed badge order, from the role with the most permissions to the one with the fewest
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

  const badges =
    roles.length > 0 ? (
      roles.map((role) => (
        <Badge key={role} className={roleColors[role]}>
          {roleLabels[role]}
        </Badge>
      ))
    ) : (
      <Badge className="bg-muted text-muted-foreground">{strings.roles.noRole}</Badge>
    )

  // Always in one row: the buttons stay on the right on mobile too.
  // On mobile the roles go below the email; from sm, next to the buttons
  return (
    <div className="flex items-center justify-between gap-3 p-4 rounded-lg border border-border">
      <div className="flex items-center gap-3 sm:gap-4 min-w-0 flex-1">
        <UserAvatar name={user.name} avatar={user.avatar} className="h-10 w-10 shrink-0 text-sm" />
        <div className="min-w-0">
          <p className="font-medium text-foreground truncate">{user.name}</p>
          <p className="text-sm text-muted-foreground flex items-center gap-1 min-w-0">
            <Mail className="h-3 w-3 shrink-0" />
            <span className="truncate">{user.email}</span>
          </p>
          <div className="flex flex-wrap gap-1.5 mt-2 sm:hidden">{badges}</div>
        </div>
      </div>

      <div className="flex items-center gap-1 sm:gap-3 shrink-0">
        <div className="hidden sm:flex flex-wrap gap-1.5">{badges}</div>
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
