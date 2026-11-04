import { Footer } from "@/components/footer"
import { Header } from "@/components/header"
import { BundlesStrip, CatalogStrip, FeaturedStrip } from "@/components/catalog-highlights"

// Store home: a short showcase. Featured products, bundles and the first catalog page, which ends with the link
// to /catalogo (the full listing, search and filters live there)
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
