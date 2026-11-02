
import { useState } from "react"
import { Link } from "react-router-dom"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Eye, EyeOff, ArrowRight, Check } from "lucide-react"

export default function RegisterPage() {
  const navigate = useNavigate()
  const { register } = useAuth()
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState("")

  const passwordRequirements = [
    { label: strings.register.passwordReqMin, met: password.length >= 8 },
    { label: strings.register.passwordReqNumber, met: /\d/.test(password) },
    { label: strings.register.passwordReqLetter, met: /[a-zA-Z]/.test(password) },
  ]

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")

    if (password !== confirmPassword) {
      setError(strings.register.passwordMismatch)
      return
    }

    if (!passwordRequirements.every(req => req.met)) {
      setError(strings.register.passwordReqsError)
      return
    }

    setIsLoading(true)

    try {
      const success = await register(email, password, name)
      if (success) {
        navigate("/")
      } else {
        setError(strings.register.failed)
      }
    } catch {
      setError(strings.register.genericError)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 py-12">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            {strings.register.title}
          </h1>
          <p className="text-muted-foreground mt-2">
            {strings.register.subtitle}
          </p>
        </div>

        <Card className="border-border/50">
          <form onSubmit={handleSubmit}>
            <CardHeader className="pb-4">
              <CardTitle className="text-lg">{strings.register.cardTitle}</CardTitle>
              <CardDescription>
                {strings.register.cardDescription}
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              {error && (
                <div className="bg-destructive/10 text-destructive text-sm px-4 py-3 rounded-md">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <label htmlFor="name" className="text-sm font-medium text-foreground">
                  {strings.register.fullName}
                </label>
                <Input
                  id="name"
                  type="text"
                  placeholder={strings.register.namePlaceholder}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  autoComplete="name"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="email" className="text-sm font-medium text-foreground">
                  {strings.register.email}
                </label>
                <Input
                  id="email"
                  type="email"
                  placeholder={strings.register.emailPlaceholder}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="password" className="text-sm font-medium text-foreground">
                  {strings.register.password}
                </label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder={strings.register.passwordPlaceholder}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    autoComplete="new-password"
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

                <div className="space-y-1 pt-2">
                  {passwordRequirements.map((req, index) => (
                    <div key={index} className="flex items-center gap-2 text-xs">
                      <div className={`h-4 w-4 rounded-full flex items-center justify-center ${req.met ? "bg-primary text-primary-foreground" : "bg-muted"}`}>
                        {req.met && <Check className="h-3 w-3" />}
                      </div>
                      <span className={req.met ? "text-foreground" : "text-muted-foreground"}>
                        {req.label}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <label htmlFor="confirmPassword" className="text-sm font-medium text-foreground">
                  {strings.register.confirmPassword}
                </label>
                <Input
                  id="confirmPassword"
                  type={showPassword ? "text" : "password"}
                  placeholder={strings.register.confirmPasswordPlaceholder}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  autoComplete="new-password"
                />
              </div>
            </CardContent>

            <CardFooter className="flex flex-col gap-4">
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <span className="flex items-center gap-2">
                    <span className="h-4 w-4 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full animate-spin" />
                    {strings.register.submitting}
                  </span>
                ) : (
                  <span className="flex items-center gap-2">
                    {strings.register.submit}
                    <ArrowRight className="h-4 w-4" />
                  </span>
                )}
              </Button>

              <p className="text-sm text-muted-foreground text-center">
                {strings.register.alreadyAccount}{" "}
                <Link to="/login" className="text-foreground font-medium hover:underline">
                  {strings.register.signIn}
                </Link>
              </p>
            </CardFooter>
          </form>
        </Card>
      </div>
    </div>
  )
}
