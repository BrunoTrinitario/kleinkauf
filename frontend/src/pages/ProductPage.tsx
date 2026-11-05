import { Link, useLocation, useNavigate, useParams } from "react-router-dom"
import { CATALOG_PATH } from "@/lib/catalog-url"
import axios from "axios"
import { productImageUrl } from "@/lib/api"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { useCatalogProduct } from "@/api/catalog"
import { Header } from "@/components/header"
import { Footer } from "@/components/footer"
import { ProductDetail } from "@/components/product-detail"
import { Card } from "@/components/ui/card"
import { ArrowLeft } from "lucide-react"

// Product page (/productos/:slug): it is the link that gets shared (see lib/share.ts). Public
export default function ProductPage() {
  const { slug = "" } = useParams()
  const navigate = useNavigate()
  // key "default" = it is the first page opened in this tab (there is nowhere to go back to inside the store)
  const cameFromStore = useLocation().key !== "default"
  const detail = useCatalogProduct(slug, slug !== "")
  const product = detail.data
  const notFound = axios.isAxiosError(detail.error) && detail.error.response?.status === 404

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header />
      <main className="container mx-auto flex-1 px-4 py-8 space-y-6">
        {/* From the store it goes back to where it was (the catalog with its filters); from a shared link, to the catalog */}
        {cameFromStore ? (
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            {strings.productPage.backPrevious}
          </button>
        ) : (
          <Link to={CATALOG_PATH} className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" />
            {strings.productPage.back}
          </Link>
        )}

        {product ? (
          <Card className="overflow-hidden p-0">
            <ProductDetail
              product={product}
              metadata={product.metadata}
              metadataError={null}
              imageSrcs={(product.images ?? []).map((name) => productImageUrl(name))}
            />
          </Card>
        ) : detail.isError ? (
          <p className="text-muted-foreground">
            {notFound ? strings.productPage.notFound : getErrorMessage(detail.error, strings.productPage.notFound)}
          </p>
        ) : (
          <p className="text-muted-foreground">{strings.productPage.loading}</p>
        )}
      </main>
      <Footer />
    </div>
  )
}
