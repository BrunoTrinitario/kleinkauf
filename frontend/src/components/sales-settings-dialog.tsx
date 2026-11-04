import { useEffect, useState } from "react"
import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import type { SalesSettings } from "@/lib/types"
import { useSalesSettings, useUpdateSalesSettings } from "@/api/sales"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

interface SalesSettingsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

type Form = Record<"alias" | "cbu" | "holder" | "bank" | "expirationHours", string>

const toForm = (settings: SalesSettings | undefined): Form => ({
  alias: settings?.alias ?? "",
  cbu: settings?.cbu ?? "",
  holder: settings?.holder ?? "",
  bank: settings?.bank ?? "",
  expirationHours: settings?.expirationHours != null ? String(settings.expirationHours) : "",
})

// Mismas reglas que SalesSettingsService (docs/ventas.md, "Configuracion de ventas")
function validate(form: Form): string | null {
  if (form.alias && !/^[A-Za-z0-9.-]{6,20}$/.test(form.alias)) return strings.salesSettings.invalidAlias
  if (form.cbu && !/^\d{22}$/.test(form.cbu)) return strings.salesSettings.invalidCbu
  if (form.holder.length > 100) return strings.salesSettings.invalidHolder
  if (form.bank.length > 100) return strings.salesSettings.invalidBank
  if (form.expirationHours) {
    const hours = Number(form.expirationHours)
    if (!Number.isInteger(hours) || hours < 1 || hours > 720) return strings.salesSettings.invalidExpiration
  }
  return null
}

// Datos para transferir y plazo para pagar (solo ADMINISTRADOR). Sin titular y alias o CBU el checkout no vende
export function SalesSettingsDialog({ open, onOpenChange }: SalesSettingsDialogProps) {
  const settings = useSalesSettings(open)
  const update = useUpdateSalesSettings()
  const [form, setForm] = useState<Form>(toForm(undefined))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open && settings.data) setForm(toForm(settings.data))
    if (open) setError(null)
  }, [open, settings.data])

  const set = (field: keyof Form) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    const clean: Form = {
      alias: form.alias.trim(),
      cbu: form.cbu.replace(/\s/g, ""),
      holder: form.holder.trim(),
      bank: form.bank.trim(),
      expirationHours: form.expirationHours.trim(),
    }
    const invalid = validate(clean)
    if (invalid) {
      setError(invalid)
      return
    }
    setError(null)
    try {
      await update.mutateAsync({
        alias: clean.alias || null,
        cbu: clean.cbu || null,
        holder: clean.holder || null,
        bank: clean.bank || null,
        expirationHours: clean.expirationHours ? Number(clean.expirationHours) : null,
      })
      toast.success(strings.salesSettings.saved)
      onOpenChange(false)
    } catch (err) {
      setError(getErrorMessage(err, strings.salesSettings.saveError))
    }
  }

  const incomplete = !form.holder.trim() || (!form.alias.trim() && !form.cbu.trim())

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.salesSettings.title}</DialogTitle>
          <DialogDescription>{strings.salesSettings.description}</DialogDescription>
        </DialogHeader>

        {settings.isError ? (
          <p className="text-sm text-destructive">{strings.salesSettings.loadError}</p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <Field id="settings-holder" label={strings.salesSettings.holder}>
              <Input id="settings-holder" value={form.holder} maxLength={100} onChange={set("holder")} />
            </Field>
            <Field id="settings-alias" label={strings.salesSettings.alias} hint={strings.salesSettings.aliasHint}>
              <Input id="settings-alias" value={form.alias} maxLength={20} onChange={set("alias")} />
            </Field>
            <Field id="settings-cbu" label={strings.salesSettings.cbu} hint={strings.salesSettings.cbuHint}>
              <Input id="settings-cbu" value={form.cbu} inputMode="numeric" maxLength={26} onChange={set("cbu")} />
            </Field>
            <Field id="settings-bank" label={strings.salesSettings.bank}>
              <Input id="settings-bank" value={form.bank} maxLength={100} onChange={set("bank")} />
            </Field>
            <Field
              id="settings-expiration"
              label={strings.salesSettings.expirationHours}
              hint={strings.salesSettings.expirationHint}
            >
              <Input
                id="settings-expiration"
                type="number"
                inputMode="numeric"
                min={1}
                max={720}
                step={1}
                placeholder="48"
                value={form.expirationHours}
                onChange={set("expirationHours")}
              />
            </Field>

            {incomplete && !settings.isPending ? (
              <p className="text-sm text-muted-foreground">{strings.salesSettings.incomplete}</p>
            ) : null}
            {error ? (
              <p className="text-sm text-destructive" role="alert">
                {error}
              </p>
            ) : null}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                {strings.salesSettings.cancel}
              </Button>
              <Button type="submit" disabled={update.isPending || settings.isPending}>
                {update.isPending ? strings.salesSettings.saving : strings.salesSettings.save}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-2">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  )
}
