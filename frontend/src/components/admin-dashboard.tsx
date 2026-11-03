import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { useAuth } from "@/lib/auth-context"
import type { Page } from "@/lib/types"
import { strings } from "@/lib/strings"
import { productKeys } from "@/api/products"
import { userKeys } from "@/api/users"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Users, Package } from "lucide-react"

// Solo hacen falta los totales: se pide una pagina de 1 y se lee totalItems.
// Las keys cuelgan de productos y usuarios, asi un alta o una baja los actualiza
const totalOf = (path: string) => () =>
  api.get<Page<unknown>>(path, { params: { size: 1 } }).then((response) => response.data.totalItems)

export function AdminDashboard() {
  const { user } = useAuth()
  const enabled = !!user?.token
  const products = useQuery({
    queryKey: [...productKeys.all, "total"],
    queryFn: totalOf("/products"),
    enabled,
    meta: { errorMessage: strings.errors.loadDashboard },
  })
  const users = useQuery({
    queryKey: [...userKeys.all, "total"],
    queryFn: totalOf("/users"),
    enabled,
    meta: { errorMessage: strings.errors.loadDashboard },
  })
  const isLoading = products.isPending || users.isPending
  const totalProducts = products.data ?? 0
  const totalUsers = users.data ?? 0

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
