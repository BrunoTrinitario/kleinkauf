import { useState } from "react"
import axios from "axios"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { useResetPassword } from "@/api/account"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { isValidPassword } from "@/lib/validation"
import { Headline } from "@/components/highlight"
import { PasswordRequirements } from "@/components/password-requirements"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"

/*
Where the recovery email link leads: /reset-password?token=. The token is valid for 15 minutes and only once; if
it expired or was already used, the backend responds 401 and requesting another one is offered. When done it goes to login with a notice
 */
export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")
  const resetPassword = useResetPassword()
  const [password, setPassword] = useState("")
  const [confirm, setConfirm] = useState("")
  const [error, setError] = useState("")
  const [linkExpired, setLinkExpired] = useState(false)

  const mismatch = confirm.length > 0 && password !== confirm
  const canSubmit = !!token && isValidPassword(password) && password === confirm && !resetPassword.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit || !token) return
    setError("")
    try {
      await resetPassword.mutateAsync({ token, newPassword: password })
      navigate("/login?contrasena=cambiada", { replace: true })
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        setLinkExpired(true)
        return
      }
      setError(getErrorMessage(err, strings.resetPassword.error))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-heading-sm text-foreground">
            <Headline text={strings.resetPassword.title} highlight={strings.resetPassword.highlight} />
          </h1>
          <p className="text-muted-foreground mt-2">{strings.resetPassword.subtitle}</p>
        </div>

        <Card className="border-border/50">
          {!token || linkExpired ? (
            <>
              <CardHeader className="pb-4">
                <CardTitle>{strings.resetPassword.cardTitle}</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="bg-destructive/10 text-destructive text-sm px-4 py-3 rounded-md">
                  {token ? strings.resetPassword.invalidLink : strings.resetPassword.missingToken}
                </div>
              </CardContent>
              <CardFooter>
                <Button asChild className="w-full">
                  <Link to="/recuperar">{strings.resetPassword.requestNew}</Link>
                </Button>
              </CardFooter>
            </>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
              <CardHeader className="pb-4">
                <CardTitle>{strings.resetPassword.cardTitle}</CardTitle>
              </CardHeader>

              <CardContent className="space-y-4">
                {error && (
                  <div className="bg-destructive/10 text-destructive text-sm px-4 py-3 rounded-md">{error}</div>
                )}
                <div className="space-y-2">
                  <label htmlFor="new-password" className="text-sm font-medium text-foreground">
                    {strings.resetPassword.passwordLabel}
                  </label>
                  <Input
                    id="new-password"
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    autoComplete="new-password"
                  />
                  <PasswordRequirements password={password} />
                </div>
                <div className="space-y-2">
                  <label htmlFor="confirm-password" className="text-sm font-medium text-foreground">
                    {strings.resetPassword.confirmLabel}
                  </label>
                  <Input
                    id="confirm-password"
                    type="password"
                    value={confirm}
                    aria-invalid={mismatch}
                    onChange={(e) => setConfirm(e.target.value)}
                    required
                    autoComplete="new-password"
                  />
                  {mismatch ? <p className="text-xs text-destructive">{strings.resetPassword.mismatch}</p> : null}
                </div>
              </CardContent>

              <CardFooter>
                <Button type="submit" className="w-full" disabled={!canSubmit}>
                  {resetPassword.isPending ? strings.resetPassword.submitting : strings.resetPassword.submit}
                </Button>
              </CardFooter>
            </form>
          )}
        </Card>
      </div>
    </div>
  )
}
