import type { TenantStatus } from "@/lib/tenant-status"
import { strings } from "@/lib/strings"
import { Headline } from "@/components/highlight"
import { Button } from "@/components/ui/button"
import { Ban, Store } from "lucide-react"

interface StoreUnavailablePageProps {
  status: Exclude<TenantStatus, "ok">
}

// Se muestra en lugar de las rutas cuando el gateway dice que la tienda no existe o esta deshabilitada.
// Volver a intentar recarga: una tienda que se vuelve a habilitar tarda hasta 1 minuto en verse (cache del gateway)
export default function StoreUnavailablePage({ status }: StoreUnavailablePageProps) {
  const notFound = status === "not-found"
  const Icon = notFound ? Store : Ban

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="w-full max-w-md text-center">
        <div className="mx-auto mb-6 flex h-14 w-14 items-center justify-center rounded-full bg-muted">
          <Icon className="h-6 w-6 text-muted-foreground" />
        </div>
        <h1 className="text-heading-sm text-foreground">
          {notFound ? (
            <Headline text={strings.storeUnavailable.notFoundTitle} highlight={strings.storeUnavailable.notFoundHighlight} />
          ) : (
            <Headline text={strings.storeUnavailable.disabledTitle} highlight={strings.storeUnavailable.disabledHighlight} />
          )}
        </h1>
        <p className="text-muted-foreground mt-3">
          {notFound
            ? strings.storeUnavailable.notFoundDescription(window.location.host)
            : strings.storeUnavailable.disabledDescription}
        </p>
        {notFound ? null : (
          <Button className="mt-8" onClick={() => window.location.reload()}>
            {strings.storeUnavailable.retry}
          </Button>
        )}
      </div>
    </div>
  )
}
