import { useState } from "react"
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

interface PlatformSuperadminCreateModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreate: (payload: { name: string; email: string; password: string }) => void
  isCreating?: boolean
  error?: string | null
}

export function PlatformSuperadminCreateModal({
  open,
  onOpenChange,
  onCreate,
  isCreating = false,
  error,
}: PlatformSuperadminCreateModalProps) {
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")

  const nameError = name.length > 0 && !isValidName(name)
  const emailError = email.trim() !== "" && !isValidEmail(email)
  const canCreate = isValidName(name) && isValidEmail(email) && isValidPassword(password) && !isCreating

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
    if (!nextOpen) {
      setName("")
      setEmail("")
      setPassword("")
    }
  }

  const handleCreate = () => {
    if (!canCreate) return
    onCreate({ name: name.trim(), email: email.trim(), password })
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.platformSuperadminCreate.title}</DialogTitle>
          <DialogDescription>{strings.platformSuperadminCreate.description}</DialogDescription>
        </DialogHeader>

        <div className="grid gap-3">
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <div className="grid gap-2">
            <label htmlFor="superadmin-name" className="text-sm font-medium">
              {strings.platformSuperadminCreate.name}
            </label>
            <Input
              id="superadmin-name"
              value={name}
              maxLength={NAME_MAX_LENGTH}
              aria-invalid={nameError}
              onChange={(event) => setName(event.target.value)}
            />
            {nameError ? <p className="text-xs text-destructive">{strings.validation.nameInvalid}</p> : null}
          </div>
          <div className="grid gap-2">
            <label htmlFor="superadmin-email" className="text-sm font-medium">
              {strings.platformSuperadminCreate.email}
            </label>
            <Input
              id="superadmin-email"
              type="email"
              value={email}
              aria-invalid={emailError}
              onChange={(event) => setEmail(event.target.value)}
            />
            {emailError ? <p className="text-xs text-destructive">{strings.validation.emailInvalid}</p> : null}
          </div>
          <div className="grid gap-2">
            <label htmlFor="superadmin-password" className="text-sm font-medium">
              {strings.platformSuperadminCreate.password}
            </label>
            <Input
              id="superadmin-password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <PasswordRequirements password={password} />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)}>
            {strings.platformSuperadminCreate.cancel}
          </Button>
          <Button onClick={handleCreate} disabled={!canCreate}>
            {isCreating ? strings.platformSuperadminCreate.creating : strings.platformSuperadminCreate.create}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
