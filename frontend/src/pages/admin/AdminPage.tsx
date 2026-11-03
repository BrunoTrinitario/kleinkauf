
import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { loginUrl, useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { Headline } from "@/components/highlight"
import { Header } from "@/components/header"
import { AdminDashboard } from "@/components/admin-dashboard"

export default function AdminPage() {
  const navigate = useNavigate()
  const { activeRole, isAuthenticated, isLoading: isAuthLoading } = useAuth()

  useEffect(() => {
    if (isAuthLoading) {
      return
    }
    if (!isAuthenticated) {
      navigate(loginUrl())
      return
    }
    if (activeRole !== "ADMINISTRADOR") {
      navigate("/")
    }
  }, [isAuthenticated, isAuthLoading, activeRole, navigate])

  if (isAuthLoading || !isAuthenticated || activeRole !== "ADMINISTRADOR") {
    return null
  }

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-heading-sm text-foreground">
            <Headline text={strings.admin.title} highlight="administración" />
          </h1>
          <p className="text-muted-foreground mt-2">
            {strings.admin.subtitle}
          </p>
        </div>
        <AdminDashboard />
      </main>
    </div>
  )
}
