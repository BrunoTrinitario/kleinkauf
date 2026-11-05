import { useState } from "react"
import { isSessionExpired } from "@/lib/api"
import { useCreateSuperadmin, useSetSuperadminActive, useSuperadmins } from "@/api/platform"
import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import type { Superadmin } from "@/lib/types"
import { usePlatformSession } from "@/hooks/use-platform-session"
import { PageHeader } from "@/components/page-header"
import { PlatformHeader } from "@/components/platform-header"
import { PlatformSuperadminCreateModal } from "@/components/platform-superadmin-create-modal"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Switch } from "@/components/ui/switch"
import { Mail, Plus, User as UserIcon } from "lucide-react"

const dateFormat = new Intl.DateTimeFormat("es-AR", { dateStyle: "short" })
const dateTimeFormat = new Intl.DateTimeFormat("es-AR", { dateStyle: "short", timeStyle: "short" })

export default function PlatformSuperadminsPage() {
  const { user, ready, handleError } = usePlatformSession()
  const [savingId, setSavingId] = useState<number | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const superadminsQuery = useSuperadmins(ready)
  // If the list fails it shows empty (the notice comes from the QueryClient)
  const superadmins = superadminsQuery.data ?? (superadminsQuery.isError ? [] : null)
  const setActive = useSetSuperadminActive()
  const createSuperadmin = useCreateSuperadmin()
  const isCreating = createSuperadmin.isPending

  if (!ready) {
    return null
  }

  const handleToggle = async (target: Superadmin, active: boolean) => {
    setSavingId(target.id)
    try {
      await setActive.mutateAsync({ id: target.id, active })
      toast.success(
        active
          ? strings.platformSuperadmins.activatedToast(target.name)
          : strings.platformSuperadmins.deactivatedToast(target.name)
      )
    } catch (err) {
      // 409: it is the last active one. 400 (deactivating yourself) never happens: the switch is disabled
      handleError(err, strings.errors.saveSuperadmin, {
        conflict: strings.platformSuperadmins.lastActive,
        badRequest: strings.platformSuperadmins.selfHint,
      })
    } finally {
      setSavingId(null)
    }
  }

  const handleCreate = async (payload: { name: string; email: string; password: string }) => {
    setCreateError(null)
    try {
      await createSuperadmin.mutateAsync(payload)
      setIsCreateOpen(false)
    } catch (err) {
      if (isSessionExpired(err)) return
      setCreateError(getErrorMessage(err, strings.platformSuperadminCreate.createError))
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <PlatformHeader />
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.platformSuperadmins.title}
          highlight={strings.platformSuperadmins.highlight}
          subtitle={strings.platformSuperadmins.subtitle}
          action={
            <Button onClick={() => setIsCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              {strings.platformSuperadmins.createButton}
            </Button>
          }
        />

        <Card>
          <CardHeader>
            <CardTitle>
              {strings.platformSuperadmins.listTitle} ({superadmins?.length ?? 0})
            </CardTitle>
            <CardDescription>{strings.platformSuperadmins.notice}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 px-3 sm:px-6">
            {superadmins === null ? (
              <p className="text-sm text-muted-foreground">{strings.platformSuperadmins.loading}</p>
            ) : (
              superadmins.map((superadmin) => {
                const isSelf = superadmin.id === user?.id
                const badge = superadmin.active ? (
                  <Badge className="bg-sky-wash text-cyan-edge">{strings.platformSuperadmins.active}</Badge>
                ) : (
                  <Badge className="bg-muted text-muted-foreground">{strings.platformSuperadmins.inactive}</Badge>
                )
                return (
                  <div
                    key={superadmin.id}
                    className={`flex items-center justify-between gap-3 p-4 rounded-lg border border-border ${superadmin.active ? "" : "opacity-70"}`}
                  >
                    <div className="flex items-center gap-3 sm:gap-4 min-w-0 flex-1">
                      <div className="h-10 w-10 shrink-0 rounded-full bg-muted flex items-center justify-center">
                        <UserIcon className="h-5 w-5 text-muted-foreground" />
                      </div>
                      <div className="min-w-0">
                        <p className="font-medium text-foreground truncate">
                          {superadmin.name}
                          {isSelf ? (
                            <span className="text-muted-foreground font-normal"> ({strings.platformSuperadmins.you})</span>
                          ) : null}
                        </p>
                        <p className="text-sm text-muted-foreground flex items-center gap-1 min-w-0">
                          <Mail className="h-3 w-3 shrink-0" />
                          <span className="truncate">{superadmin.email}</span>
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">
                          {strings.platformSuperadmins.createdAt} {dateFormat.format(new Date(superadmin.createdAt))}
                          {" · "}
                          {strings.platformSuperadmins.lastLogin}{" "}
                          {superadmin.lastLogin
                            ? dateTimeFormat.format(new Date(superadmin.lastLogin))
                            : strings.platformSuperadmins.never}
                        </p>
                        <div className="flex flex-wrap gap-1.5 mt-2 sm:hidden">{badge}</div>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 shrink-0">
                      <div className="hidden sm:flex">{badge}</div>
                      <Switch
                        checked={superadmin.active}
                        disabled={isSelf || savingId === superadmin.id}
                        title={isSelf ? strings.platformSuperadmins.selfHint : undefined}
                        onCheckedChange={(checked) => handleToggle(superadmin, checked)}
                        aria-label={strings.platformSuperadmins.toggleAria(superadmin.name)}
                      />
                    </div>
                  </div>
                )
              })
            )}
          </CardContent>
        </Card>
      </main>

      <PlatformSuperadminCreateModal
        open={isCreateOpen}
        onOpenChange={(open) => {
          setIsCreateOpen(open)
          if (!open) setCreateError(null)
        }}
        onCreate={handleCreate}
        isCreating={isCreating}
        error={createError}
      />
    </div>
  )
}
