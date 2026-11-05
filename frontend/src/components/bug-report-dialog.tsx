import { useState } from "react"
import { toast } from "sonner"
import { useCreateBugReport } from "@/api/bug-reports"
import { useAuth } from "@/lib/auth-context"
import { getErrorMessage } from "@/lib/api-error"
import { strings } from "@/lib/strings"
import { BUG_REPORT_MAX_LENGTH, BUG_REPORT_MIN_LENGTH, EMAIL_MAX_LENGTH, isValidEmail } from "@/lib/validation"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

interface BugReportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// "Report a problem" from the footer. With an open session it does not ask for an email: the backend uses the account's
export function BugReportDialog({ open, onOpenChange }: BugReportDialogProps) {
  const { user } = useAuth()
  const createReport = useCreateBugReport()
  const [description, setDescription] = useState("")
  const [email, setEmail] = useState("")
  const [error, setError] = useState<string | null>(null)

  const length = description.trim().length
  const tooShort = description.length > 0 && length < BUG_REPORT_MIN_LENGTH
  const emailError = email.trim() !== "" && !isValidEmail(email)
  const canSend =
    length >= BUG_REPORT_MIN_LENGTH && length <= BUG_REPORT_MAX_LENGTH && !emailError && !createReport.isPending

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
    if (!nextOpen) {
      setDescription("")
      setEmail("")
      setError(null)
    }
  }

  const handleSend = async () => {
    if (!canSend) return
    setError(null)
    try {
      await createReport.mutateAsync({
        description: description.trim(),
        email: user ? undefined : email.trim() || undefined,
        pageUrl: window.location.href,
      })
      toast.success(strings.bugReport.sent)
      handleOpenChange(false)
    } catch (err) {
      setError(getErrorMessage(err, strings.bugReport.sendError))
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{strings.bugReport.title}</DialogTitle>
          <DialogDescription>{strings.bugReport.description}</DialogDescription>
        </DialogHeader>

        <div className="grid gap-3">
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <div className="grid gap-2">
            <label htmlFor="bug-report-description" className="text-sm font-medium">
              {strings.bugReport.whatHappened}
            </label>
            <Textarea
              id="bug-report-description"
              rows={5}
              value={description}
              maxLength={BUG_REPORT_MAX_LENGTH}
              placeholder={strings.bugReport.placeholder}
              aria-invalid={tooShort}
              onChange={(event) => setDescription(event.target.value)}
            />
            <div className="flex justify-between gap-2 text-xs text-muted-foreground">
              <span className={tooShort ? "text-destructive" : undefined}>
                {tooShort ? strings.bugReport.tooShort(BUG_REPORT_MIN_LENGTH) : ""}
              </span>
              <span>{strings.bugReport.length(description.length, BUG_REPORT_MAX_LENGTH)}</span>
            </div>
          </div>
          {user ? (
            <p className="text-xs text-muted-foreground">{strings.bugReport.loggedInHint(user.email)}</p>
          ) : (
            <div className="grid gap-2">
              <label htmlFor="bug-report-email" className="text-sm font-medium">
                {strings.bugReport.email}
              </label>
              <Input
                id="bug-report-email"
                type="email"
                value={email}
                maxLength={EMAIL_MAX_LENGTH}
                aria-invalid={emailError}
                onChange={(event) => setEmail(event.target.value)}
              />
              <p className={`text-xs ${emailError ? "text-destructive" : "text-muted-foreground"}`}>
                {emailError ? strings.validation.emailInvalid : strings.bugReport.emailHint}
              </p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)}>
            {strings.bugReport.cancel}
          </Button>
          <Button onClick={handleSend} disabled={!canSend}>
            {createReport.isPending ? strings.bugReport.sending : strings.bugReport.send}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
