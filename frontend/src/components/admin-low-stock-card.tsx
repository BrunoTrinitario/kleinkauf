import { strings } from "@/lib/strings"
import type { LowStockProduct } from "@/api/products"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { AlertTriangle } from "lucide-react"

interface AdminLowStockCardProps {
  products: LowStockProduct[]
  onRegister: (product: LowStockProduct) => void
}

// Productos con stock igual o menor a su minimo (area STOCK). Sin ninguno no se muestra
export function AdminLowStockCard({ products, onRegister }: AdminLowStockCardProps) {
  if (products.length === 0) return null

  return (
    <Card className="mb-6 border-destructive/30">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 text-destructive" />
          {strings.adminLowStock.title} ({products.length})
        </CardTitle>
        <CardDescription>{strings.adminLowStock.description}</CardDescription>
      </CardHeader>
      <CardContent className="px-3 sm:px-6">
        <ul className="divide-y divide-border">
          {products.map((product) => (
            <li key={product.id} className="flex items-center justify-between gap-3 py-2">
              <div className="min-w-0">
                <p className="font-medium truncate">{product.name}</p>
                <p className="text-sm text-muted-foreground">
                  {strings.adminLowStock.count(product.stock, product.minStock)}
                </p>
              </div>
              <Button variant="outline" size="sm" onClick={() => onRegister(product)}>
                {strings.adminLowStock.register}
              </Button>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
