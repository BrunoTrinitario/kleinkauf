import { useState } from "react"
import { Link } from "react-router-dom"
import { strings } from "@/lib/strings"
import { CATALOG_PATH } from "@/lib/catalog-url"
import { useAuth } from "@/lib/auth-context"
import { formatAmount, formatServerDateTime } from "@/lib/utils"
import { useMyOrders } from "@/api/sales"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { OrderStatusBadge } from "@/components/order-status-badge"
import { PaginationControls } from "@/components/pagination-controls"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"

// "Mis pedidos" (/mis-pedidos): los pedidos hechos con la sesion iniciada. El detalle es la pantalla del pedido,
// con el email del pedido (la consulta va por numero + email)
export default function MyOrdersPage() {
  const { isAuthenticated } = useAuth()
  const [page, setPage] = useState(0)
  const orders = useMyOrders(page, isAuthenticated)
  const data = orders.data

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8 space-y-6">
        <PageHeader title={strings.myOrders.title} highlight={strings.myOrders.highlight} subtitle={strings.myOrders.subtitle} />

        {orders.isPending ? (
          <p className="text-muted-foreground">{strings.myOrders.loading}</p>
        ) : orders.isError || !data ? (
          <p className="text-destructive">{strings.myOrders.loadError}</p>
        ) : data.items.length === 0 ? (
          <Card className="max-w-2xl">
            <CardContent className="space-y-4">
              <p className="text-muted-foreground">{strings.myOrders.empty}</p>
              <Button asChild>
                <Link to={CATALOG_PATH}>{strings.cart.backToCatalog}</Link>
              </Button>
            </CardContent>
          </Card>
        ) : (
          <>
            <Card className="p-0 gap-0 overflow-hidden max-w-4xl">
              <ul className="divide-y divide-border">
                {data.items.map((order) => (
                  <li key={order.number} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1 min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono font-medium">{order.number}</span>
                        <OrderStatusBadge status={order.status} />
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {formatServerDateTime(order.createdAt)} · {strings.myOrders.units(order.units)}
                      </p>
                    </div>
                    <div className="flex items-center justify-between gap-4 sm:justify-end">
                      <span className="font-medium tabular-nums">{formatAmount(order.total)}</span>
                      <Button variant="outline" size="sm" asChild>
                        <Link to={`/pedido/${order.number}`} state={{ email: order.buyerEmail }}>
                          {strings.myOrders.view}
                        </Link>
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
            <PaginationControls
              page={page}
              totalPages={data.totalPages}
              disabled={orders.isFetching}
              onPageChange={setPage}
            />
          </>
        )}
      </main>
      <Footer />
    </div>
  )
}
