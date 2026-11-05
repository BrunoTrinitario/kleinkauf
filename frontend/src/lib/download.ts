import { api } from "./api"

// The file comes from the backend (with BOM): it is downloaded as is, with the name it sends
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
