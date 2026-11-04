import { useEffect } from "react"
import { useStoreConfig } from "@/api/store"
import { productImageUrl } from "@/lib/api"
import { applyStoreTheme, rememberStoreTheme } from "@/lib/store-themes"

const DEFAULT_FAVICON = "/icon.svg"

// Applies what the store configured: the color theme, its name as the tab title and its icon as the favicon
// (if enabled). Until the settings arrive, index.html applies (OCEAN theme, KleinKauf icon). StoreRoutes calls it,
// so it runs on every store page
export function useStoreBranding() {
  const config = useStoreConfig().data

  useEffect(() => {
    if (!config) return
    applyStoreTheme(config.theme)
    rememberStoreTheme(config.theme)
    document.title = config.name

    const favicon = config.icon && config.showIconAsFavicon ? productImageUrl(config.icon) : DEFAULT_FAVICON
    let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
    if (!link) {
      link = document.createElement("link")
      link.rel = "icon"
      document.head.appendChild(link)
    }
    link.href = favicon
    // An uploaded icon is PNG/JPG/WEBP/GIF: without a type the browser infers it from the file
    if (favicon === DEFAULT_FAVICON) link.type = "image/svg+xml"
    else link.removeAttribute("type")
  }, [config])
}
