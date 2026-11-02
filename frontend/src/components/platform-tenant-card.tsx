import type { PlatformTenant } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"
import { CalendarDays, ExternalLink, Store, Trash2 } from "lucide-react"

const dateFormat = new Intl.DateTimeFormat("es-AR", { day: "2-digit", month: "2-digit", year: "numeric" })

interface PlatformTenantCardProps {
  tenant: PlatformTenant
  onToggleEnabled: (tenant: PlatformTenant, enabled: boolean) => void
  onDelete: (tenant: PlatformTenant) => void
  // Mientras se guarda el cambio de estado de esta tienda
  isSaving?: boolean
}

export function PlatformTenantCard({ tenant, onToggleEnabled, onDelete, isSaving }: PlatformTenantCardProps) {
  const badge = tenant.enabled ? (
    <Badge className="bg-sky-wash text-cyan-edge">{strings.platformTenants.enabled}</Badge>
  ) : (
    <Badge className="bg-muted text-muted-foreground">{strings.platformTenants.disabled}</Badge>
  )

  // Misma disposicion que la tarjeta de usuario: acciones siempre a la derecha, el estado abajo en mobile
  return (
    <div className="flex items-center justify-between gap-3 p-4 rounded-lg border border-border">
      <div className="flex items-center gap-3 sm:gap-4 min-w-0 flex-1">
        <div className="h-10 w-10 shrink-0 rounded-full bg-muted flex items-center justify-center">
          <Store className="h-5 w-5 text-muted-foreground" />
        </div>
        <div className="min-w-0">
          <a
            href={tenant.url}
            target="_blank"
            rel="noreferrer"
            className="font-medium text-foreground hover:underline inline-flex items-center gap-1 max-w-full"
            aria-label={`${strings.platformTenants.openStore}: ${tenant.identifier}`}
          >
            <span className="truncate">{tenant.identifier}</span>
            <ExternalLink className="h-3 w-3 shrink-0 text-muted-foreground" />
          </a>
          <p className="text-sm text-muted-foreground truncate">{tenant.url}</p>
          <p className="text-xs text-muted-foreground flex items-center gap-1 mt-1">
            <CalendarDays className="h-3 w-3 shrink-0" />
            {strings.platformTenants.createdAt} {dateFormat.format(new Date(tenant.createdAt))}
            <span className="hidden sm:inline">
              {" · "}
              {strings.platformTenants.schema} {tenant.schema}
            </span>
          </p>
          <div className="flex flex-wrap gap-1.5 mt-2 sm:hidden">{badge}</div>
        </div>
      </div>

      <div className="flex items-center gap-2 sm:gap-3 shrink-0">
        <div className="hidden sm:flex">{badge}</div>
        <Switch
          checked={tenant.enabled}
          disabled={isSaving}
          onCheckedChange={(checked) => onToggleEnabled(tenant, checked)}
          aria-label={strings.platformTenants.toggleAria(tenant.identifier)}
        />
        <Button
          variant="ghost"
          size="icon"
          className="text-destructive hover:text-destructive"
          onClick={() => onDelete(tenant)}
          aria-label={strings.platformTenants.deleteAria}
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
