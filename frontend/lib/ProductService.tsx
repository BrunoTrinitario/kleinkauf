import type { Product } from "@/lib/types"

const API_URL = "/api/products"

export async function getProductById(id: string): Promise<Product> {
    const response = await fetch(`${API_URL}/${id}`, { cache: "no-store" })
    if (!response.ok) {
        throw new Error(`Failed to fetch product: ${response.status}`)
    }
    const data = (await response.json()) as Product
    return data
}

export async function getAllProducts(): Promise<Product[]> {
    const response = await fetch(API_URL, { cache: "no-store" })
    if (!response.ok) {
        throw new Error(`Failed to fetch products: ${response.status}`)
    }

    const data = (await response.json()) as Product[]
    return data
}
