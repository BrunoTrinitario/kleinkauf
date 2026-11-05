import type { CatalogSort } from "./types"

/*
Catalog filters in the URL (/catalogo?q=&categoria=&orden=&pagina=): back returns to the same listing and a search or
a category can be shared. pagina starts at 1 in the URL and at 0 in the backend. Default values are not
written (orden=name, pagina=1).
 */

export const CATALOG_PATH = "/catalogo"

const SORTS: CatalogSort[] = ["name", "price_asc", "price_desc"]

export interface CatalogFilters {
  q: string
  category: string
  sort: CatalogSort
  page: number
}

export function readCatalogFilters(params: URLSearchParams): CatalogFilters {
  const sort = params.get("orden") as CatalogSort | null
  const page = Number.parseInt(params.get("pagina") ?? "", 10)
  return {
    q: params.get("q") ?? "",
    category: params.get("categoria") ?? "",
    sort: sort && SORTS.includes(sort) ? sort : "name",
    page: Number.isFinite(page) && page > 1 ? page - 1 : 0,
  }
}

export function catalogSearch(filters: Partial<CatalogFilters>): string {
  const params = new URLSearchParams()
  if (filters.q?.trim()) params.set("q", filters.q.trim())
  if (filters.category) params.set("categoria", filters.category)
  if (filters.sort && filters.sort !== "name") params.set("orden", filters.sort)
  if (filters.page && filters.page > 0) params.set("pagina", String(filters.page + 1))
  const search = params.toString()
  return search ? `?${search}` : ""
}

export const catalogUrl = (filters: Partial<CatalogFilters> = {}) => `${CATALOG_PATH}${catalogSearch(filters)}`
