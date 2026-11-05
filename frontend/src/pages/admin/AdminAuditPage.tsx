import { Link } from "react-router-dom"
import { Radio } from "lucide-react"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { AuditLog } from "@/components/audit-log"
import { Button } from "@/components/ui/button"

// Store audit (ADMINISTRADOR only, checked by the route). The superadmin does not see it
export default function AdminAuditPage() {
  const { user } = useAuth()
  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader
          title={strings.audit.storeTitle}
          highlight={strings.audit.highlight}
          subtitle={strings.audit.storeSubtitle}
          backTo="/admin"
          action={
            <Button variant="outline" asChild>
              <Link to="/admin/actividad">
                <Radio className="mr-2 h-4 w-4" />
                {strings.auth.activity}
              </Link>
            </Button>
          }
        />
        <AuditLog enabled={!!user?.token} />
      </main>
    </div>
  )
}
