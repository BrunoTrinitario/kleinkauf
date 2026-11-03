import { useEffect, useMemo, useState } from "react"
import { api, isSessionExpired, PLATFORM_PATH } from "@/lib/api"
import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { getErrorMessage } from "@/lib/api-error"
import type { NewTenantDto, PlatformTenant } from "@/lib/types"
import { usePlatformSession } from "@/hooks/use-platform-session"
import { PageHeader } from "@/components/page-header"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "@/components/list-toolbar"
import { PlatformHeader } from "@/components/platform-header"
import { PlatformTenantCard } from "@/components/platform-tenant-card"
import { PlatformTenantCreateModal } from "@/components/platform-tenant-create-modal"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Plus } from "lucide-react"

type StatusFilter = "" | "enabled" | "disabled"

const TENANTS_URL = `${PLATFORM_PATH}/admin/tenants`

export default function PlatformTenantsPage() {
  const { ready, handleError } = usePlatformSession()
  const [tenants, setTenants] = useState<PlatformTenant[] | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [searchInput, setSearchInput] = useState("")
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("")
  const [savingId, setSavingId] = useState<number | null>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  // El objetivo se mantiene al cerrar el dialogo para que el texto no cambie durante la animacion
  const [deleteTarget, setDeleteTarget] = useState<PlatformTenant | null>(null)
  const [isDeleteOpen, setIsDeleteOpen] = useState(false)

  useEffect(() => {
    if (!ready) {
      return
    }

    let isMounted = true

    const loadTenants = async () => {
      setIsLoading(true)
      try {
        const response = await api.get<PlatformTenant[]>(TENANTS_URL)
        if (isMounted) {
          setTenants(response.data)
        }
      } catch (err) {
        if (isMounted) {
          setTenants(null)
          handleError(err, strings.errors.loadTenants)
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadTenants()

    return () => {
      isMounted = false
    }
  }, [ready, handleError])

  // El backend devuelve todas las tiendas no borradas sin paginar: se filtra aca
  const visibleTenants = useMemo(() => {
    const query = searchInput.trim().toLowerCase()
    return (tenants ?? []).filter((tenant) => {
      if (query && !tenant.identifier.includes(query)) {
        return false
      }
      if (statusFilter === "enabled") return tenant.enabled
      if (statusFilter === "disabled") return !tenant.enabled
      return true
    })
  }, [tenants, searchInput, statusFilter])

  if (!ready) {
    return null
  }

  const hasFilters = searchInput.trim() !== "" || statusFilter !== ""

  const replaceTenant = (updated: PlatformTenant) => {
    setTenants((prev) => prev?.map((tenant) => (tenant.id === updated.id ? updated : tenant)) ?? prev)
  }

  const handleToggleEnabled = async (target: PlatformTenant, enabled: boolean) => {
    setSavingId(target.id)
    try {
      const response = await api.put<PlatformTenant>(`${TENANTS_URL}/${target.id}/enabled`, { enabled })
      replaceTenant(response.data)
      toast.success(
        enabled
          ? strings.platformTenants.enabledToast(target.identifier)
          : strings.platformTenants.disabledToast(target.identifier)
      )
    } catch (err) {
      handleError(err, strings.errors.saveTenant)
    } finally {
      setSavingId(null)
    }
  }

  const handleDelete = async (target: PlatformTenant) => {
    try {
      await api.delete(`${TENANTS_URL}/${target.id}`)
      setTenants((prev) => prev?.filter((tenant) => tenant.id !== target.id) ?? prev)
    } catch (err) {
      handleError(err, strings.errors.deleteTenant)
    }
  }

  const handleCreate = async (payload: NewTenantDto) => {
    setIsCreating(true)
    setCreateError(null)
    try {
      const response = await api.post<PlatformTenant>(TENANTS_URL, payload)
      // La lista va de la mas nueva a la mas vieja, igual que la devuelve el backend
      setTenants((prev) => [response.data, ...(prev ?? [])])
      setIsCreateOpen(false)
    } catch (err) {
      if (isSessionExpired(err)) return
      setCreateError(getErrorMessage(err, strings.platformTenantCreate.createError))
    } finally {
      setIsCreating(false)
    }
  }

  const handleCreateOpenChange = (open: boolean) => {
    setIsCreateOpen(open)
    if (!open) {
      setCreateError(null)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <PlatformHeader />
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.platformTenants.title}
          highlight={strings.platformTenants.highlight}
          subtitle={strings.platformTenants.subtitle}
          action={
            <Button onClick={() => setIsCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              {strings.platformTenants.createButton}
            </Button>
          }
        />

        <div className="mb-6">
          <ListToolbar>
            <SearchField
              value={searchInput}
              onChange={setSearchInput}
              placeholder={strings.platformTenants.searchPlaceholder}
            />
            <FilterGroup>
              <FilterSelect
                value={statusFilter}
                onChange={(value) => setStatusFilter(value as StatusFilter)}
                label={strings.platformTenants.allStatuses}
              >
                <option value="">{strings.platformTenants.allStatuses}</option>
                <option value="enabled">{strings.platformTenants.enabled}</option>
                <option value="disabled">{strings.platformTenants.disabled}</option>
              </FilterSelect>
            </FilterGroup>
          </ListToolbar>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>
              {strings.platformTenants.listTitle} ({visibleTenants.length})
            </CardTitle>
            <CardDescription>{strings.platformTenants.propagationNotice}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 px-3 sm:px-6">
            {isLoading && !tenants ? (
              <p className="text-sm text-muted-foreground">{strings.platformTenants.loading}</p>
            ) : visibleTenants.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                {hasFilters ? strings.platformTenants.emptySearch : strings.platformTenants.empty}
              </p>
            ) : (
              visibleTenants.map((tenant) => (
                <PlatformTenantCard
                  key={tenant.id}
                  tenant={tenant}
                  isSaving={savingId === tenant.id}
                  onToggleEnabled={handleToggleEnabled}
                  onDelete={(target) => {
                    setDeleteTarget(target)
                    setIsDeleteOpen(true)
                  }}
                />
              ))
            )}
          </CardContent>
        </Card>
      </main>

      <PlatformTenantCreateModal
        open={isCreateOpen}
        onOpenChange={handleCreateOpenChange}
        onCreate={handleCreate}
        isCreating={isCreating}
        error={createError}
      />

      <AlertDialog open={isDeleteOpen} onOpenChange={setIsDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{strings.platformTenants.deleteTitle}</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget
                ? `${strings.platformTenants.deleteQuestion} ${deleteTarget.identifier}? ${strings.platformTenants.deleteDescription}`
                : strings.platformTenants.deleteDescription}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{strings.platformTenants.deleteCancel}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (deleteTarget) {
                  handleDelete(deleteTarget)
                }
                setIsDeleteOpen(false)
              }}
            >
              {strings.platformTenants.deleteConfirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
