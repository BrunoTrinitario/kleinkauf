// Identificador de la tienda abierta: el primer label del host (mitienda.localhost -> mitienda)
export function storeIdentifier(): string {
  return window.location.hostname.split(".")[0]
}

/*
Link para compartir un producto: la pagina del producto en la tienda (https://mitienda.dominio/productos/{slug}).
Los crawlers no ejecutan JavaScript, asi que la SPA sola no mostraria la foto ni el precio en la vista previa de
WhatsApp o Instagram: en el server, Caddy le pasa esos pedidos al gateway (/share/{tienda}/products/{slug}), que
devuelve las etiquetas Open Graph. Las personas abren la SPA de siempre.
 */
export function productShareUrl(slug: string): string {
  return `${window.location.origin}/productos/${encodeURIComponent(slug)}`
}

export function whatsappShareUrl(text: string, url: string): string {
  return `https://wa.me/?text=${encodeURIComponent(`${text} ${url}`)}`
}
