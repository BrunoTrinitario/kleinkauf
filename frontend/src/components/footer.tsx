import { useState } from "react"
import { Link } from "react-router-dom"
import { strings } from "@/lib/strings"
import { BugReportDialog } from "@/components/bug-report-dialog"
import { StoreSocialLinks } from "@/components/social-links"
import { Bug } from "lucide-react"

const linkClass = "text-sm text-muted-foreground hover:text-foreground transition-colors"

export function Footer() {
  const [isReportOpen, setIsReportOpen] = useState(false)

  return (
    <footer className="border-t border-border bg-card mt-16">
      <div className="container mx-auto px-4 py-8">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          {/* The store shows its name at the top; the platform signs at the bottom */}
          <p className="text-sm text-muted-foreground">
            {strings.footer.poweredBy} <span className="font-medium text-foreground">{strings.site.name}</span>
          </p>
          <StoreSocialLinks />
          <nav className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2">
            <Link to="/privacidad" className={linkClass}>
              {strings.footer.privacy}
            </Link>
            <Link to="/terminos" className={linkClass}>
              {strings.footer.terms}
            </Link>
            <button
              type="button"
              onClick={() => setIsReportOpen(true)}
              className={`inline-flex items-center gap-1.5 ${linkClass}`}
            >
              <Bug className="h-4 w-4" />
              {strings.footer.reportBug}
            </button>
          </nav>
        </div>
      </div>
      <BugReportDialog open={isReportOpen} onOpenChange={setIsReportOpen} />
    </footer>
  )
}
