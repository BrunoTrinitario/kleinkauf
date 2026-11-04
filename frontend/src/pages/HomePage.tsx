import { useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router-dom"
import { ArrowRight, Search } from "lucide-react"
import { strings } from "@/lib/strings"
import { catalogUrl, CATALOG_PATH } from "@/lib/catalog-url"
import { useCatalogCategories } from "@/api/catalog"
import { Footer } from "@/components/footer"
import { Header } from "@/components/header"
import { Headline } from "@/components/highlight"
import { BundlesStrip, FeaturedStrip } from "@/components/catalog-highlights"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

// Home de la tienda: una vidriera corta. Buscar y las categorias llevan directo al catalogo (/catalogo); debajo,
// los destacados y unos pocos combos. El listado completo y sus filtros estan en /catalogo
export default function HomePage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState("")
  const categories = useCatalogCategories().data ?? []

  const handleSearch = (event: FormEvent) => {
    event.preventDefault()
    navigate(catalogUrl({ q: query }))
  }

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8 space-y-12">
        <section className="space-y-5">
          <h1 className="text-heading-sm">
            <Headline text={strings.home.heroTitle} highlight={strings.home.heroHighlight} />
          </h1>
          <form onSubmit={handleSearch} role="search" className="flex max-w-2xl gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={strings.productCatalog.searchPlaceholder}
                aria-label={strings.productCatalog.searchPlaceholder}
                className="h-11 pl-10"
              />
            </div>
            <Button type="submit" className="h-11">
              {strings.home.search}
            </Button>
          </form>
          {categories.length > 0 ? (
            <nav aria-label={strings.home.categoriesLabel} className="flex flex-wrap gap-2">
              {categories.map((category) => (
                <Link
                  key={category.name}
                  to={catalogUrl({ category: category.name })}
                  className="rounded-full border border-border bg-card px-3 py-1.5 text-sm text-foreground transition-colors hover:border-foreground/30 hover:bg-muted"
                >
                  {category.name} <span className="text-muted-foreground">{category.products}</span>
                </Link>
              ))}
            </nav>
          ) : null}
        </section>

        <FeaturedStrip />
        <BundlesStrip />
      </main>

      <Footer />
    </div>
  )
}
