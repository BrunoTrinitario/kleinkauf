
import { useMemo, useState } from "react"
import type { UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { NAME_MAX_LENGTH, isValidEmail, isValidName, isValidPassword } from "@/lib/validation"
import { PasswordRequirements } from "@/components/password-requirements"
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

interface AdminUserCreateModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreate?: (payload: { name: string; email: string; password: string; roles: string[] }) => void
  isCreating?: boolean
  error?: string | null
}

export function AdminUserCreateModal({
  open,
  onOpenChange,
  onCreate,
  isCreating = false,
  error,
}: AdminUserCreateModalProps) {
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [roles, setRoles] = useState<Set<UserRole>>(new Set(["CLIENTE"]))

  // The email shows the error only once something has been typed
  const emailError = email.trim() !== "" && !isValidEmail(email)
  const nameError = name.length > 0 && !isValidName(name)

  const canCreate = useMemo(
    () =>
      !!onCreate &&
      isValidName(name) &&
      isValidEmail(email) &&
      isValidPassword(password) &&
      roles.size > 0 &&
      !isCreating,
    [onCreate, name, email, password, roles, isCreating]
  )

  const toggleRole = (role: UserRole) => {
    setRoles((prev) => {
      const next = new Set(prev)
      if (next.has(role)) {
        next.delete(role)
      } else {
        next.add(role)
      }
      return next
    })
  }

  const resetForm = () => {
    setName("")
    setEmail("")
    setPassword("")
    setRoles(new Set(["CLIENTE"]))
  }

  const handleCreate = () => {
    if (!onCreate || !canCreate) return
    // The password goes as is: spaces count, as on sign-up
    onCreate({
      name: name.trim(),
      email: email.trim(),
      password,
      roles: Array.from(roles),
    })
  }

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
    if (!nextOpen) {
      resetForm()
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.adminUserCreate.title}</DialogTitle>
          <DialogDescription>
            {strings.adminUserCreate.description}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {error ? (
            <p className="text-sm text-destructive">{error}</p>
          ) : null}
          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminUserCreate.basicInfo}</h3>
            <div className="grid gap-3">
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminUserCreate.name}</label>
                <Input
                  value={name}
                  maxLength={NAME_MAX_LENGTH}
                  aria-invalid={nameError}
                  onChange={(event) => setName(event.target.value)}
                />
                {nameError ? (
                  <p className="text-xs text-destructive">{strings.validation.nameInvalid}</p>
                ) : null}
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminUserCreate.email}</label>
                <Input
                  type="email"
                  value={email}
                  aria-invalid={emailError}
                  onChange={(event) => setEmail(event.target.value)}
                />
                {emailError ? (
                  <p className="text-xs text-destructive">{strings.validation.emailInvalid}</p>
                ) : null}
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">{strings.adminUserCreate.password}</label>
                <Input
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <PasswordRequirements password={password} />
              </div>
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.adminUserCreate.roles}</h3>
            <div className="grid gap-2">
              {ROLE_OPTIONS.map((role) => (
                <label key={role.value} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={roles.has(role.value)}
                    onChange={() => toggleRole(role.value)}
                  />
                  <span>{role.label}</span>
                </label>
              ))}
            </div>
          </section>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)}>
            {strings.adminUserCreate.cancel}
          </Button>
          <Button onClick={handleCreate} disabled={!canCreate}>
            {isCreating ? strings.adminUserCreate.creating : strings.adminUserCreate.create}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
