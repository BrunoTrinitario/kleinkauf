
import { useEffect, useMemo, useState } from "react"
import type { RoleDto, User, UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { NAME_MAX_LENGTH, isValidName } from "@/lib/validation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

const ROLE_OPTIONS: { value: UserRole; label: string }[] = [
  { value: "ADMINISTRADOR", label: strings.roles.administrador },
  { value: "EMPLEADO", label: strings.roles.empleado },
  { value: "CLIENTE", label: strings.roles.cliente },
]

interface AdminUserEditModalProps {
  user: User | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSave?: (user: User) => void
  token: string | null
}

export function AdminUserEditModal({
  user,
  open,
  onOpenChange,
  onSave,
  token,
}: AdminUserEditModalProps) {
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [roles, setRoles] = useState<RoleDto[]>([])

  const canSave = useMemo(
    () => !!onSave && !!user && !!token && isValidName(name),
    [onSave, user, token, name]
  )

  useEffect(() => {
    if (!user || !open) return
    setName(user.name ?? "")
    setEmail(user.email ?? "")
    setRoles(user.roles ?? [])
  }, [user, open])

  if (!user) return null

  const roleSet = new Set(roles.map((role) => role.type))

  const toggleRole = (roleName: UserRole) => {
    if (roleSet.has(roleName)) {
      setRoles((prev) => prev.filter((role) => role.type !== roleName))
    } else {
      setRoles((prev) => [...prev, { type: roleName, permissions: 0 }])
    }
  }

  const handleSave = () => {
    if (!onSave) return

    onSave({
      ...user,
      name,
      roles,
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.adminUserEdit.title}</DialogTitle>
          <DialogDescription>
            {strings.adminUserEdit.description}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminUserEdit.basicInfo}</h3>
            <div className="grid gap-3">
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminUserEdit.name}</label>
                <Input
                  value={name}
                  maxLength={NAME_MAX_LENGTH}
                  aria-invalid={!isValidName(name)}
                  onChange={(event) => setName(event.target.value)}
                />
                {!isValidName(name) ? (
                  <p className="text-xs text-destructive">{strings.validation.nameInvalid}</p>
                ) : null}
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminUserEdit.email}</label>
                {/* The backend does not allow changing the email */}
                <Input type="email" value={email} disabled readOnly />
                <p className="text-xs text-muted-foreground">{strings.adminUserEdit.emailLocked}</p>
              </div>
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminUserEdit.roles}</h3>
            <div className="grid gap-2">
              {ROLE_OPTIONS.map((role) => (
                <label key={role.value} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={roleSet.has(role.value)}
                    onChange={() => toggleRole(role.value)}
                  />
                  <span>{role.label}</span>
                </label>
              ))}
            </div>
          </section>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {strings.adminUserEdit.cancel}
          </Button>
          <Button onClick={handleSave} disabled={!canSave}>
            {strings.adminUserEdit.save}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
