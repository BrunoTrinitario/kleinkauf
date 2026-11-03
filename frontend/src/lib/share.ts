import { API_BASE_URL } from "./api"

// Identificador de la tienda abierta: el primer label del host (mitienda.localhost -> mitienda)
export function storeIdentifier(): string {
  return window.location.hostname.split(".")[0]
}

/*
Link para compartir un producto: pasa por el gateway (/share/{tienda}/products/{slug}), que devuelve las etiquetas
Open Graph para la vista previa de WhatsApp o Instagram y redirige a la pagina del producto en la tienda.
Los crawlers no ejecutan JavaScript: compartir la URL de la SPA no mostraria la foto ni el precio.
 */
export function productShareUrl(slug: string): string {
  return `${API_BASE_URL}/share/${storeIdentifier()}/products/${encodeURIComponent(slug)}`
}

export function whatsappShareUrl(text: string, url: string): string {
  return `https://wa.me/?text=${encodeURIComponent(`${text} ${url}`)}`
}
