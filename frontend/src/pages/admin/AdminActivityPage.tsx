import { useState, type ReactNode } from "react"
import { Link } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { Activity, Eye, Package, Receipt, ScrollText, ShoppingBag, TriangleAlert, Users } from "lucide-react"
import {
  observabilityKeys,
  useStoreUsage,
  useStoreUsers,
  type StoreActivity,
  type StoreUsage,
  type StoreUsers,
} from "@/api/observability"
import { productKeys } from "@/api/products"
import { salesKeys } from "@/api/sales"
import { useLiveChannels } from "@/hooks/use-live-channels"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { formatAmount } from "@/lib/utils"
import { Header } from "@/components/header"
import { PageHeader } from "@/components/page-header"
import { LiveStatusBadge, formatRate, timeAgo } from "@/components/live-status"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

const s = strings.storeActivity
// The feed lives only in the page: the last FEED_MAX events since it was opened
const FEED_MAX = 50
const CHANNELS = ["store.activity", "store.usage", "store.users"]

type FeedItem = StoreActivity & { key: string }

/*
Live store activity (ADMINISTRADOR only): connected users, today's usage and what is happening (orders,
receipts and stock). Usage and connected users load over REST and then the WS updates them every 10 s; the feed is
live only. An order or a low stock also refreshes the menu counters.
 */
export default function AdminActivityPage() {
  const { user } = useAuth()
  const enabled = !!user?.token
  const queryClient = useQueryClient()
  const usage = useStoreUsage(enabled)
  const users = useStoreUsers(enabled)
  const [feed, setFeed] = useState<FeedItem[]>([])

  const status = useLiveChannels(
    CHANNELS,
    (channel, data) => {
      if (channel === "store.usage") {
        queryClient.setQueryData(observabilityKeys.storeUsage(), data as StoreUsage)
      } else if (channel === "store.users") {
        queryClient.setQueryData(observabilityKeys.storeUsers(), data as StoreUsers)
      } else if (channel === "store.activity") {
        const item = data as StoreActivity
        setFeed((current) => [{ ...item, key: `${item.type}-${item.at}-${current.length}` }, ...current].slice(0, FEED_MAX))
        if (item.type.startsWith("order.")) {
          queryClient.invalidateQueries({ queryKey: salesKeys.admin() })
        } else {
          queryClient.invalidateQueries({ queryKey: productKeys.all })
        }
        const { title } = describe(item)
        if (item.type !== "stock.movement") toast(title)
      }
    },
    enabled
  )

  const traffic = usage.data?.traffic

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8 space-y-6">
        <PageHeader
          title={s.title}
          highlight={s.highlight}
          subtitle={s.subtitle}
          backTo="/admin"
          action={
            <div className="flex flex-wrap items-center gap-3">
              <LiveStatusBadge status={status} />
              <Button variant="outline" asChild>
                <Link to="/admin/auditoria">
                  <ScrollText className="mr-2 h-4 w-4" />
                  {s.auditLink}
                </Link>
              </Button>
            </div>
          }
        />

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <Tile label={s.connected} value={users.data?.total} icon={Users} hint={s.connectedHint} />
          <Tile label={s.visits} value={usage.data?.catalogVisits} icon={Eye} />
          <Tile
            label={s.requests}
            value={traffic ? s.perSecond(formatRate(traffic.requestsPerSecond)) : undefined}
            icon={Activity}
            hint={traffic ? undefined : s.noTraffic}
          />
          <Tile
            label={s.errors}
            value={traffic ? s.perSecond(formatRate(traffic.errors4xxPerSecond + traffic.errors5xxPerSecond)) : undefined}
            icon={TriangleAlert}
            hint={traffic ? s.errorsHint(formatRate(traffic.errors4xxPerSecond), formatRate(traffic.errors5xxPerSecond)) : s.noTraffic}
          />
        </div>

        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle>{s.feedTitle}</CardTitle>
              <CardDescription>{s.feedDescription}</CardDescription>
            </CardHeader>
            <CardContent>
              {feed.length === 0 ? (
                <p className="text-sm text-muted-foreground">{s.feedEmpty}</p>
              ) : (
                <ul className="divide-y divide-border">
                  {feed.map((item) => (
                    <FeedRow key={item.key} item={item} />
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>{s.usersTitle}</CardTitle>
                <CardDescription>{s.usersDescription}</CardDescription>
              </CardHeader>
              <CardContent>
                {users.isError ? (
                  <p className="text-sm text-destructive">{s.loadError}</p>
                ) : !users.data ? null : users.data.users.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{s.usersEmpty}</p>
                ) : (
                  <ul className="space-y-2 text-sm">
                    {users.data.users.map((connected) => (
                      <li key={connected.userId} className="flex items-center justify-between gap-3">
                        <span className="flex min-w-0 items-center gap-2">
                          <span className="h-2 w-2 shrink-0 rounded-full bg-emerald-500" />
                          <span className="truncate">{connected.name ?? s.unknownUser(connected.userId)}</span>
                        </span>
                        <span className="shrink-0 text-muted-foreground">{timeAgo(connected.lastActivity)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{s.topTitle}</CardTitle>
                <CardDescription>{s.topDescription}</CardDescription>
              </CardHeader>
              <CardContent>
                {usage.isError ? (
                  <p className="text-sm text-destructive">{s.loadError}</p>
                ) : !usage.data ? null : usage.data.topProducts.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{s.topEmpty}</p>
                ) : (
                  <ol className="space-y-2 text-sm">
                    {usage.data.topProducts.map((product) => (
                      <li key={product.slug} className="flex justify-between gap-3">
                        <Link to={`/productos/${product.slug}`} className="truncate hover:underline">
                          {product.slug}
                        </Link>
                        <span className="shrink-0 tabular-nums text-muted-foreground">{s.views(product.views)}</span>
                      </li>
                    ))}
                  </ol>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  )
}

function Tile({ label, value, icon: Icon, hint }: { label: string; value: ReactNode; icon: typeof Users; hint?: string }) {
  return (
    <Card className="gap-2">
      <CardHeader className="flex flex-row items-center justify-between pb-0">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-medium tabular-nums">{value ?? "-"}</div>
        {hint ? <p className="text-xs text-muted-foreground mt-1">{hint}</p> : null}
      </CardContent>
    </Card>
  )
}

// Title, detail, icon and link of each feed event
function describe(item: StoreActivity): { title: string; detail?: string; icon: typeof Users; to?: string } {
  const e = s.events
  switch (item.type) {
    case "order.created":
      return {
        title: item.channel === "LOCAL" ? e.localSale(item.number) : e.orderCreated(item.number),
        detail: [formatAmount(item.total), item.buyerName ? e.buyer(item.buyerName) : null].filter(Boolean).join(" · "),
        icon: ShoppingBag,
        to: `/admin/orders/${item.number}`,
      }
    case "order.receipt-uploaded":
      return {
        title: e.receiptUploaded(item.number),
        detail: [formatAmount(item.total), item.buyerName ? e.buyer(item.buyerName) : null].filter(Boolean).join(" · "),
        icon: Receipt,
        to: `/admin/orders/${item.number}`,
      }
    case "stock.movement":
      return {
        title: e.stockMovement(item.productName),
        detail: [e.movement(s.movementTypes[item.movementType] ?? item.movementType, item.quantity, item.stockAfter), item.reason]
          .filter(Boolean)
          .join(" · "),
        icon: Package,
      }
    case "stock.low":
      return { title: e.stockLow(item.productName), detail: e.low(item.stock, item.minStock), icon: TriangleAlert }
  }
}

function FeedRow({ item }: { item: FeedItem }) {
  const { title, detail, icon: Icon, to } = describe(item)
  const content = (
    <>
      <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-muted">
        <Icon className="h-4 w-4" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium">{title}</span>
        {detail ? <span className="block truncate text-sm text-muted-foreground">{detail}</span> : null}
      </span>
      <span className="shrink-0 text-xs text-muted-foreground">{timeAgo(item.at)}</span>
    </>
  )
  return (
    <li>
      {to ? (
        <Link to={to} className="-mx-2 flex items-start gap-3 rounded-md px-2 py-3 hover:bg-muted/50">
          {content}
        </Link>
      ) : (
        <div className="flex items-start gap-3 py-3">{content}</div>
      )}
    </li>
  )
}
