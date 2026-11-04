import type { TenantStatus } from "@/lib/tenant-status"
import { strings } from "@/lib/strings"
import { Headline } from "@/components/highlight"
import { Button } from "@/components/ui/button"
import { Ban, Store } from "lucide-react"

interface StoreUnavailablePageProps {
  status: Exclude<TenantStatus, "ok">
}

// Shown instead of the routes when the gateway says the store does not exist or is disabled.
// Retry reloads: a store that is enabled again takes up to 1 minute to show (gateway cache)
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
