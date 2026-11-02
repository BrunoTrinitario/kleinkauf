
import { Link } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import type { UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
  DropdownMenuPortal,
} from "@/components/ui/dropdown-menu"
import { User, LogOut, Settings, Users, LayoutDashboard, Store, Briefcase, Shield, Check, Package } from "lucide-react"

const roles: { value: UserRole; label: string; icon: typeof User }[] = [
  { value: "CLIENTE", label: strings.roles.cliente, icon: User },
  { value: "EMPLEADO", label: strings.roles.empleado, icon: Briefcase },
  { value: "ADMINISTRADOR", label: strings.roles.administrador, icon: Shield },
]

export function Header() {
  const { user, activeRole, isAuthenticated, logout, switchRole } = useAuth()
  const availableRoles = user?.roles.map(role => role.type) ?? []

  const getRoleBadgeColor = () => {
    switch (activeRole) {
      case "ADMINISTRADOR":
        return "bg-foreground text-background"
      case "EMPLEADO":
        return "bg-muted text-foreground"
      default:
        return "bg-muted text-muted-foreground"
    }
  }

  const canManageProducts = activeRole === "ADMINISTRADOR" || activeRole === "EMPLEADO"

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2">
          <Store className="h-6 w-6" />
          <span className="font-semibold text-lg">{strings.site.name}</span>
        </Link>

        {/* Navigation */}
        <nav className="hidden md:flex items-center gap-6">
          <Link to="/" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
            {strings.nav.catalog}
          </Link>
          {isAuthenticated && canManageProducts && (
            <Link to="/admin/products" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              {strings.nav.products}
            </Link>
          )}
          {isAuthenticated && activeRole === "ADMINISTRADOR" && (
            <Link to="/admin/users" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              {strings.nav.users}
            </Link>
          )}
        </nav>

        {/* Actions */}
        <div className="flex items-center gap-3">
          {isAuthenticated ? (
            <>
              {/* User Menu */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" className="flex items-center gap-2 px-2">
                    <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center">
                      <User className="h-4 w-4" />
                    </div>
                    <div className="hidden sm:flex flex-col items-start">
                      <span className="text-sm font-medium leading-none">{user?.name}</span>
                      <span className={`text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded mt-1 ${getRoleBadgeColor()}`}>
                        {activeRole}
                      </span>
                    </div>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-56">
                  <DropdownMenuLabel>
                    <div className="flex flex-col">
                      <span>{user?.name}</span>
                      <span className="text-xs font-normal text-muted-foreground">{user?.email}</span>
                    </div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  
                  {/* Role Switcher */}
                  <DropdownMenuSub>
                    <DropdownMenuSubTrigger>
                      <Shield className="mr-2 h-4 w-4" />
                      {strings.auth.switchRole}
                    </DropdownMenuSubTrigger>
                    <DropdownMenuPortal>
                      <DropdownMenuSubContent>
                        {roles.filter(role => availableRoles.includes(role.value)).map((role) => (
                          <DropdownMenuItem
                            key={role.value}
                            onClick={() => switchRole(role.value)}
                            className="flex items-center justify-between"
                          >
                            <div className="flex items-center">
                              <role.icon className="mr-2 h-4 w-4" />
                              <span>{role.label}</span>
                            </div>
                            {activeRole === role.value && (
                              <Check className="h-4 w-4 text-primary" />
                            )}
                          </DropdownMenuItem>
                        ))}
                      </DropdownMenuSubContent>
                    </DropdownMenuPortal>
                  </DropdownMenuSub>
                  
                  <DropdownMenuSeparator />
                  
                  {activeRole === "ADMINISTRADOR" && (
                    <>
                      <DropdownMenuItem asChild>
                        <Link to="/admin">
                          <LayoutDashboard className="mr-2 h-4 w-4" />
                          {strings.auth.dashboard}
                        </Link>
                      </DropdownMenuItem>
                    </>
                  )}

                  {canManageProducts && (
                    <>
                      <DropdownMenuItem asChild>
                        <Link to="/admin/products">
                          <Package className="mr-2 h-4 w-4" />
                          {strings.auth.manageProducts}
                        </Link>
                      </DropdownMenuItem>
                    </>
                  )}

                  {activeRole === "ADMINISTRADOR" && (
                    <>
                      <DropdownMenuItem asChild>
                        <Link to="/admin/users">
                          <Users className="mr-2 h-4 w-4" />
                          {strings.auth.manageUsers}
                        </Link>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                    </>
                  )}
                  
                  <DropdownMenuItem asChild>
                    <Link to="/user/settings">
                      <Settings className="mr-2 h-4 w-4" />
                      {strings.auth.settings}
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={logout} className="text-destructive focus:text-destructive">
                    <LogOut className="mr-2 h-4 w-4" />
                    {strings.auth.signOut}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <div className="flex items-center gap-2">
              <Button variant="ghost" asChild>
                <Link to="/login">{strings.auth.signIn}</Link>
              </Button>
              <Button asChild>
                <Link to="/register">{strings.auth.registerNow}</Link>
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
