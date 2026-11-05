import { strings } from "@/lib/strings"
import { checkPassword } from "@/lib/validation"
import { Check } from "lucide-react"

interface PasswordRequirementsProps {
  password: string
}

// List of password requirements that get ticked while typing
export function PasswordRequirements({ password }: PasswordRequirementsProps) {
  const checks = checkPassword(password)
  const requirements = [
    { label: strings.validation.passwordReqMin, met: checks.minLength },
    { label: strings.validation.passwordReqNumber, met: checks.hasNumber },
    { label: strings.validation.passwordReqLetter, met: checks.hasLetter },
  ]

  return (
    <div className="space-y-1 pt-2">
      {requirements.map((req) => (
        <div key={req.label} className="flex items-center gap-2 text-xs">
          <div className={`h-4 w-4 rounded-full flex items-center justify-center ${req.met ? "bg-primary text-primary-foreground" : "bg-muted"}`}>
            {req.met && <Check className="h-3 w-3" />}
          </div>
          <span className={req.met ? "text-foreground" : "text-muted-foreground"}>
            {req.label}
          </span>
        </div>
      ))}
      {/* The maximum is almost never reached: it is only flagged if exceeded */}
      {!checks.maxLength ? (
        <p className="text-xs text-destructive">{strings.validation.passwordTooLong}</p>
      ) : null}
    </div>
  )
}
