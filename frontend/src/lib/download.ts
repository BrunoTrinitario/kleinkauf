import { api } from "./api"

// El archivo viene del backend (con BOM): se descarga tal cual, con el nombre que manda
export async function downloadCsv(path: string, fallbackName: string, params?: Record<string, unknown>) {
  const response = await api.get<Blob>(path, { responseType: "blob", params })
  const disposition = String(response.headers["content-disposition"] ?? "")
  const name = disposition.match(/filename="?([^";]+)"?/)?.[1] ?? fallbackName
  const url = URL.createObjectURL(response.data)
  const link = document.createElement("a")
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}
