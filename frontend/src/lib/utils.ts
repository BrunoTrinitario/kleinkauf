import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const priceFormat = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })

// Precio del catalogo: null = el producto todavia no tiene precio
export function formatPrice(price: number | null, ask: string) {
  return price === null ? ask : priceFormat.format(price)
}
