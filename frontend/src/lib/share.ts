// Identifier of the open store: the first label of the host (mitienda.localhost -> mitienda)
export function storeIdentifier(): string {
  return window.location.hostname.split(".")[0]
}

/*
Link to share a product: the product page in the store (https://mitienda.dominio/productos/{slug}).
Crawlers do not run JavaScript, so the SPA alone would not show the photo or the price in the WhatsApp or
Instagram preview: on the server, Caddy passes those requests to the gateway (/share/{store}/products/{slug}), which
returns the Open Graph tags. People open the usual SPA.
 */
export function productShareUrl(slug: string): string {
  return `${window.location.origin}/productos/${encodeURIComponent(slug)}`
}

export function whatsappShareUrl(text: string, url: string): string {
  return `https://wa.me/?text=${encodeURIComponent(`${text} ${url}`)}`
}
