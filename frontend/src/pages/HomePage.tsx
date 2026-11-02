import { Footer } from "@/components/footer"
import { Header } from "@/components/header"
import { ProductCatalog } from "@/components/product-catalog"

export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8">
        <ProductCatalog />
      </main>

      <Footer />
    </div>
  )
}
