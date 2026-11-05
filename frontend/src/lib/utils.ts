import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const priceFormat = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })

// Catalog price: null = the product has no price yet
export function formatPrice(price: number | null, ask: string) {
  return price === null ? ask : priceFormat.format(price)
}

const dateTimeFormat = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

// Some backend dates arrive without a zone (LocalDateTime in server time, UTC in Docker): without the Z the
// browser would take them as local time and they would show shifted
export function parseServerDateTime(value: string) {
  const hasZone = /([zZ]|[+-]\d\d:?\d\d)$/.test(value)
  return new Date(hasZone ? value : `${value}Z`)
}

export function formatServerDateTime(value: string) {
  return dateTimeFormat.format(parseServerDateTime(value))
}

// Amount without the "consultar precio" case (cart and orders always have a price)
export function formatAmount(amount: number) {
  return priceFormat.format(amount)
}
