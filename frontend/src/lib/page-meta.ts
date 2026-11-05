import { useEffect } from "react"
import { useLocation } from "react-router-dom"
import { useStoreConfig } from "@/api/store"
import { productImageUrl } from "@/lib/api"
import { strings } from "@/lib/strings"

/*
Search engine and social preview tags of the store pages (title, description, Open Graph, canonical and robots).
- Public pages call usePageMeta with what describes them (the product page, its name, description and photo).
- useRouteSeo (in StoreRoutes) sets robots for every route (the panel, the cart and the account are noindex) and,
  on pages that do not call usePageMeta, goes back to the store's defaults.
Google runs JavaScript, so it reads these tags. Social network crawlers do not: a shared product goes through
/share/{store}/products/{slug}, which returns its Open Graph from the server.
 */

export interface PageMeta {
  // Without the store name: it is added ("Mesa de roble · Casa Roble")
  title?: string
  description?: string | null
  // File under /products/images/; without it, the store icon if it has one
  image?: string | null
  type?: "website" | "product"
}

// Only these are indexed; the rest (login, cart, checkout, orders, account, panel) are noindex
const PUBLIC_PATHS = [/^\/$/, /^\/catalogo$/, /^\/combos$/, /^\/productos\/[^/]+$/, /^\/privacidad$/, /^\/terminos$/]
const MAX_DESCRIPTION = 160
// Set while a page with its own tags is shown, so the route defaults do not overwrite them
const PAGE_META_FLAG = "pageMeta"

export function isIndexable(pathname: string) {
  return PUBLIC_PATHS.some((pattern) => pattern.test(pathname))
}

function setMeta(attribute: "name" | "property", key: string, content: string | null) {
  let tag = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (content === null) {
    tag?.remove()
    return
  }
  if (!tag) {
    tag = document.createElement("meta")
    tag.setAttribute(attribute, key)
    document.head.appendChild(tag)
  }
  tag.content = content
}

function setCanonical(href: string | null) {
  let link = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (href === null) {
    link?.remove()
    return
  }
  if (!link) {
    link = document.createElement("link")
    link.rel = "canonical"
    document.head.appendChild(link)
  }
  link.href = href
}

function shorten(text: string) {
  const clean = text.replace(/\s+/g, " ").trim()
  return clean.length <= MAX_DESCRIPTION ? clean : `${clean.slice(0, MAX_DESCRIPTION - 1).trimEnd()}…`
}

function apply(title: string, description: string, url: string | null, image: string | null, type: string, site: string) {
  document.title = title
  setMeta("name", "description", description)
  setMeta("property", "og:title", title)
  setMeta("property", "og:description", description)
  setMeta("property", "og:type", type)
  setMeta("property", "og:site_name", site)
  setMeta("property", "og:url", url)
  setMeta("property", "og:image", image)
  setMeta("name", "twitter:card", image ? "summary_large_image" : "summary")
  setCanonical(url)
}

export function usePageMeta(meta: PageMeta) {
  const store = useStoreConfig().data
  const { pathname } = useLocation()
  const { title, description, image, type = "website" } = meta

  useEffect(() => {
    if (!store) return
    const root = document.documentElement
    root.dataset[PAGE_META_FLAG] = "1"
    const picture = image ?? store.icon
    apply(
      title ? `${title} · ${store.name}` : store.name,
      shorten(description || strings.seo.storeDescription(store.name)),
      // Without query: /catalogo?pagina=2 and /catalogo are the same page for search engines
      window.location.origin + pathname,
      picture ? productImageUrl(picture) : null,
      type,
      store.name
    )
    return () => {
      delete root.dataset[PAGE_META_FLAG]
    }
  }, [store, pathname, title, description, image, type])
}

// Robots on every route and the store defaults on pages without their own tags. StoreRoutes calls it
export function useRouteSeo() {
  const store = useStoreConfig().data
  const { pathname } = useLocation()

  useEffect(() => {
    setMeta("name", "robots", isIndexable(pathname) ? "index, follow" : "noindex, nofollow")
    // Child effects run first: a page that has its own tags already set them
    if (!store || document.documentElement.dataset[PAGE_META_FLAG]) return
    apply(store.name, strings.seo.storeDescription(store.name), null, null, "website", store.name)
  }, [store, pathname])
}

// The platform (superadmin panel) is not for search engines
export function usePlatformSeo() {
  useEffect(() => {
    setMeta("name", "robots", "noindex, nofollow")
  }, [])
}
