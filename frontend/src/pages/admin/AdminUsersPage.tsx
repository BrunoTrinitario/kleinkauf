
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
import { PageHeader } from "@/components/page-header"
import { FilterGroup, FilterSelect, ListToolbar, SearchField } from "@/components/list-toolbar"
import { Header } from "@/components/header"
import type { Page, User, UserRole } from "@/lib/types"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { AdminUserCard } from "@/components/admin-user-card"
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
import axios from "axios"

export default function AdminUsersPage() {
  const navigate = useNavigate()
  const { user, activeRole, isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [usersPage, setUsersPage] = useState<Page<User> | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [searchInput, setSearchInput] = useState("")
  const searchQuery = useDebouncedValue(searchInput.trim())
  const [roleFilter, setRoleFilter] = useState<UserRole | "">("")
  const [page, setPage] = useState(0)
  // Se incrementa para volver a pedir la pagina actual (despues de crear o borrar)
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [isEditOpen, setIsEditOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [isDeleteOpen, setIsDeleteOpen] = useState(false)
  const [selectedDeleteUser, setSelectedDeleteUser] = useState<User | null>(null)
  const apiBasePublic =
    import.meta.env.VITE_API_BASE_PUBLIC ?? "http://localhost:3000"

  useEffect(() => {
    if (isAuthLoading) {
      return
    }
    if (!isAuthenticated) {
      navigate("/login")
      return
    }
    if (activeRole !== "ADMINISTRADOR") {
      navigate("/")
    }
  }, [isAuthenticated, isAuthLoading, activeRole, navigate])

  // Un filtro nuevo arranca desde la primera pagina
  useEffect(() => {
    setPage(0)
  }, [searchQuery, roleFilter])

  useEffect(() => {
    if (!user?.token) {
      return
    }

    let isMounted = true

    const loadUsers = async () => {
      setIsLoading(true)
      try {
        const response = await axios.get<Page<User>>(`${apiBasePublic}/api/v1/users`, {
          headers: {
            Authorization: `Bearer ${user.token}`,
          },
          params: {
            q: searchQuery || undefined,
            role: roleFilter || undefined,
            page,
          },
        })
        if (isMounted) {
          setUsersPage(response.data)
        }
      } catch (err) {
        if (isMounted) {
          setUsersPage(null)
          notifyError(err, strings.errors.loadUsers)
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    loadUsers()

    return () => {
      isMounted = false
    }
  }, [apiBasePublic, user?.token, searchQuery, roleFilter, page, reloadKey])

  if (isAuthLoading || !isAuthenticated || activeRole !== "ADMINISTRADOR") {
    return null
  }

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

  const updateUserLocally = (updatedUser: User) => {
    setUsersPage((prev) =>
      prev
        ? { ...prev, items: prev.items.map((item) => (item.id === updatedUser.id ? updatedUser : item)) }
        : prev
    )
  }

  const handleSaveUser = async (updatedUser: User) => {
    if (!user?.token) {
      return
    }

    const existingUser = users.find((item) => item.id === updatedUser.id)
    if (!existingUser) {
      return
    }

    const previousRoles = new Set(existingUser.roles.map((role) => role.type))
    const nextRoles = new Set(updatedUser.roles.map((role) => role.type))
    const rolesToAdd = Array.from(nextRoles).filter((role) => !previousRoles.has(role))
    const rolesToRemove = Array.from(previousRoles).filter((role) => !nextRoles.has(role))

    const headers = { Authorization: `Bearer ${user.token}` }

    try {
      // El PATCH solo cambia nombre y contrasena: el email no se puede editar
      if (existingUser.name !== updatedUser.name) {
        await axios.patch(
          `${apiBasePublic}/api/v1/users/${updatedUser.id}`,
          { name: updatedUser.name },
          { headers }
        )
      }

      // roleName va como query param, no en el body
      await Promise.all(
        rolesToAdd.map((roleName) =>
          axios.post(`${apiBasePublic}/api/v1/users/${updatedUser.id}/roles`, null, {
            headers,
            params: { roleName },
          })
        )
      )

      await Promise.all(
        rolesToRemove.map((roleName) =>
          axios.delete(`${apiBasePublic}/api/v1/users/${updatedUser.id}/roles`, {
            headers,
            params: { roleName },
          })
        )
      )

      // Se usa el usuario que devuelve el backend: trae los roles con sus permisos reales
      const response = await axios.get(`${apiBasePublic}/api/v1/users/${updatedUser.id}`, { headers })
      updateUserLocally(response.data as User)
      setIsEditOpen(false)
      setSelectedUser(null)
    } catch (err) {
      notifyError(err, strings.errors.saveUser)
      // Si algo fallo a mitad de camino, se recarga el usuario para no mostrar datos que no se guardaron
      axios
        .get(`${apiBasePublic}/api/v1/users/${updatedUser.id}`, { headers })
        .then((response) => updateUserLocally(response.data as User))
        .catch(() => {})
    }
  }

  const handleDeleteUser = async (target: User) => {
    if (!user?.token) {
      return
    }

    try {
      await axios.delete(`${apiBasePublic}/api/v1/users/${target.id}`, {
        headers: {
          Authorization: `Bearer ${user.token}`,
        },
      })
      // Si era el ultimo de la pagina se vuelve a la anterior; si no, se recarga la actual
      if (users.length === 1 && page > 0) {
        setPage(page - 1)
      } else {
        setReloadKey((prev) => prev + 1)
      }
    } catch (err) {
      notifyError(err, strings.errors.deleteUser)
    }
  }

  const handleCreateUser = async (payload: {
    name: string
    email: string
    password: string
    roles: string[]
  }) => {
    if (!user?.token) {
      return
    }

    setIsCreating(true)
    setCreateError(null)

    try {
      await axios.post(
        `${apiBasePublic}/api/v1/users`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${user.token}`,
          },
        }
      )

      // El usuario nuevo puede caer en otra pagina segun el orden: se recarga la actual
      setReloadKey((prev) => prev + 1)
      setIsCreateOpen(false)
    } catch (err) {
      setCreateError(getErrorMessage(err, strings.adminUsers.createError))
    } finally {
      setIsCreating(false)
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
          backTo="/admin"
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
