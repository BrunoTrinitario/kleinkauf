import { Footer } from "@/components/footer"
import { Header } from "@/components/header"
import { ProductCatalog } from "@/components/product-catalog"
import { CatalogBundles, CatalogFeatured } from "@/components/catalog-highlights"
import { strings } from "@/lib/strings"

export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8 space-y-12">
        <CatalogFeatured />
        <CatalogBundles />
        <section className="space-y-4">
          <h2 className="text-subheading">{strings.home.catalogTitle}</h2>
          <ProductCatalog />
        </section>
      </main>

      <Footer />
    </div>
  )
}
