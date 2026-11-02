"use client"

import { useMemo, useState } from "react"
import type { Product } from "@/lib/types"
import { strings } from "@/lib/strings"
import { ProductCard } from "./product-card"
import { ProductModal } from "./product-modal"
import { Input } from "@/components/ui/input"
import { Search, SlidersHorizontal } from "lucide-react"

interface ProductCatalogProps {
  products: Product[]
}

export function ProductCatalog({ products }: ProductCatalogProps) {
  const defaultCategory = strings.productCatalog.all
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedCategory, setSelectedCategory] = useState<string>(defaultCategory)
  const [sortBy, setSortBy] = useState<"name" | "price-asc" | "price-desc" | "rating">("name")

  const categories = useMemo(
    () => [defaultCategory, ...Array.from(new Set(products.map(product => product.category)))],
    [products]
  )

  const filteredProducts = useMemo(() => {
    let visibleProducts = [...products]

    if (searchQuery) {
      const query = (searchQuery ?? "").toLowerCase()
      visibleProducts = visibleProducts.filter(p => {
        const name = p.name?.toLowerCase() ?? ""
        const description = p.description?.toLowerCase() ?? ""
        return name.includes(query) || description.includes(query)
      })
    }

    // Filter by category
    if (selectedCategory !== defaultCategory) {
      visibleProducts = visibleProducts.filter(p => p.category === selectedCategory)
    }

    // Sort
    switch (sortBy) {
      case "name":
        visibleProducts.sort((a, b) => a.name.localeCompare(b.name))
        break
    }

    return visibleProducts
  }, [products, searchQuery, selectedCategory, sortBy])

  const handleProductClick = (product: Product) => {
    setSelectedProduct(product)
    setModalOpen(true)
  }

  return (
    <div className="space-y-6">
      {/* Search and Filters */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            type="text"
            placeholder={strings.productCatalog.searchPlaceholder}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10"
          />
        </div>

        <div className="flex gap-2">
          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            className="h-9 px-3 rounded-md border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring"
          >
            {categories.map(cat => (
              <option key={cat} value={cat}>{cat}</option>
            ))}
          </select>

          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
            className="h-9 px-3 rounded-md border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="name">{strings.productCatalog.sortByName}</option>
          </select>
        </div>
      </div>

      {/* Results Count */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {filteredProducts.length} {strings.productCatalog.productLabel}
          {filteredProducts.length !== 1 ? strings.productCatalog.productPluralSuffix : ""} {strings.productCatalog.resultsFound}
        </p>
        <button className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors sm:hidden">
          <SlidersHorizontal className="h-4 w-4" />
          {strings.productCatalog.filters}
        </button>
      </div>

      {/* Product Grid */}
      {filteredProducts.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {filteredProducts.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              onClick={() => handleProductClick(product)}
            />
          ))}
        </div>
      ) : (
        <div className="text-center py-12">
          <p className="text-muted-foreground">{strings.productCatalog.empty}</p>
          <button
            onClick={() => {
              setSearchQuery("")
              setSelectedCategory(defaultCategory)
            }}
            className="text-foreground font-medium hover:underline mt-2"
          >
            {strings.productCatalog.clearFilters}
          </button>
        </div>
      )}

      <ProductModal
        product={selectedProduct}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </div>
  )
}
