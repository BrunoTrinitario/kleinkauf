
import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
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
      navigate("/login")
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
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            {strings.admin.title}
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
