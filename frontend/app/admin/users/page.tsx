"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
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
  const router = useRouter()
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
    process.env.NEXT_PUBLIC_API_BASE_PUBLIC ?? "http://localhost:3000"

  useEffect(() => {
    if (isAuthLoading) {
      return
    }
    if (!isAuthenticated) {
      router.push("/login")
      return
    }
    if (activeRole !== "ADMINISTRADOR") {
      router.push("/")
    }
  }, [isAuthenticated, isAuthLoading, activeRole, router])

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
      } catch {
        if (isMounted) {
          setUsers([])
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

    try {
      if (existingUser.name !== updatedUser.name || existingUser.email !== updatedUser.email) {
        await axios.patch(
          `${apiBasePublic}/api/v1/users/${updatedUser.id}`,
          {
            name: updatedUser.name,
            email: updatedUser.email,
          },
          {
            headers: {
              Authorization: `Bearer ${user.token}`,
            },
          }
        )
      }

      await Promise.all(
        rolesToAdd.map((roleName) =>
          axios.post(
            `${apiBasePublic}/api/v1/users/${updatedUser.id}/roles`,
            { roleName },
            {
              headers: {
                Authorization: `Bearer ${user.token}`,
              },
            }
          )
        )
      )

      await Promise.all(
        rolesToRemove.map((roleName) =>
          axios.delete(`${apiBasePublic}/api/v1/users/${updatedUser.id}/roles`, {
            headers: {
              Authorization: `Bearer ${user.token}`,
            },
            data: { roleName },
          })
        )
      )

      updateUserLocally(updatedUser)
      setIsEditOpen(false)
      setSelectedUser(null)
    } catch {
      // TODO: surface error to user
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
    } catch {
      // TODO: surface error to user
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
    } catch {
      setCreateError(strings.adminUsers.createError)
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
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              {strings.adminUsers.title}
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
