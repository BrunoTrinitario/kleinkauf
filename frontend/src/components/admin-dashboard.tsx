import { useEffect, useState } from "react"
import axios from "axios"
import { useAuth } from "@/lib/auth-context"
import type { Page } from "@/lib/types"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Users, Package } from "lucide-react"

export function AdminDashboard() {
  const { user } = useAuth()
  const [totalProducts, setTotalProducts] = useState(0)
  const [totalUsers, setTotalUsers] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  useEffect(() => {
    if (!user?.token) {
      return
    }

    let isMounted = true

    // Solo hacen falta los totales: se pide una pagina de 1 y se lee totalItems
    const loadStats = async () => {
      const options = {
        headers: {
          Authorization: `Bearer ${user.token}`,
        },
        params: { size: 1 },
      }
      try {
        const [productsResponse, usersResponse] = await Promise.all([
          axios.get<Page<unknown>>(`${apiBasePublic}/api/v1/products`, options),
          axios.get<Page<unknown>>(`${apiBasePublic}/api/v1/users`, options),
        ])

        if (isMounted) {
          setTotalProducts(productsResponse.data.totalItems)
          setTotalUsers(usersResponse.data.totalItems)
        }
      } catch (err) {
        if (isMounted) {
          notifyError(err, strings.errors.loadDashboard)
          setTotalProducts(0)
          setTotalUsers(0)
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadStats()

    return () => {
      isMounted = false
    }
  }, [apiBasePublic, user?.token])

  return (
    <div className="space-y-8">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {strings.admin.totalProducts}
            </CardTitle>
            <Package className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-medium">
              {isLoading ? "-" : totalProducts}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {strings.admin.totalUsers}
            </CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-medium">
              {isLoading ? "-" : totalUsers}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
