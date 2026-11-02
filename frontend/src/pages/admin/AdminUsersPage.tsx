
import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { getErrorMessage, notifyError } from "@/lib/api-error"
import { Headline } from "@/components/highlight"
import { Header } from "@/components/header"
import type { User } from "@/lib/types"
import { AdminUserCard } from "@/components/admin-user-card"
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
  const [users, setUsers] = useState<User[]>([])
  const [isLoading, setIsLoading] = useState(true)
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

  useEffect(() => {
    if (!user?.token) {
      return
    }

    let isMounted = true

    const loadUsers = async () => {
      try {
        const response = await axios.get(`${apiBasePublic}/api/v1/users/all`, {
          headers: {
            Authorization: `Bearer ${user.token}`,
          },
        })
        if (isMounted) {
          setUsers(response.data as User[])
        }
      } catch (err) {
        if (isMounted) {
          setUsers([])
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
  }, [apiBasePublic, user?.token])

  if (isAuthLoading || !isAuthenticated || activeRole !== "ADMINISTRADOR") {
    return null
  }

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
    setUsers((prev) =>
      prev.map((item) => (item.id === updatedUser.id ? updatedUser : item))
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
      setUsers((prev) => prev.filter((item) => item.id !== target.id))
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
      const response = await axios.post(
        `${apiBasePublic}/api/v1/users`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${user.token}`,
          },
        }
      )

      const createdUser = response.data as User
      setUsers((prev) => [createdUser, ...prev])
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
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-heading-sm text-foreground">
            <Headline text={strings.adminUsers.title} highlight="Usuarios" />
          </h1>
            <p className="text-muted-foreground mt-2">
              {strings.adminUsers.subtitle}
            </p>
          </div>
          <Button onClick={() => setIsCreateOpen(true)}>{strings.adminUsers.createButton}</Button>
        </div>

        {/* Users List */}
        <Card>
          <CardHeader>
            <CardTitle>{strings.adminUsers.usersTitle} ({users.length})</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <p className="text-sm text-muted-foreground">{strings.adminUsers.loading}</p>
            ) : users.length === 0 ? (
              <p className="text-sm text-muted-foreground">{strings.adminUsers.empty}</p>
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
