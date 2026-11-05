
import { useEffect, useState } from "react"
import { useAuth } from "@/lib/auth-context"
import { useQueryClient } from "@tanstack/react-query"
import type { Product } from "@/lib/types"
import { useCatalogCategories } from "@/api/catalog"
import {
  invalidateProducts,
  useAdminProducts,
  useDeleteProduct,
  useFeaturedProducts,
  useLowStock,
  useSetFeatured,
} from "@/api/products"
import { AdminStockDialog, type StockTarget } from "@/components/admin-stock-dialog"
import { AdminLowStockCard } from "@/components/admin-low-stock-card"
import { AdminFeaturedDialog } from "@/components/admin-featured-dialog"
import { AdminCsvDialog } from "@/components/admin-csv-dialog"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { PaginationControls } from "@/components/pagination-controls"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { PageHeader } from "@/components/page-header"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "@/components/list-toolbar"
import { Header } from "@/components/header"
import { AdminProductCard } from "@/components/admin-product-card"
import { AdminProductFormModal } from "@/components/admin-product-form-modal"
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
import { FileSpreadsheet, Plus, Package, Star } from "lucide-react"

// Opens with any dashboard area (RequireArea in App.tsx); creating, editing and deleting require PRODUCTOS
export default function ProductsManagementPage() {
  const { user, can } = useAuth()
  const queryClient = useQueryClient()
  const [searchInput, setSearchInput] = useState("")
  const searchQuery = useDebouncedValue(searchInput.trim())
  const [categoryFilter, setCategoryFilter] = useState("")
  const [page, setPage] = useState(0)
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  // Product to delete: confirmation is asked first (deleting cannot be undone from the dashboard)
  const [productToDelete, setProductToDelete] = useState<Product | null>(null)
  const [stockTarget, setStockTarget] = useState<StockTarget | null>(null)
  const [isFeaturedOpen, setIsFeaturedOpen] = useState(false)
  const [isCsvOpen, setIsCsvOpen] = useState(false)

  const canEditProducts = can("PRODUCTOS")
  const canManageStock = can("STOCK")
  // The CSV carries costs: it requires PRODUCTOS and PRECIOS
  const canUseCsv = canEditProducts && can("PRECIOS")
  const lowStock = useLowStock(canManageStock).data ?? []
  const featured = useFeaturedProducts(canEditProducts).data
  const setFeatured = useSetFeatured()

  // A new search or filter starts from the first page
  useEffect(() => {
    setPage(0)
  }, [searchQuery, categoryFilter])

  // Categories for the filter (the same as the catalog, with their product count)
  const categories = useCatalogCategories().data ?? []
  const productsQuery = useAdminProducts({ q: searchQuery, category: categoryFilter, page }, !!user?.token)
  const productsPage = productsQuery.data
  const isLoading = productsQuery.isFetching
  const deleteProduct = useDeleteProduct()

  const products = productsPage?.items ?? []

  // Same form to create (selectedProduct null) and to edit
  const openCreateForm = () => {
    setSelectedProduct(null)
    setIsFormOpen(true)
  }

  const handleEditProduct = (product: Product) => {
    setSelectedProduct(product)
    setIsFormOpen(true)
  }

  // On save the current page is reloaded: a new or renamed product may change place
  const handleSaved = () => {
    setIsFormOpen(false)
    setSelectedProduct(null)
    invalidateProducts(queryClient)
  }

  const handleDeleteProduct = async (product: Product) => {
    try {
      await deleteProduct.mutateAsync(product.id)
      // If it was the last one on the page it goes back to the previous one (the current one reloads on its own)
      if (products.length === 1 && page > 0) {
        setPage(page - 1)
      }
    } catch (err) {
      notifyError(err, strings.errors.deleteProduct)
    }
  }

  // A new featured product goes last on the home page (its order is the count that already exists)
  const handleToggleFeatured = async (product: Product) => {
    try {
      await setFeatured.mutateAsync(
        product.featured ? { id: product.id, featured: false } : { id: product.id, featured: true, order: featured?.length }
      )
    } catch (err) {
      notifyError(err, strings.adminFeatured.toggleError)
    }
  }

  const openStock = (target: { id: string | number; name: string; stock: number }) =>
    setStockTarget({ id: String(target.id), name: target.name, stock: target.stock })

  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.adminProducts.title}
          highlight="productos"
          subtitle={strings.adminProducts.subtitle}
          action={
            canEditProducts ? (
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" onClick={() => setIsFeaturedOpen(true)}>
                  <Star className="h-4 w-4 mr-2" />
                  {strings.adminFeatured.button}
                </Button>
                {canUseCsv ? (
                  <Button variant="outline" onClick={() => setIsCsvOpen(true)}>
                    <FileSpreadsheet className="h-4 w-4 mr-2" />
                    {strings.adminCsv.button}
                  </Button>
                ) : null}
                <Button onClick={openCreateForm}>
                  <Plus className="h-4 w-4 mr-2" />
                  {strings.adminProducts.createButton}
                </Button>
              </div>
            ) : undefined
          }
        />

        {canManageStock ? <AdminLowStockCard products={lowStock} onRegister={openStock} /> : null}

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
                    onEdit={canEditProducts ? handleEditProduct : undefined}
                    onDelete={canEditProducts ? setProductToDelete : undefined}
                    onStock={canManageStock ? openStock : undefined}
                    onToggleFeatured={canEditProducts ? handleToggleFeatured : undefined}
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
        canEditPrices={can("PRECIOS")}
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

      <AdminStockDialog
        product={stockTarget}
        open={stockTarget !== null}
        onOpenChange={(open) => !open && setStockTarget(null)}
      />
      <AdminFeaturedDialog open={isFeaturedOpen} onOpenChange={setIsFeaturedOpen} />
      <AdminCsvDialog open={isCsvOpen} onOpenChange={setIsCsvOpen} />
    </div>
  )
}
