import { Link } from "react-router-dom"
import { Minus, Plus, Trash2 } from "lucide-react"
import { productImageUrl } from "@/lib/api"
import { strings } from "@/lib/strings"
import { CATALOG_PATH } from "@/lib/catalog-url"
import { formatAmount } from "@/lib/utils"
import { MAX_QUANTITY, useCart, type CartItem } from "@/lib/cart"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"

// Carrito (/carrito): cantidades y total con los precios que vio el comprador. Publico
export default function CartPage() {
  const { items, total, clear } = useCart()

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <PageHeader title={strings.cart.title} highlight={strings.cart.highlight} subtitle={strings.cart.subtitle} />

        {items.length === 0 ? (
          <Card className="max-w-3xl">
            <CardContent className="space-y-4 py-2">
              <p className="text-muted-foreground">{strings.cart.empty}</p>
              <div className="flex flex-wrap gap-3">
                <Button asChild>
                  <Link to={CATALOG_PATH}>{strings.cart.backToCatalog}</Link>
                </Button>
                <Button variant="outline" asChild>
                  <Link to="/pedido">{strings.cart.lookupLink}</Link>
                </Button>
              </div>
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
            <Card className="p-0 gap-0 overflow-hidden">
              <ul className="divide-y divide-border">
                {items.map((item) => (
                  <CartLine key={`${item.type}-${item.id}`} item={item} />
                ))}
              </ul>
            </Card>

            <Card>
              <CardContent className="space-y-4">
                <div className="flex items-baseline justify-between">
                  <span className="text-muted-foreground">{strings.cart.total}</span>
                  <span className="text-lg font-medium">{formatAmount(total)}</span>
                </div>
                <p className="text-xs text-muted-foreground">{strings.cart.priceNote}</p>
                <Button className="w-full" asChild>
                  <Link to="/checkout">{strings.cart.checkout}</Link>
                </Button>
                <Button variant="ghost" className="w-full text-muted-foreground" onClick={clear}>
                  {strings.cart.clear}
                </Button>
              </CardContent>
            </Card>
          </div>
        )}
      </main>
      <Footer />
    </div>
  )
}

function CartLine({ item }: { item: CartItem }) {
  const { setQuantity, remove } = useCart()

  return (
    <li className="flex gap-3 p-4 sm:gap-4">
      <div className="h-16 w-16 shrink-0 overflow-hidden rounded-md bg-muted sm:h-20 sm:w-20">
        {item.image ? (
          <img src={productImageUrl(item.image)} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : null}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          {item.type === "BUNDLE" ? (
            <p className="text-xs uppercase tracking-wider text-muted-foreground">{strings.cart.bundle}</p>
          ) : null}
          {item.slug ? (
            <Link to={`/productos/${item.slug}`} className="font-medium hover:underline underline-offset-4">
              {item.name}
            </Link>
          ) : (
            <p className="font-medium">{item.name}</p>
          )}
          <p className="text-sm text-muted-foreground">{formatAmount(item.price)}</p>
        </div>

        <div className="flex items-center gap-2 sm:gap-4 sm:justify-end">
          <div className="flex shrink-0 items-center gap-0.5 sm:gap-1" role="group" aria-label={strings.cart.quantity}>
            <Button
              variant="outline"
              size="icon"
              className="h-7 w-7 sm:h-8 sm:w-8"
              disabled={item.quantity <= 1}
              onClick={() => setQuantity(item.type, item.id, item.quantity - 1)}
              aria-label={strings.cart.decrease(item.name)}
            >
              <Minus className="h-3.5 w-3.5" />
            </Button>
            <span className="w-6 text-center text-sm tabular-nums sm:w-8" aria-live="polite">
              {item.quantity}
            </span>
            <Button
              variant="outline"
              size="icon"
              className="h-7 w-7 sm:h-8 sm:w-8"
              disabled={item.quantity >= MAX_QUANTITY}
              onClick={() => setQuantity(item.type, item.id, item.quantity + 1)}
              aria-label={strings.cart.increase(item.name)}
            >
              <Plus className="h-3.5 w-3.5" />
            </Button>
          </div>
          <span className="ml-auto whitespace-nowrap text-right font-medium tabular-nums sm:ml-0 sm:w-28">
            {formatAmount(item.price * item.quantity)}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 shrink-0 text-muted-foreground hover:text-destructive"
            onClick={() => remove(item.type, item.id)}
            aria-label={strings.cart.remove(item.name)}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </li>
  )
}
