import axios from "axios"
import { NextResponse } from "next/server"

const apiBaseUrl = process.env.API_BASE_URL
const apiBasePublic = process.env.API_BASE_PUBLIC

export async function GET(
  _request: Request,
  context: { params: { id: string } }
) {
  if (!apiBaseUrl) {
    return NextResponse.json({ error: "API_BASE_URL is not set" }, { status: 500 })
  }

  const { id } = context.params
  try {
    const response = await axios.get(`${apiBaseUrl}/products/${id}`, {
      headers: { "Cache-Control": "no-store" },
    })
    return NextResponse.json(response.data)
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status ?? 502
      return NextResponse.json(
        { error: `Failed to fetch product: ${status}` },
        { status }
      )
    }

    return NextResponse.json({ error: "Unexpected error" }, { status: 500 })
  }
}
