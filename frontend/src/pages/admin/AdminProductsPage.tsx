
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { loginUrl, useAuth } from "@/lib/auth-context"
import { useQueryClient } from "@tanstack/react-query"
import type { Product } from "@/lib/types"
import { useCatalogCategories } from "@/api/catalog"
import { invalidateProducts, useAdminProducts, useDeleteProduct } from "@/api/products"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { PaginationControls } from "@/components/pagination-controls"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { PageHeader } from "@/components/page-header"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "@/components/list-toolbar"
import { Header } from "@/components/header"
import { AdminProductCard } from "@/components/admin-product-card"
import { AdminProductFormModal } from "@/components/admin-product-form-modal"
import { hasPermission } from "@/lib/permissions"
import { Button } from "@/components/ui/button"
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
import { Plus, Package } from "lucide-react"

export default function ProductsManagementPage() {
  const navigate = useNavigate()
  const { user, activeRole, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const queryClient = useQueryClient()
  const [searchInput, setSearchInput] = useState("")
  const searchQuery = useDebouncedValue(searchInput.trim())
  const [categoryFilter, setCategoryFilter] = useState("")
  const [page, setPage] = useState(0)
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  // Producto a borrar: se pide confirmacion antes (borrar no se puede deshacer desde el panel)
  const [productToDelete, setProductToDelete] = useState<Product | null>(null)

  const canManageProducts = activeRole === "ADMINISTRADOR" || activeRole === "EMPLEADO"

  useEffect(() => {
    if (isAuthLoading) {
      return
    }
    if (!isAuthenticated) {
      navigate(loginUrl())
      return
    }
    if (!canManageProducts) {
      navigate("/")
    }
  }, [isAuthenticated, isAuthLoading, canManageProducts, navigate])

  // Una busqueda o filtro nuevo arranca desde la primera pagina
  useEffect(() => {
    setPage(0)
  }, [searchQuery, categoryFilter])

  // Categorias para el filtro (las mismas que el catalogo, con su cantidad de productos)
  const categories = useCatalogCategories().data ?? []
  const productsQuery = useAdminProducts(
    { q: searchQuery, category: categoryFilter, page },
    canManageProducts && !!user?.token
  )
  const productsPage = productsQuery.data
  const isLoading = productsQuery.isFetching
  const deleteProduct = useDeleteProduct()

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
    invalidateProducts(queryClient)
  }

  const handleDeleteProduct = async (product: Product) => {
    try {
      await deleteProduct.mutateAsync(product.id)
      // Si era el ultimo de la pagina se vuelve a la anterior (la actual se recarga sola)
      if (products.length === 1 && page > 0) {
        setPage(page - 1)
      }
    } catch (err) {
      notifyError(err, strings.errors.deleteProduct)
    }
  }


  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.adminProducts.title}
          highlight="productos"
          subtitle={strings.adminProducts.subtitle}
          backTo="/admin"
          action={
            <Button onClick={openCreateForm}>
              <Plus className="h-4 w-4 mr-2" />
              {strings.adminProducts.createButton}
            </Button>
          }
        />

        <div className="mb-6">
          <ListToolbar>
            <SearchField
              value={searchInput}
              onChange={setSearchInput}
              placeholder={strings.adminProducts.searchPlaceholder}
            />
            <FilterGroup>
              <FilterSelect value={categoryFilter} onChange={setCategoryFilter} label={strings.productCatalog.all}>
                <option value="">{strings.productCatalog.all}</option>
                {categories.map((category) => (
                  <option key={category.name} value={category.name}>
                    {category.name} ({category.products})
                  </option>
                ))}
              </FilterSelect>
            </FilterGroup>
          </ListToolbar>
        </div>

        {/* Products grid */}
        <Card>
          <CardHeader>
            <CardTitle>{strings.adminProducts.productsTitle} ({productsPage?.totalItems ?? 0})</CardTitle>
            <CardDescription>
              {strings.adminProducts.productsDescription}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6 px-3 sm:px-6">
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
