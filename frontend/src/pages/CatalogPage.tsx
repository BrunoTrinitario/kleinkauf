import { strings } from "@/lib/strings"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { PageHeader } from "@/components/page-header"
import { ProductCatalog } from "@/components/product-catalog"

// Catalogo completo (/catalogo) con busqueda, categoria, orden y pagina en la URL. Publico
export default function CatalogPage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <PageHeader
          title={strings.catalogPage.title}
          highlight={strings.catalogPage.highlight}
          subtitle={strings.catalogPage.subtitle}
        />
        <ProductCatalog />
      </main>
      <Footer />
    </div>
  )
}
