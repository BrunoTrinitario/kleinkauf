import { Footer } from "@/components/footer"
import { Header } from "@/components/header"
import { BundlesStrip, CatalogStrip, FeaturedStrip } from "@/components/catalog-highlights"

// Home de la tienda: una vidriera corta. Destacados, unos pocos combos y la primera pagina del catalogo, que
// termina en el link a /catalogo (ahi estan el listado completo, la busqueda y los filtros)
export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8 space-y-12">
        <FeaturedStrip />
        <BundlesStrip />
        <CatalogStrip />
      </main>

      <Footer />
    </div>
  )
}
