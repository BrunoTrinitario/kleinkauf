import { useEffect, useState } from "react"

// Devuelve value recien cuando deja de cambiar por delayMs (para no pegarle al servidor por cada tecla)
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timeout = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timeout)
  }, [value, delayMs])

  return debounced
}
