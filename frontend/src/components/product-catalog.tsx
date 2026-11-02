
import axios from "axios"
import { useEffect, useState } from "react"
import type { CatalogCategory, CatalogPage, CatalogProduct, CatalogSort } from "@/lib/types"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { ProductCard } from "./product-card"
import { ProductModal } from "./product-modal"
import { PaginationControls } from "./pagination-controls"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "./list-toolbar"

export function ProductCatalog() {
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"
  const [catalog, setCatalog] = useState<CatalogPage | null>(null)
  const [categories, setCategories] = useState<CatalogCategory[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [selectedProduct, setSelectedProduct] = useState<CatalogProduct | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [searchInput, setSearchInput] = useState("")
  const searchQuery = useDebouncedValue(searchInput.trim())
  const [selectedCategory, setSelectedCategory] = useState("")
  const [sortBy, setSortBy] = useState<CatalogSort>("name")
  const [page, setPage] = useState(0)

  // Una busqueda nueva arranca desde la primera pagina
  useEffect(() => {
    setPage(0)
  }, [searchQuery])

  useEffect(() => {
    let isMounted = true

    axios
      .get<CatalogCategory[]>(`${apiBasePublic}/api/v1/catalog/categories`)
      .then((response) => {
        if (isMounted) {
          setCategories(response.data)
        }
      })
      .catch(() => {
        // Sin categorias el catalogo sigue funcionando, solo sin el filtro
      })

    return () => {
      isMounted = false
    }
  }, [apiBasePublic])

  useEffect(() => {
    let isMounted = true

    const loadCatalog = async () => {
      setIsLoading(true)
      try {
        const response = await axios.get<CatalogPage>(`${apiBasePublic}/api/v1/catalog/products`, {
          params: {
            q: searchQuery || undefined,
            category: selectedCategory || undefined,
            sort: sortBy,
            page,
          },
        })
        if (isMounted) {
          setCatalog(response.data)
        }
      } catch (err) {
        if (isMounted) {
          setCatalog(null)
          notifyError(err, strings.errors.loadProducts)
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadCatalog()

    return () => {
      isMounted = false
    }
  }, [apiBasePublic, searchQuery, selectedCategory, sortBy, page])

  const products = catalog?.items ?? []
  const totalItems = catalog?.totalItems ?? 0
  const totalPages = catalog?.totalPages ?? 0

  const handleProductClick = (product: CatalogProduct) => {
    setSelectedProduct(product)
    setModalOpen(true)
  }

  const clearFilters = () => {
    setSearchInput("")
    setSelectedCategory("")
    setPage(0)
  }

  return (
    <div className="space-y-6">
      <ListToolbar>
        <SearchField
          value={searchInput}
          onChange={setSearchInput}
          placeholder={strings.productCatalog.searchPlaceholder}
        />
        <FilterGroup>
          <FilterSelect
            value={selectedCategory}
            label={strings.productCatalog.all}
            onChange={(value) => {
              setSelectedCategory(value)
              setPage(0)
            }}
          >
            <option value="">{strings.productCatalog.all}</option>
            {categories.map(category => (
              <option key={category.name} value={category.name}>
                {category.name} ({category.products})
              </option>
            ))}
          </FilterSelect>
          <FilterSelect
            value={sortBy}
            label={strings.productCatalog.sortByName}
            onChange={(value) => {
              setSortBy(value as CatalogSort)
              setPage(0)
            }}
          >
            <option value="name">{strings.productCatalog.sortByName}</option>
            <option value="price_asc">{strings.productCatalog.sortByPriceAsc}</option>
            <option value="price_desc">{strings.productCatalog.sortByPriceDesc}</option>
          </FilterSelect>
        </FilterGroup>
      </ListToolbar>

      {/* Results Count */}
      <p className="text-sm text-muted-foreground">
        {totalItems}{" "}
        {totalItems === 1 ? strings.productCatalog.resultsOne : strings.productCatalog.resultsMany}
      </p>

      {/* Product Grid */}
      {isLoading && !catalog ? (
        <p className="text-sm text-muted-foreground">{strings.home.loadingProducts}</p>
      ) : products.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {products.map((product) => (
            <ProductCard
              key={product.slug}
              product={product}
              onClick={() => handleProductClick(product)}
            />
          ))}
        </div>
      ) : (
        <div className="text-center py-12">
          <p className="text-muted-foreground">{strings.productCatalog.empty}</p>
          <button
            onClick={clearFilters}
            className="text-foreground font-medium hover:underline mt-2"
          >
            {strings.productCatalog.clearFilters}
          </button>
        </div>
      )}

      <PaginationControls
        page={page}
        totalPages={totalPages}
        disabled={isLoading}
        onPageChange={setPage}
      />

      <ProductModal
        product={selectedProduct}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </div>
  )
}
