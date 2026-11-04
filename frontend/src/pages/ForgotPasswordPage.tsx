import { useState } from "react"
import { Link } from "react-router-dom"
import { useRequestPasswordReset } from "@/api/account"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { EMAIL_MAX_LENGTH, isValidEmail } from "@/lib/validation"
import { Headline } from "@/components/highlight"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { MailCheck } from "lucide-react"

/*
"Olvide mi contrasena" (/recuperar), solo en las tiendas. La respuesta es la misma exista o no el email (el backend
no lo dice), asi que siempre se muestra "revisa tu email". El link del mail lleva a /reset-password
 */
export default function ForgotPasswordPage() {
  const requestReset = useRequestPasswordReset()
  const [email, setEmail] = useState("")
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState("")

  const emailError = email.trim() !== "" && !isValidEmail(email)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!isValidEmail(email)) return
    setError("")
    try {
      await requestReset.mutateAsync(email.trim())
      setSentTo(email.trim())
    } catch (err) {
      setError(getErrorMessage(err, strings.forgotPassword.error))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-heading-sm text-foreground">
            <Headline text={strings.forgotPassword.title} highlight={strings.forgotPassword.highlight} />
          </h1>
          <p className="text-muted-foreground mt-2">{strings.forgotPassword.subtitle}</p>
        </div>

        <Card className="border-border/50">
          {sentTo ? (
            <>
              <CardHeader className="pb-4">
                <CardTitle className="flex items-center gap-2">
                  <MailCheck className="h-5 w-5" />
                  {strings.forgotPassword.sentTitle}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm text-muted-foreground">
                <p>{strings.forgotPassword.sent(sentTo)}</p>
                <p>{strings.forgotPassword.spamHint}</p>
              </CardContent>
              <CardFooter className="flex flex-col gap-3">
                <Button asChild className="w-full">
                  <Link to="/login">{strings.forgotPassword.backToLogin}</Link>
                </Button>
                <Button variant="ghost" className="w-full" onClick={() => setSentTo(null)}>
                  {strings.forgotPassword.tryAnother}
                </Button>
              </CardFooter>
            </>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
              <CardHeader className="pb-4">
                <CardTitle>{strings.forgotPassword.cardTitle}</CardTitle>
                <CardDescription>{strings.forgotPassword.cardDescription}</CardDescription>
              </CardHeader>

              <CardContent className="space-y-4">
                {error && (
                  <div className="bg-destructive/10 text-destructive text-sm px-4 py-3 rounded-md">{error}</div>
                )}
                <div className="space-y-2">
                  <label htmlFor="email" className="text-sm font-medium text-foreground">
                    {strings.forgotPassword.emailLabel}
                  </label>
                  <Input
                    id="email"
                    type="email"
                    value={email}
                    maxLength={EMAIL_MAX_LENGTH}
                    aria-invalid={emailError}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoComplete="email"
                  />
                  {emailError ? <p className="text-xs text-destructive">{strings.validation.emailInvalid}</p> : null}
                </div>
              </CardContent>

              <CardFooter className="flex flex-col gap-4">
                <Button type="submit" className="w-full" disabled={!isValidEmail(email) || requestReset.isPending}>
                  {requestReset.isPending ? strings.forgotPassword.submitting : strings.forgotPassword.submit}
                </Button>
                <Link to="/login" className="text-sm text-muted-foreground hover:text-foreground hover:underline">
                  {strings.forgotPassword.backToLogin}
                </Link>
              </CardFooter>
            </form>
          )}
        </Card>
      </div>
    </div>
  )
}
