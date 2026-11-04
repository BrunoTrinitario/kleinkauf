import { useState } from "react"
import { toast } from "sonner"
import { useBugReports, useSetBugReportResolved } from "@/api/platform"
import { strings } from "@/lib/strings"
import type { BugReport, BugReportStatus } from "@/lib/types"
import { usePlatformSession } from "@/hooks/use-platform-session"
import { FilterSelect } from "@/components/list-toolbar"
import { PageHeader } from "@/components/page-header"
import { PaginationControls } from "@/components/pagination-controls"
import { PlatformHeader } from "@/components/platform-header"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Check, Mail, RotateCcw, Store, User as UserIcon } from "lucide-react"

const dateTimeFormat = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" })

// Reportes de "Reportar un problema" del footer de las tiendas. Por defecto, los pendientes
export default function PlatformBugReportsPage() {
  const { ready, handleError } = usePlatformSession()
  const [status, setStatus] = useState<BugReportStatus>("pending")
  const [page, setPage] = useState(0)
  const [savingId, setSavingId] = useState<number | null>(null)
  const reportsQuery = useBugReports(status, page, ready)
  const setResolved = useSetBugReportResolved()
  const reports = reportsQuery.data

  if (!ready) {
    return null
  }

  const handleStatusChange = (value: string) => {
    setStatus(value as BugReportStatus)
    setPage(0)
  }

  const handleToggle = async (report: BugReport, resolved: boolean) => {
    setSavingId(report.id)
    try {
      await setResolved.mutateAsync({ id: report.id, resolved })
      toast.success(resolved ? strings.platformBugReports.resolvedToast : strings.platformBugReports.pendingToast)
      // Sale de la lista filtrada: si era el ultimo de la pagina, volver a la anterior
      if (status !== "all" && reports && reports.items.length === 1 && page > 0) {
        setPage(page - 1)
      }
    } catch (err) {
      handleError(err, strings.errors.saveBugReport)
    } finally {
      setSavingId(null)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <PlatformHeader />
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.platformBugReports.title}
          highlight={strings.platformBugReports.highlight}
          subtitle={strings.platformBugReports.subtitle}
        />

        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
            <div className="space-y-1.5">
              <CardTitle>
                {strings.platformBugReports.listTitle} ({reports?.totalItems ?? 0})
              </CardTitle>
              {reports ? (
                <CardDescription>{strings.platformBugReports.pendingCount(reports.pending)}</CardDescription>
              ) : null}
            </div>
            <FilterSelect value={status} onChange={handleStatusChange} label={strings.platformBugReports.statusLabel}>
              <option value="pending">{strings.platformBugReports.statuses.pending}</option>
              <option value="resolved">{strings.platformBugReports.statuses.resolved}</option>
              <option value="all">{strings.platformBugReports.statuses.all}</option>
            </FilterSelect>
          </CardHeader>
          <CardContent className="space-y-4 px-3 sm:px-6">
            {reportsQuery.isPending ? (
              <p className="text-sm text-muted-foreground">{strings.platformBugReports.loading}</p>
            ) : !reports || reports.items.length === 0 ? (
              // Si la lista falla se muestra vacia (el aviso sale del QueryClient)
              <p className="text-sm text-muted-foreground">{strings.platformBugReports.empty[status]}</p>
            ) : (
              reports.items.map((report) => (
                <BugReportItem
                  key={report.id}
                  report={report}
                  saving={savingId === report.id}
                  onToggle={(resolved) => handleToggle(report, resolved)}
                />
              ))
            )}
            <PaginationControls
              page={page}
              totalPages={reports?.totalPages ?? 0}
              disabled={reportsQuery.isFetching}
              onPageChange={setPage}
            />
          </CardContent>
        </Card>
      </main>
    </div>
  )
}

interface BugReportItemProps {
  report: BugReport
  saving: boolean
  onToggle: (resolved: boolean) => void
}

function BugReportItem({ report, saving, onToggle }: BugReportItemProps) {
  const resolved = report.resolvedAt !== null

  return (
    <div className={`p-4 rounded-lg border border-border space-y-3 ${resolved ? "opacity-70" : ""}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm min-w-0">
          <span className="flex items-center gap-1 font-medium text-foreground min-w-0">
            <Store className="h-4 w-4 shrink-0 text-muted-foreground" />
            {report.storeUrl ? (
              <a href={report.storeUrl} target="_blank" rel="noreferrer" className="truncate hover:underline">
                {report.store}
              </a>
            ) : (
              <span className="truncate">{report.store ?? strings.platformBugReports.noStore}</span>
            )}
          </span>
          <span className="text-muted-foreground">{dateTimeFormat.format(new Date(report.createdAt))}</span>
        </div>
        {resolved ? (
          <Badge className="bg-muted text-muted-foreground">{strings.platformBugReports.resolvedBadge}</Badge>
        ) : (
          <Badge className="bg-sky-wash text-cyan-edge">{strings.platformBugReports.pendingBadge}</Badge>
        )}
      </div>

      <p className="text-sm text-foreground whitespace-pre-wrap break-words">{report.description}</p>

      <div className="text-xs text-muted-foreground space-y-1 min-w-0">
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="flex items-center gap-1">
            <UserIcon className="h-3 w-3 shrink-0" />
            {report.reporterName ?? strings.platformBugReports.anonymous}
          </span>
          <span className="flex items-center gap-1 min-w-0">
            <Mail className="h-3 w-3 shrink-0" />
            {report.email ? (
              <a href={`mailto:${report.email}`} className="truncate hover:underline">
                {report.email}
              </a>
            ) : (
              strings.platformBugReports.noEmail
            )}
          </span>
        </p>
        {report.pageUrl ? (
          <p className="truncate" title={report.pageUrl}>
            {strings.platformBugReports.page} {report.pageUrl}
          </p>
        ) : null}
        {report.userAgent ? (
          <p className="truncate" title={report.userAgent}>
            {strings.platformBugReports.browser} {report.userAgent}
          </p>
        ) : null}
        {report.resolvedAt ? (
          <p>{strings.platformBugReports.resolvedAt(dateTimeFormat.format(new Date(report.resolvedAt)))}</p>
        ) : null}
      </div>

      <div className="flex justify-end">
        <Button variant="outline" size="sm" disabled={saving} onClick={() => onToggle(!resolved)}>
          {resolved ? <RotateCcw className="h-4 w-4 mr-2" /> : <Check className="h-4 w-4 mr-2" />}
          {resolved ? strings.platformBugReports.markPending : strings.platformBugReports.markResolved}
        </Button>
      </div>
    </div>
  )
}
