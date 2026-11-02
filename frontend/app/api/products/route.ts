import axios from "axios"
import { NextResponse } from "next/server"

const apiBaseUrl = process.env.API_BASE_URL

export async function GET() {
  if (!apiBaseUrl) {
    return NextResponse.json({ error: "API_BASE_URL is not set" }, { status: 500 })
  }

  try {
    const response = await axios.get(`${apiBaseUrl}/products/all`, {
      headers: { "Cache-Control": "no-store" },
    })
    return NextResponse.json(response.data)
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 502
      return NextResponse.json(
        { error: `Failed to fetch products: ${status}` },
        { status }
      )
    }

    return NextResponse.json({ error: "Unexpected error" }, { status: 500 })
  }
}
