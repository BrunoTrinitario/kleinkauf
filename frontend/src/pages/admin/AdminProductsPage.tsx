
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import type { Product } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Header } from "@/components/header"
import { AdminProductCard } from "@/components/admin-product-card"
import { AdminProductEditModal } from "@/components/admin-product-edit-modal"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import axios from "axios"
import { 
  ArrowLeft, 
  Plus,
  Search,
  Package
} from "lucide-react"
import { Link } from "react-router-dom"

export default function ProductsManagementPage() {
  const navigate = useNavigate()
  const { user, activeRole, isAuthenticated } = useAuth()
  const [products, setProducts] = useState<Product[]>([])
  const [searchQuery, setSearchQuery] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createName, setCreateName] = useState("")
  const [createCategory, setCreateCategory] = useState("")
  const [createDescription, setCreateDescription] = useState("")
  const [createStock, setCreateStock] = useState(0)
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  const canManageProducts = activeRole === "ADMINISTRADOR" || activeRole === "EMPLEADO"

  useEffect(() => {
    if (!isAuthenticated || !canManageProducts) {
      navigate("/")
    }
  }, [isAuthenticated, canManageProducts, navigate])

  if (!isAuthenticated || !canManageProducts) {
    return null
  }

  useEffect(() => {
    let isMounted = true

    const loadProducts = async () => {
      try {
        const response = await axios.get(`${apiBasePublic}/api/v1/products/all`)
        const data = response.data as Product[]
        if (isMounted) {
          setProducts(data)
        }
      } catch {
        if (isMounted) {
          setProducts([])
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadProducts()

    return () => {
      isMounted = false
    }
  }, [apiBasePublic])

  const filteredProducts = products.filter(product => {
    const query = searchQuery.toLowerCase()
    const name = product.name?.toLowerCase() ?? ""
    const category = product.category?.toLowerCase() ?? ""
    const description = product.description?.toLowerCase() ?? ""
    return (
      name.includes(query) ||
      category.includes(query) ||
      description.includes(query)
    )
  })

  const handleEditProduct = (product: Product) => {
    setSelectedProduct(product)
    setIsEditOpen(true)
  }

  const handleEditOpenChange = (open: boolean) => {
    setIsEditOpen(open)
    if (!open) {
      setSelectedProduct(null)
    }
  }

  const resetCreateForm = () => {
    setCreateName("")
    setCreateCategory("")
    setCreateDescription("")
    setCreateStock(0)
    setCreateError(null)
  }

  const handleCreateOpenChange = (open: boolean) => {
    setIsCreateOpen(open)
    if (!open) {
      resetCreateForm()
    }
  }

  const handleDeleteProduct = async (product: Product) => {
    if (!user?.token) {
      return
    }

    try {
      await axios.delete(`${apiBasePublic}/api/v1/products/${product.id}`, {
        headers: {
          Authorization: `Bearer ${user.token}`,
        },
      })
      setProducts((prev) => prev.filter((item) => item.id !== product.id))
    } catch {
      // TODO: surface error to user
    }
  }

  const handleSaveProduct = (updatedProduct: Product) => {
    setProducts((prev) =>
      prev.map((item) => (item.id === updatedProduct.id ? updatedProduct : item))
    )
    setIsEditOpen(false)
    setSelectedProduct(null)
  }

  const handleCreateProduct = async () => {
    if (!user?.token) {
      return
    }

    setIsCreating(true)
    setCreateError(null)

    try {
      const response = await axios.post(
        `${apiBasePublic}/api/v1/products`,
        {
          name: createName,
          category: createCategory,
          description: createDescription,
          stock: createStock,
        },
        {
          headers: {
            Authorization: `Bearer ${user.token}`,
          },
        }
      )

      const created = response.data as Product
      setProducts((prev) => [
        {
          ...created,
          images: created.images ?? [],
        },
        ...prev,
      ])
      setIsCreateOpen(false)
      resetCreateForm()
    } catch {
      setCreateError(strings.adminProducts.createError)
    } finally {
      setIsCreating(false)
    }
  }


  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container mx-auto px-4 py-8">
        <div className="flex items-center gap-4 mb-8">
          <Button variant="ghost" size="icon" asChild>
            <Link to="/admin">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">{strings.adminProducts.title}</h1>
            <p className="text-muted-foreground">{strings.adminProducts.subtitle}</p>
          </div>
        </div>

        {/* Actions bar */}
        <Card className="mb-6">
          <CardContent className="py-4">
            <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
              <div className="relative w-full sm:w-80">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input 
                  placeholder={strings.adminProducts.searchPlaceholder}
                  className="pl-10"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
              <Button onClick={() => setIsCreateOpen(true)}>
                <Plus className="h-4 w-4 mr-2" />
                {strings.adminProducts.createButton}
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Products grid */}
        <Card>
          <CardHeader>
            <CardTitle>{strings.adminProducts.productsTitle} ({filteredProducts.length})</CardTitle>
            <CardDescription>
              {strings.adminProducts.productsDescription}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="text-center py-12">
                <p className="text-muted-foreground">{strings.adminProducts.loading}</p>
              </div>
            ) : filteredProducts.length === 0 ? (
              <div className="text-center py-12">
                <Package className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
                <p className="text-muted-foreground">{strings.adminProducts.empty}</p>
              </div>
            ) : (
              <div className="space-y-4">
                {filteredProducts.map((product) => (
                  <AdminProductCard
                    key={product.id}
                    product={product}
                    apiBasePublic={apiBasePublic}
                    onEdit={handleEditProduct}
                    onDelete={handleDeleteProduct}
                  />
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </main>

      <AdminProductEditModal
        product={selectedProduct}
        open={isEditOpen}
        onOpenChange={handleEditOpenChange}
        token={user?.token ?? null}
        onSave={(product) => handleSaveProduct(product)}
      />

      <Dialog open={isCreateOpen} onOpenChange={handleCreateOpenChange}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{strings.adminProducts.createDialogTitle}</DialogTitle>
            <DialogDescription>
              {strings.adminProducts.createDialogDescription}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4">
            {createError ? (
              <p className="text-sm text-destructive">{createError}</p>
            ) : null}
            <div className="grid gap-2">
              <label className="text-sm font-medium">{strings.adminProducts.nameLabel}</label>
              <Input value={createName} onChange={(e) => setCreateName(e.target.value)} />
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">{strings.adminProducts.categoryLabel}</label>
              <Input value={createCategory} onChange={(e) => setCreateCategory(e.target.value)} />
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">{strings.adminProducts.descriptionLabel}</label>
              <textarea
                className="w-full min-h-[96px] px-3 py-2 text-sm rounded-md border border-input bg-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                value={createDescription}
                onChange={(e) => setCreateDescription(e.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">{strings.adminProducts.stockLabel}</label>
              <Input
                type="number"
                value={createStock}
                onChange={(e) => setCreateStock(Number(e.target.value) || 0)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => handleCreateOpenChange(false)}>
              {strings.adminProducts.cancel}
            </Button>
            <Button onClick={handleCreateProduct} disabled={isCreating || !user?.token}>
              {isCreating ? strings.adminProducts.creating : strings.adminProducts.create}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
