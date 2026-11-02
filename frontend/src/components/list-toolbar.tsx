import type { ReactNode } from "react"
import { Input } from "@/components/ui/input"
import { Search } from "lucide-react"

// Buscador y filtros de los listados (catalogo y paneles): siempre en una fila, sin card

export function ListToolbar({ children }: { children: ReactNode }) {
  return <div className="flex flex-col sm:flex-row gap-4">{children}</div>
}

interface SearchFieldProps {
  value: string
  onChange: (value: string) => void
  placeholder: string
}

export function SearchField({ value, onChange, placeholder }: SearchFieldProps) {
  return (
    <div className="relative flex-1">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
      <Input
        type="text"
        placeholder={placeholder}
        aria-label={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="pl-10"
      />
    </div>
  )
}

// Los selects van juntos a la derecha del buscador
export function FilterGroup({ children }: { children: ReactNode }) {
  return <div className="flex gap-2">{children}</div>
}

interface FilterSelectProps {
  value: string
  onChange: (value: string) => void
  label: string
  children: ReactNode
}

export function FilterSelect({ value, onChange, label, children }: FilterSelectProps) {
  return (
    <select
      value={value}
      aria-label={label}
      onChange={(e) => onChange(e.target.value)}
      className="h-9 min-w-0 flex-1 sm:flex-none px-3 rounded-md border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring"
    >
      {children}
    </select>
  )
}
