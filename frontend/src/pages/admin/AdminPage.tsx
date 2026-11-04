import { Link } from "react-router-dom"
import { Store } from "lucide-react"
import { strings } from "@/lib/strings"
import { Headline } from "@/components/highlight"
import { Header } from "@/components/header"
import { AdminDashboard } from "@/components/admin-dashboard"
import { Button } from "@/components/ui/button"

// ADMINISTRADOR only: checked by the route (RequireRole in App.tsx)
export default function AdminPage() {
  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-heading-sm text-foreground">
              <Headline text={strings.admin.title} highlight="administración" />
            </h1>
            <p className="text-muted-foreground mt-2">
              {strings.admin.subtitle}
            </p>
          </div>
          <Button variant="outline" asChild className="self-start">
            <Link to="/admin/store">
              <Store className="h-4 w-4" />
              {strings.adminStore.menu}
            </Link>
          </Button>
        </div>
        <AdminDashboard />
      </main>
    </div>
  )
}
