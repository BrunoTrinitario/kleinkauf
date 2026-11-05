import { toast } from "sonner"
import { strings } from "@/lib/strings"
import { notifyError } from "@/lib/api-error"
import { PERMISSIONS, type PermissionArea } from "@/lib/permissions"
import type { UserRole } from "@/lib/types"
import { useRolePermissions, useSetRolePermissions } from "@/api/users"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const ROLES: { value: UserRole; label: string }[] = [
  { value: "ADMINISTRADOR", label: strings.roles.administrador },
  { value: "EMPLEADO", label: strings.roles.empleado },
  { value: "CLIENTE", label: strings.roles.cliente },
]

const AREAS = Object.keys(PERMISSIONS) as PermissionArea[]

// ADMINISTRADOR has everything and is not configurable; USUARIOS is not delegable (the backend responds 400)
const isEditable = (role: UserRole, area: PermissionArea) => role !== "ADMINISTRADOR" && area !== "USUARIOS"

// Permission matrix per role (ADMINISTRADOR only, like the users page). Each checkbox is saved right away
export function AdminPermissionsCard({ enabled }: { enabled: boolean }) {
  const query = useRolePermissions(enabled)
  const save = useSetRolePermissions()
  const maskOf = (role: UserRole) => query.data?.find((item) => item.role === role)?.permissions ?? 0

  const toggle = async (role: UserRole, area: PermissionArea) => {
    const permissions = maskOf(role) ^ PERMISSIONS[area]
    const label = ROLES.find((item) => item.value === role)?.label ?? role
    try {
      await save.mutateAsync({ role, permissions })
      toast.success(strings.adminPermissions.saved(label), { id: "permissions-saved" })
    } catch (err) {
      notifyError(err, strings.adminPermissions.saveError)
    }
  }

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>{strings.adminPermissions.title}</CardTitle>
        <CardDescription>
          {strings.adminPermissions.description} {strings.adminPermissions.delay}
        </CardDescription>
      </CardHeader>
      <CardContent className="px-3 sm:px-6">
        {query.isPending ? (
          <p className="text-sm text-muted-foreground">{strings.adminPermissions.loading}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{strings.adminPermissions.area}</TableHead>
                {ROLES.map((role) => (
                  <TableHead key={role.value} className="text-center">
                    {role.label}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {AREAS.map((area) => {
                const info = strings.adminPermissions.areas[area]
                return (
                  <TableRow key={area}>
                    <TableCell className="whitespace-normal">
                      <p className="font-medium">{info.label}</p>
                      <p className="text-xs text-muted-foreground">{info.hint}</p>
                    </TableCell>
                    {ROLES.map((role) => {
                      const editable = isEditable(role.value, area)
                      const checked = (maskOf(role.value) & PERMISSIONS[area]) !== 0
                      return (
                        <TableCell key={role.value} className="text-center">
                          <input
                            type="checkbox"
                            className="h-4 w-4 align-middle disabled:opacity-50"
                            checked={checked}
                            disabled={!editable || save.isPending}
                            aria-label={strings.adminPermissions.toggleAria(info.label, role.label)}
                            onChange={() => toggle(role.value, area)}
                          />
                        </TableCell>
                      )
                    })}
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
