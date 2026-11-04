import { useEffect } from "react"
import { Link, useLocation } from "react-router-dom"
import { strings } from "@/lib/strings"
import { CATALOG_PATH } from "@/lib/catalog-url"
import { useCatalogBundles } from "@/api/catalog"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { BundleCard } from "@/components/catalog-highlights"
import { Button } from "@/components/ui/button"

// Combos que se pueden comprar (/combos). Desde la home se llega a uno con /combos#combo-{id}. Publica
export default function BundlesPage() {
  const bundlesQuery = useCatalogBundles()
  const bundles = bundlesQuery.data ?? []
  const { hash } = useLocation()

  // El router no baja solo al ancla: se hace cuando ya estan las tarjetas
  useEffect(() => {
    if (!hash || bundles.length === 0) return
    document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ block: "start" })
  }, [hash, bundles.length])

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <PageHeader
          title={strings.bundlesPage.title}
          highlight={strings.bundlesPage.highlight}
          subtitle={strings.home.bundlesDescription}
        />
        {bundlesQuery.isPending ? (
          <p className="text-muted-foreground">{strings.bundlesPage.loading}</p>
        ) : bundles.length === 0 ? (
          <div className="space-y-4">
            <p className="text-muted-foreground">{strings.bundlesPage.empty}</p>
            <Button asChild>
              <Link to={CATALOG_PATH}>{strings.cart.backToCatalog}</Link>
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {bundles.map((bundle) => (
              <BundleCard key={bundle.id} bundle={bundle} />
            ))}
          </div>
        )}
      </main>
      <Footer />
    </div>
  )
}
