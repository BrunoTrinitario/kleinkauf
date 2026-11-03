import type { PeriodGroupBy } from "./types"

// Rangos fijos de los graficos de los paneles (plataforma y tienda)
export type RangeKey = "days" | "weeks" | "months"

export interface PeriodRange {
  from: string
  to: string
  groupBy: PeriodGroupBy
}

const pad = (n: number) => String(n).padStart(2, "0")
const isoDate = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`

// Periodos completos que terminan hoy: 30 dias, 12 semanas (empiezan el lunes, como en el backend) o 12 meses
export function rangeParams(key: RangeKey): PeriodRange {
  const today = new Date()
  const from = new Date(today)
  if (key === "days") {
    from.setDate(today.getDate() - 29)
    return { from: isoDate(from), to: isoDate(today), groupBy: "DAY" }
  }
  if (key === "weeks") {
    const sinceMonday = (today.getDay() + 6) % 7
    from.setDate(today.getDate() - sinceMonday - 7 * 11)
    return { from: isoDate(from), to: isoDate(today), groupBy: "WEEK" }
  }
  from.setDate(1)
  from.setMonth(today.getMonth() - 11)
  return { from: isoDate(from), to: isoDate(today), groupBy: "MONTH" }
}
