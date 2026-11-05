import { strings } from "@/lib/strings"
import { usePlatformSession } from "@/hooks/use-platform-session"
import { PlatformHeader } from "@/components/platform-header"
import { PageHeader } from "@/components/page-header"
import { AuditLog } from "@/components/audit-log"

// Platform audit (stores and superadmins). It does not include the stores' audit: each administrator sees their own
export default function PlatformAuditPage() {
  const { ready } = usePlatformSession()
  if (!ready) {
    return null
  }
  return (
    <div className="min-h-screen bg-background">
      <PlatformHeader />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader
          title={strings.audit.platformTitle}
          highlight={strings.audit.highlight}
          subtitle={strings.audit.platformSubtitle}
        />
        <AuditLog enabled={ready} />
      </main>
    </div>
  )
}
