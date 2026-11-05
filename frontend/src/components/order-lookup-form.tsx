import { useState, type FormEvent } from "react"
import { useNavigate } from "react-router-dom"
import { strings } from "@/lib/strings"
import { isValidEmail } from "@/lib/validation"
import { normalizeOrderNumber } from "@/lib/known-orders"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

interface OrderLookupFormProps {
  initialNumber?: string
  initialEmail?: string
  // Message above the form (e.g. "we could not find the order")
  message?: string | null
}

// Number + email: goes to the order screen, which does the lookup (POST, so the email does not go in the URL)
export function OrderLookupForm({ initialNumber = "", initialEmail = "", message }: OrderLookupFormProps) {
  const navigate = useNavigate()
  const [number, setNumber] = useState(initialNumber)
  const [email, setEmail] = useState(initialEmail)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const normalized = normalizeOrderNumber(number)
    if (normalized.length === 0 || !isValidEmail(email)) {
      setError(strings.orderLookup.invalid)
      return
    }
    setError(null)
    navigate(`/pedido/${encodeURIComponent(normalized)}`, { state: { email: email.trim().toLowerCase() } })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {message ? <p className="text-sm text-destructive" role="alert">{message}</p> : null}
      {error ? <p className="text-sm text-destructive" role="alert">{error}</p> : null}
      <div className="grid gap-2">
        <Label htmlFor="lookup-number">{strings.orderLookup.number}</Label>
        <Input
          id="lookup-number"
          autoCapitalize="characters"
          placeholder={strings.orderLookup.numberPlaceholder}
          value={number}
          onChange={(event) => setNumber(event.target.value)}
        />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="lookup-email">{strings.orderLookup.email}</Label>
        <Input
          id="lookup-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
      </div>
      <Button type="submit">{strings.orderLookup.submit}</Button>
    </form>
  )
}
