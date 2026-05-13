"use client"

import axios from "axios"
import { useEffect, useState } from "react"
import type { Product } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Footer } from "@/components/footer"
import { Header } from "@/components/header"
import { ProductCatalog } from "@/components/product-catalog"
export default function HomePage() {
  const [products, setProducts] = useState<Product[]>([])
  const [isLoadingProducts, setIsLoadingProducts] = useState(true)
  const apiBasePublic =
    process.env.NEXT_PUBLIC_API_BASE_PUBLIC ?? "http://localhost:3000"

  useEffect(() => {
    let isMounted = true

    const loadProducts = async () => {
      try {
        const response = await axios.get(`${apiBasePublic}/api/v1/products/all`)
        const data = response.data as Product[]
        if (isMounted) {
          setProducts(data)
        }
      } catch {
        if (isMounted) {
          setProducts([])
        }
      } finally {
        if (isMounted) {
          setIsLoadingProducts(false)
        }
      }
    }

    loadProducts()

    return () => {
      isMounted = false
    }
  }, [])

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        {isLoadingProducts ? (
          <p className="text-sm text-muted-foreground">{strings.home.loadingProducts}</p>
        ) : (
          <ProductCatalog products={products} />
        )}
      </main>

      <Footer />
    </div>
  )
}
