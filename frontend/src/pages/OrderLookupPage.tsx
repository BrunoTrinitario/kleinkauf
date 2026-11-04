import { strings } from "@/lib/strings"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { OrderLookupForm } from "@/components/order-lookup-form"
import { Card, CardContent } from "@/components/ui/card"

// Consulta de un pedido (/pedido) con numero + email: la usa el invitado, que no tiene "mis pedidos". Publica
export default function OrderLookupPage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <PageHeader
          title={strings.orderLookup.title}
          highlight={strings.orderLookup.highlight}
          subtitle={strings.orderLookup.subtitle}
        />
        <Card className="max-w-md">
          <CardContent>
            <OrderLookupForm />
          </CardContent>
        </Card>
      </main>
      <Footer />
    </div>
  )
}
