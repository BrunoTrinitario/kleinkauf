
import { useState } from "react"
import { Link } from "react-router-dom"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import type { LoginUserDto } from "@/lib/types"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import { IS_PLATFORM } from "@/lib/platform"
import { Headline } from "@/components/highlight"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Eye, EyeOff, ArrowRight } from "lucide-react"
export default function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState("")

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")
    setIsLoading(true)

    try {
      const payload: LoginUserDto = { email, password }
      await login(payload)
      navigate("/")
    } catch (err) {
      setError(
        getErrorMessage(err, strings.login.genericError, {
          unauthorized: strings.login.invalidCredentials,
          badRequest: strings.login.invalidCredentials,
        })
      )
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-heading-sm text-foreground">
            {IS_PLATFORM ? (
              <Headline text={strings.platformLogin.title} highlight={strings.platformLogin.highlight} />
            ) : (
              <Headline text={strings.login.title} highlight="nuevo" />
            )}
          </h1>
          <p className="text-muted-foreground mt-2">
            {IS_PLATFORM ? strings.platformLogin.subtitle : strings.login.subtitle}
          </p>
        </div>

        <Card className="border-border/50">
          <form onSubmit={handleSubmit} className="flex flex-col gap-6">
            <CardHeader className="pb-4">
              <CardTitle>{strings.login.cardTitle}</CardTitle>
              <CardDescription>
                {IS_PLATFORM ? strings.platformLogin.cardDescription : strings.login.cardDescription}
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              {error && (
                <div className="bg-destructive/10 text-destructive text-sm px-4 py-3 rounded-md">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <label htmlFor="email" className="text-sm font-medium text-foreground">
                  {strings.login.emailLabel}
                </label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="password" className="text-sm font-medium text-foreground">
                  {strings.login.passwordLabel}
                </label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    autoComplete="current-password"
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                    aria-label={showPassword ? strings.login.hidePassword : strings.login.showPassword}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>
            </CardContent>

            <CardFooter className="flex flex-col gap-4">
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <span className="flex items-center gap-2">
                    <span className="h-4 w-4 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full animate-spin" />
                    {strings.login.submitting}
                  </span>
                ) : (
                  <span className="flex items-center gap-2">
                    {strings.login.submit}
                    <ArrowRight className="h-4 w-4" />
                  </span>
                )}
              </Button>

              {/* En la plataforma no hay registro: los superadmins se crean desde el backend */}
              {!IS_PLATFORM && (
                <p className="text-sm text-muted-foreground text-center">
                  {strings.login.noAccount}{" "}
                  <Link to="/register" className="text-foreground font-medium hover:underline">
                    {strings.login.createOne}
                  </Link>
                </p>
              )}
            </CardFooter>
          </form>
        </Card>
      </div>
    </div>
  )
}
