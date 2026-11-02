
import { useEffect, useState } from "react"
import axios from "axios"
import { useAuth } from "@/lib/auth-context"
import type { Product, User } from "@/lib/types"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Users, Package } from "lucide-react"

export function AdminDashboard() {
  const { user } = useAuth()
  const [products, setProducts] = useState<Product[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  useEffect(() => {
    if (!user?.token) {
      return
    }

    let isMounted = true

    const loadStats = async () => {
      try {
        const [productsResponse, usersResponse] = await Promise.all([
          axios.get(`${apiBasePublic}/api/v1/products/all`, {
            headers: {
              Authorization: `Bearer ${user.token}`,
            },
          }),
          axios.get(`${apiBasePublic}/api/v1/users/all`, {
            headers: {
              Authorization: `Bearer ${user.token}`,
            },
          }),
        ])

        if (isMounted) {
          setProducts(productsResponse.data as Product[])
          setUsers(usersResponse.data as User[])
        }
      } catch (err) {
        if (isMounted) {
          notifyError(err, strings.errors.loadDashboard)
          setProducts([])
          setUsers([])
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
              {isLoading ? "-" : products.length}
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
              {isLoading ? "-" : users.length}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
