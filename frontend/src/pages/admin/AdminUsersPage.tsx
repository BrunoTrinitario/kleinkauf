
import { useEffect, useState } from "react"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
import { PageHeader } from "@/components/page-header"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "@/components/list-toolbar"
import { Header } from "@/components/header"
import type { User, UserRole } from "@/lib/types"
import { useCreateUser, useDeleteUser, useSaveUser, useUsers, type NewUserDto } from "@/api/users"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { AdminUserCard } from "@/components/admin-user-card"
import { AdminPermissionsCard } from "@/components/admin-permissions-card"
import { PaginationControls } from "@/components/pagination-controls"
import { Plus } from "lucide-react"
import { AdminUserEditModal } from "@/components/admin-user-edit-modal"
import { AdminUserCreateModal } from "@/components/admin-user-create-modal"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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

// Requires the USUARIOS area, which only ADMINISTRADOR has (RequireArea in App.tsx)
export default function AdminUsersPage() {
  const { user } = useAuth()
  const [searchInput, setSearchInput] = useState("")
  const searchQuery = useDebouncedValue(searchInput.trim())
  const [roleFilter, setRoleFilter] = useState<UserRole | "">("")
  const [page, setPage] = useState(0)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [isDeleteOpen, setIsDeleteOpen] = useState(false)
  const [selectedDeleteUser, setSelectedDeleteUser] = useState<User | null>(null)

  // A new filter starts from the first page
  useEffect(() => {
    setPage(0)
  }, [searchQuery, roleFilter])

  const usersQuery = useUsers({ q: searchQuery, role: roleFilter, page }, !!user?.token)
  const usersPage = usersQuery.data
  const isLoading = usersQuery.isFetching
  const saveUser = useSaveUser()
  const deleteUser = useDeleteUser()
  const createUser = useCreateUser()
  const isCreating = createUser.isPending

  const users = usersPage?.items ?? []
  const hasFilters = searchQuery !== "" || roleFilter !== ""

  const handleEditUser = (target: User) => {
    setSelectedUser(target)
    setIsEditOpen(true)
  }

  const handleEditOpenChange = (open: boolean) => {
    setIsEditOpen(open)
    if (!open) {
      setSelectedUser(null)
    }
  }

  const handleCreateOpenChange = (open: boolean) => {
    setIsCreateOpen(open)
    if (!open) {
      setCreateError(null)
    }
  }

  // The list is reloaded after saving (successfully or not): it brings the roles with their real permissions
  const handleSaveUser = async (updatedUser: User) => {
    const existingUser = users.find((item) => item.id === updatedUser.id)
    if (!existingUser) {
      return
    }

    try {
      await saveUser.mutateAsync({ previous: existingUser, next: updatedUser })
      setIsEditOpen(false)
      setSelectedUser(null)
    } catch (err) {
      // 409: it was the store's last ADMINISTRADOR and the role was being removed
      notifyError(err, strings.errors.saveUser, { conflict: strings.adminUsers.lastAdmin })
    }
  }

  const handleDeleteUser = async (target: User) => {
    try {
      await deleteUser.mutateAsync(target.id)
      // If it was the last one on the page it goes back to the previous one (the current one reloads on its own)
      if (users.length === 1 && page > 0) {
        setPage(page - 1)
      }
    } catch (err) {
      notifyError(err, strings.errors.deleteUser, { conflict: strings.adminUsers.lastAdmin })
    }
  }

  // The new user may land on another page depending on the order: the current one is reloaded
  const handleCreateUser = async (payload: NewUserDto) => {
    setCreateError(null)
    try {
      await createUser.mutateAsync(payload)
      setIsCreateOpen(false)
    } catch (err) {
      setCreateError(getErrorMessage(err, strings.adminUsers.createError))
    }
  }

  const handleDeleteIntent = (target: User) => {
    setSelectedDeleteUser(target)
    setIsDeleteOpen(true)
  }

  const handleDeleteOpenChange = (open: boolean) => {
    setIsDeleteOpen(open)
    if (!open) {
      setSelectedDeleteUser(null)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-8">
        <PageHeader
          title={strings.adminUsers.title}
          highlight="Usuarios"
          subtitle={strings.adminUsers.subtitle}
          action={
            <Button onClick={() => setIsCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              {strings.adminUsers.createButton}
            </Button>
          }
        />

        <div className="mb-6">
          <ListToolbar>
            <SearchField
              value={searchInput}
              onChange={setSearchInput}
              placeholder={strings.adminUsers.searchPlaceholder}
            />
            <FilterGroup>
              <FilterSelect
                value={roleFilter}
                onChange={(value) => setRoleFilter(value as UserRole | "")}
                label={strings.adminUsers.allRoles}
              >
                <option value="">{strings.adminUsers.allRoles}</option>
                <option value="ADMINISTRADOR">{strings.roles.administrador}</option>
                <option value="EMPLEADO">{strings.roles.empleado}</option>
                <option value="CLIENTE">{strings.roles.cliente}</option>
              </FilterSelect>
            </FilterGroup>
          </ListToolbar>
        </div>

        {/* Users List */}
        <Card>
          <CardHeader>
            <CardTitle>{strings.adminUsers.usersTitle} ({usersPage?.totalItems ?? 0})</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6 px-3 sm:px-6">
            {isLoading && !usersPage ? (
              <p className="text-sm text-muted-foreground">{strings.adminUsers.loading}</p>
            ) : users.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                {hasFilters ? strings.adminUsers.emptySearch : strings.adminUsers.empty}
              </p>
            ) : (
              <div className="space-y-4">
                {users.map((u) => (
                  <AdminUserCard
                    key={u.id}
                    user={u}
                    onEdit={handleEditUser}
                    onDelete={handleDeleteIntent}
                    disableDelete={user?.id === u.id}
                  />
                ))}
              </div>
            )}
            <PaginationControls
              page={page}
              totalPages={usersPage?.totalPages ?? 0}
              disabled={isLoading}
              onPageChange={setPage}
            />
          </CardContent>
        </Card>

        <AdminPermissionsCard enabled={!!user?.token} />
      </main>

      <AdminUserEditModal
        user={selectedUser}
        open={isEditOpen}
        onOpenChange={handleEditOpenChange}
        onSave={handleSaveUser}
        token={user?.token ?? null}
      />

      <AdminUserCreateModal
        open={isCreateOpen}
        onOpenChange={handleCreateOpenChange}
        onCreate={handleCreateUser}
        isCreating={isCreating}
        error={createError}
      />

      <AlertDialog open={isDeleteOpen} onOpenChange={handleDeleteOpenChange}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{strings.adminUsers.deleteTitle}</AlertDialogTitle>
            <AlertDialogDescription>
              {selectedDeleteUser
                ? `${strings.adminUsers.deleteQuestion} ${selectedDeleteUser.name}? ${strings.adminUsers.deleteDescription}`
                : strings.adminUsers.deleteDescription}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{strings.adminUsers.deleteCancel}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (selectedDeleteUser) {
                  handleDeleteUser(selectedDeleteUser)
                }
                handleDeleteOpenChange(false)
              }}
            >
              {strings.adminUsers.deleteConfirm}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
