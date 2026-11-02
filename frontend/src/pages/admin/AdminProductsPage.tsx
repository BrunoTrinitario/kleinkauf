
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import type { Page, Product } from "@/lib/types"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { PaginationControls } from "@/components/pagination-controls"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { Headline } from "@/components/highlight"
import { Header } from "@/components/header"
import { AdminProductCard } from "@/components/admin-product-card"
import { AdminProductFormModal } from "@/components/admin-product-form-modal"
import { hasPermission } from "@/lib/permissions"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
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
  const { user, activeRole, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [productsPage, setProductsPage] = useState<Page<Product> | null>(null)
  const [searchInput, setSearchInput] = useState("")
  const searchQuery = useDebouncedValue(searchInput.trim())
  const [page, setPage] = useState(0)
  // Se incrementa para volver a pedir la pagina actual (despues de crear o borrar)
  const [reloadKey, setReloadKey] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  // Producto a borrar: se pide confirmacion antes (borrar no se puede deshacer desde el panel)
  const [productToDelete, setProductToDelete] = useState<Product | null>(null)
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  const canManageProducts = activeRole === "ADMINISTRADOR" || activeRole === "EMPLEADO"

  useEffect(() => {
    if (isAuthLoading) {
      return
    }
    if (!isAuthenticated) {
      navigate("/login")
      return
    }
    if (!canManageProducts) {
      navigate("/")
    }
  }, [isAuthenticated, isAuthLoading, canManageProducts, navigate])

  // Una busqueda nueva arranca desde la primera pagina
  useEffect(() => {
    setPage(0)
  }, [searchQuery])

  useEffect(() => {
    if (!canManageProducts || !user?.token) {
      return
    }

    let isMounted = true

    const loadProducts = async () => {
      setIsLoading(true)
      try {
        const response = await axios.get<Page<Product>>(`${apiBasePublic}/api/v1/products`, {
          headers: {
            Authorization: `Bearer ${user.token}`,
          },
          params: {
            q: searchQuery || undefined,
            page,
          },
        })
        if (isMounted) {
          setProductsPage(response.data)
        }
      } catch (err) {
        if (isMounted) {
          setProductsPage(null)
          notifyError(err, strings.errors.loadProducts)
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
  }, [apiBasePublic, canManageProducts, user?.token, searchQuery, page, reloadKey])

  if (isAuthLoading || !isAuthenticated || !canManageProducts) {
    return null
  }

  const products = productsPage?.items ?? []

  // Mismo formulario para crear (selectedProduct null) y para editar
  const openCreateForm = () => {
    setSelectedProduct(null)
    setIsFormOpen(true)
  }

  const handleEditProduct = (product: Product) => {
    setSelectedProduct(product)
    setIsFormOpen(true)
  }

  // Al guardar se recarga la pagina actual: un producto nuevo o renombrado puede cambiar de lugar
  const handleSaved = () => {
    setIsFormOpen(false)
    setSelectedProduct(null)
    setReloadKey((prev) => prev + 1)
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
      // Si era el ultimo de la pagina se vuelve a la anterior; si no, se recarga la actual
      if (products.length === 1 && page > 0) {
        setPage(page - 1)
      } else {
        setReloadKey((prev) => prev + 1)
      }
    } catch (err) {
      notifyError(err, strings.errors.deleteProduct)
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
            <h1 className="text-heading-sm text-foreground">
            <Headline text={strings.adminProducts.title} highlight="productos" />
          </h1>
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
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                />
              </div>
              <Button onClick={openCreateForm}>
                <Plus className="h-4 w-4 mr-2" />
                {strings.adminProducts.createButton}
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Products grid */}
        <Card>
          <CardHeader>
            <CardTitle>{strings.adminProducts.productsTitle} ({productsPage?.totalItems ?? 0})</CardTitle>
            <CardDescription>
              {strings.adminProducts.productsDescription}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            {isLoading && !productsPage ? (
              <div className="text-center py-12">
                <p className="text-muted-foreground">{strings.adminProducts.loading}</p>
              </div>
            ) : products.length === 0 ? (
              <div className="text-center py-12">
                <Package className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
                <p className="text-muted-foreground">{strings.adminProducts.empty}</p>
              </div>
            ) : (
              <div className="space-y-4">
                {products.map((product) => (
                  <AdminProductCard
                    key={product.id}
                    product={product}
                    apiBasePublic={apiBasePublic}
                    onEdit={handleEditProduct}
                    onDelete={setProductToDelete}
                  />
                ))}
              </div>
            )}
            <PaginationControls
              page={page}
              totalPages={productsPage?.totalPages ?? 0}
              disabled={isLoading}
              onPageChange={setPage}
            />
          </CardContent>
        </Card>
      </main>

      <AdminProductFormModal
        product={selectedProduct}
        open={isFormOpen}
        onOpenChange={(open) => {
          setIsFormOpen(open)
          if (!open) setSelectedProduct(null)
        }}
        token={user?.token ?? null}
        canEditPrices={hasPermission(user?.roles, "PRECIOS")}
        onSaved={handleSaved}
      />

      <AlertDialog open={productToDelete !== null} onOpenChange={(open) => !open && setProductToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{strings.adminProducts.deleteTitle}</AlertDialogTitle>
            <AlertDialogDescription>
              {productToDelete
                ? `${strings.adminProducts.deleteQuestion} ${productToDelete.name}? ${strings.adminProducts.deleteDescription}`
                : strings.adminProducts.deleteDescription}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{strings.adminProducts.deleteCancel}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (productToDelete) {
                  handleDeleteProduct(productToDelete)
                }
                setProductToDelete(null)
              }}
            >
              {strings.adminProducts.deleteConfirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
