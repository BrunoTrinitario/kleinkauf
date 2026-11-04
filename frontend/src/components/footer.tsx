import { useState } from "react"
import { strings } from "@/lib/strings"
import { BugReportDialog } from "@/components/bug-report-dialog"
import { Bug } from "lucide-react"

export function Footer() {
  const [isReportOpen, setIsReportOpen] = useState(false)

  return (
    <footer className="border-t border-border bg-card mt-16">
      <div className="container mx-auto px-4 py-8">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            {strings.footer.platform}
          </p>
          <button
            type="button"
            onClick={() => setIsReportOpen(true)}
            className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <Bug className="h-4 w-4" />
            {strings.footer.reportBug}
          </button>
        </div>
      </div>
      <BugReportDialog open={isReportOpen} onOpenChange={setIsReportOpen} />
    </footer>
  )
}
