import { useMemo, useState } from "react"
import type { NewTenantDto } from "@/lib/types"
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

// Mismo formato que valida decorusers (TenantService.IDENTIFIER). Los reservados los rechaza el backend
const IDENTIFIER_PATTERN = /^[a-z][a-z0-9-]{1,28}[a-z0-9]$/

const isValidIdentifier = (identifier: string) => IDENTIFIER_PATTERN.test(identifier)

interface PlatformTenantCreateModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreate: (payload: NewTenantDto) => void
  isCreating?: boolean
  error?: string | null
}

export function PlatformTenantCreateModal({
  open,
  onOpenChange,
  onCreate,
  isCreating = false,
  error,
}: PlatformTenantCreateModalProps) {
  const [identifier, setIdentifier] = useState("")
  const [ownerName, setOwnerName] = useState("")
  const [ownerEmail, setOwnerEmail] = useState("")
  const [ownerPassword, setOwnerPassword] = useState("")

  const identifierError = identifier !== "" && !isValidIdentifier(identifier)
  const nameError = ownerName.length > 0 && !isValidName(ownerName)
  const emailError = ownerEmail.trim() !== "" && !isValidEmail(ownerEmail)

  // La tienda queda en <identificador>.<host de la plataforma>, con el mismo esquema
  const urlPreview = `${window.location.protocol}//${identifier || "mitienda"}.${window.location.host}`

  const canCreate = useMemo(
    () =>
      isValidIdentifier(identifier) &&
      isValidName(ownerName) &&
      isValidEmail(ownerEmail) &&
      isValidPassword(ownerPassword) &&
      !isCreating,
    [identifier, ownerName, ownerEmail, ownerPassword, isCreating]
  )

  const resetForm = () => {
    setIdentifier("")
    setOwnerName("")
    setOwnerEmail("")
    setOwnerPassword("")
  }

  const handleCreate = () => {
    if (!canCreate) return
    onCreate({
      identifier,
      ownerName: ownerName.trim(),
      ownerEmail: ownerEmail.trim(),
      ownerPassword,
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
          <DialogTitle>{strings.platformTenantCreate.title}</DialogTitle>
          <DialogDescription>{strings.platformTenantCreate.description}</DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {error ? <p className="text-sm text-destructive">{error}</p> : null}

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.platformTenantCreate.storeSection}</h3>
            <div className="grid gap-2">
              <label htmlFor="tenant-identifier" className="text-sm font-medium">
                {strings.platformTenantCreate.identifier}
              </label>
              <Input
                id="tenant-identifier"
                value={identifier}
                maxLength={30}
                autoComplete="off"
                aria-invalid={identifierError}
                // Se escribe siempre en minusculas, como lo guarda el backend
                onChange={(event) => setIdentifier(event.target.value.toLowerCase())}
              />
              <p className={`text-xs ${identifierError ? "text-destructive" : "text-muted-foreground"}`}>
                {identifierError
                  ? strings.platformTenantCreate.identifierInvalid
                  : strings.platformTenantCreate.identifierHint}
              </p>
              <p className="text-xs text-muted-foreground break-all">
                {strings.platformTenantCreate.urlPreview} {urlPreview}
              </p>
            </div>
          </section>

          <section className="space-y-3">
            <h3 className="text-sm font-medium">{strings.platformTenantCreate.ownerSection}</h3>
            <div className="grid gap-3">
              <div className="grid gap-2">
                <label htmlFor="tenant-owner-name" className="text-sm font-medium">
                  {strings.platformTenantCreate.ownerName}
                </label>
                <Input
                  id="tenant-owner-name"
                  value={ownerName}
                  maxLength={NAME_MAX_LENGTH}
                  aria-invalid={nameError}
                  onChange={(event) => setOwnerName(event.target.value)}
                />
                {nameError ? <p className="text-xs text-destructive">{strings.validation.nameInvalid}</p> : null}
              </div>
              <div className="grid gap-2">
                <label htmlFor="tenant-owner-email" className="text-sm font-medium">
                  {strings.platformTenantCreate.ownerEmail}
                </label>
                <Input
                  id="tenant-owner-email"
                  type="email"
                  value={ownerEmail}
                  aria-invalid={emailError}
                  onChange={(event) => setOwnerEmail(event.target.value)}
                />
                {emailError ? <p className="text-xs text-destructive">{strings.validation.emailInvalid}</p> : null}
              </div>
              <div className="grid gap-2">
                <label htmlFor="tenant-owner-password" className="text-sm font-medium">
                  {strings.platformTenantCreate.ownerPassword}
                </label>
                <Input
                  id="tenant-owner-password"
                  type="password"
                  autoComplete="new-password"
                  value={ownerPassword}
                  onChange={(event) => setOwnerPassword(event.target.value)}
                />
                <PasswordRequirements password={ownerPassword} />
              </div>
            </div>
          </section>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)}>
            {strings.platformTenantCreate.cancel}
          </Button>
          <Button onClick={handleCreate} disabled={!canCreate}>
            {isCreating ? strings.platformTenantCreate.creating : strings.platformTenantCreate.create}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
