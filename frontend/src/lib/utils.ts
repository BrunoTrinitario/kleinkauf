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

const dateTimeFormat = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

// Algunas fechas del backend llegan sin zona (LocalDateTime en la hora del servidor, UTC en Docker): sin la Z el
// navegador las tomaria como hora local y se verian corridas
export function formatServerDateTime(value: string) {
  const hasZone = /([zZ]|[+-]\d\d:?\d\d)$/.test(value)
  return dateTimeFormat.format(new Date(hasZone ? value : `${value}Z`))
}
