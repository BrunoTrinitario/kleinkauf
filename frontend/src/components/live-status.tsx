import type { LiveStatus } from "@/hooks/use-live-channels"
import { strings } from "@/lib/strings"
import { cn } from "@/lib/utils"

// Dot with the real-time status: it pulses while live
export function LiveStatusBadge({ status }: { status: LiveStatus }) {
  const dot = {
    live: "bg-emerald-500",
    connecting: "bg-muted-foreground",
    reconnecting: "bg-amber-500",
    denied: "bg-destructive",
  }[status]
  return (
    <span
      className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1 text-xs text-muted-foreground"
      title={status === "denied" ? strings.live.deniedHelp : undefined}
      role="status"
    >
      <span className="relative flex h-2 w-2">
        {status === "live" ? (
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-500 opacity-60" />
        ) : null}
        <span className={cn("relative inline-flex h-2 w-2 rounded-full", dot)} />
      </span>
      {strings.live[status]}
    </span>
  )
}

const relative = new Intl.RelativeTimeFormat("es-AR", { numeric: "auto" })

// "hace 3 minutos" for recent dates (connected users, live feed)
export function timeAgo(value: string, now = Date.now()): string {
  const seconds = Math.round((new Date(value).getTime() - now) / 1000)
  if (seconds > -30) return strings.storeActivity.now
  if (seconds > -3600) return relative.format(Math.round(seconds / 60), "minute")
  if (seconds > -86_400) return relative.format(Math.round(seconds / 3600), "hour")
  return relative.format(Math.round(seconds / 86_400), "day")
}

// Per-second rates: few decimals, and "-" with no data (before the second reading)
export function formatRate(value: number | null | undefined): string {
  if (value === null || value === undefined) return "-"
  if (value === 0) return "0"
  return value < 0.1 ? value.toFixed(2) : value < 10 ? value.toFixed(1) : Math.round(value).toString()
}
