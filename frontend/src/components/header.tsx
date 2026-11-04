
import { Link } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import type { UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { PRODUCT_PANEL_AREAS } from "@/lib/permissions"
import { useLowStock } from "@/api/products"
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
import { useCart } from "@/lib/cart"
import { CATALOG_PATH } from "@/lib/catalog-url"
import { useCatalogBundles } from "@/api/catalog"
import { User, LogOut, Settings, Users, LayoutDashboard, Store, Briefcase, Shield, Check, Package, Boxes, ShoppingCart, ShoppingBag } from "lucide-react"

const roles: { value: UserRole; label: string; icon: typeof User }[] = [
  { value: "CLIENTE", label: strings.roles.cliente, icon: User },
  { value: "EMPLEADO", label: strings.roles.empleado, icon: Briefcase },
  { value: "ADMINISTRADOR", label: strings.roles.administrador, icon: Shield },
]

export function Header() {
  const { user, activeRole, isAuthenticated, logout, switchRole, can } = useAuth()
  const availableRoles = user?.roles.map(role => role.type) ?? []
  const { units } = useCart()
  // "Combos" de la tienda solo si hay alguno para comprar (la misma consulta que la home, en cache)
  const hasBundles = (useCatalogBundles().data?.length ?? 0) > 0

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

  // Los mismos criterios que las rutas protegidas de App.tsx (areas del rol activo)
  const canManageProducts = PRODUCT_PANEL_AREAS.some((area) => can(area))
  const canManageUsers = can("USUARIOS")
  // Contador de stock bajo junto a "Productos" (la misma consulta que la tarjeta del panel, en cache)
  const lowStock = useLowStock(isAuthenticated && can("STOCK")).data?.length ?? 0

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border bg-card">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2">
          <Store className="h-5 w-5" strokeWidth={1.5} />
          <span className="text-sm font-medium">{strings.site.name}</span>
        </Link>

        {/* Navigation */}
        <nav className="hidden md:flex items-center gap-6">
          <Link to={CATALOG_PATH} className="text-sm text-muted-foreground hover:text-foreground transition-colors">
            {strings.nav.catalog}
          </Link>
          {hasBundles && (
            <Link to="/combos" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              {strings.nav.bundles}
            </Link>
          )}
          {/* Lo del panel va aparte de lo de la tienda (ej. "Combos" de la tienda y "Combos" del panel) */}
          {isAuthenticated && (canManageProducts || canManageUsers) && (
            <span className="flex items-center gap-2 border-l border-border pl-6 text-xs uppercase tracking-wider text-muted-foreground">
              {strings.nav.panel}
            </span>
          )}
          {isAuthenticated && canManageProducts && (
            <Link
              to="/admin/products"
              className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              {strings.nav.products}
              {lowStock > 0 ? (
                <span
                  className="min-w-5 rounded-full bg-destructive px-1.5 text-center text-[11px] font-medium leading-5 text-white"
                  aria-label={strings.adminLowStock.navBadge(lowStock)}
                  title={strings.adminLowStock.navBadge(lowStock)}
                >
                  {lowStock}
                </span>
              ) : null}
            </Link>
          )}
          {isAuthenticated && canManageProducts && (
            <Link to="/admin/bundles" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              {strings.nav.bundles}
            </Link>
          )}
          {isAuthenticated && canManageUsers && (
            <Link to="/admin/users" className="text-sm text-muted-foreground hover:text-foreground transition-colors">
              {strings.nav.users}
            </Link>
          )}
        </nav>

        {/* Actions */}
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" className="relative" asChild>
            <Link to="/carrito" aria-label={strings.cart.open(units)} title={strings.cart.open(units)}>
              <ShoppingCart className="h-5 w-5" />
              {units > 0 ? (
                <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-primary px-1 text-center text-[11px] font-medium leading-5 text-primary-foreground">
                  {units}
                </span>
              ) : null}
            </Link>
          </Button>
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
                          {lowStock > 0 ? (
                            <span className="ml-auto rounded-full bg-destructive px-1.5 text-[11px] font-medium leading-5 text-white">
                              {lowStock}
                            </span>
                          ) : null}
                        </Link>
                      </DropdownMenuItem>
                      <DropdownMenuItem asChild>
                        <Link to="/admin/bundles">
                          <Boxes className="mr-2 h-4 w-4" />
                          {strings.auth.manageBundles}
                        </Link>
                      </DropdownMenuItem>
                    </>
                  )}

                  {canManageUsers && (
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
                    <Link to="/mis-pedidos">
                      <ShoppingBag className="mr-2 h-4 w-4" />
                      {strings.myOrders.nav}
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link to="/user/settings">
                      <Settings className="mr-2 h-4 w-4" />
                      {strings.auth.settings}
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => logout()} className="text-destructive focus:text-destructive">
                    <LogOut className="mr-2 h-4 w-4" />
                    {strings.auth.signOut}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </>
          ) : (
            <div className="flex items-center gap-2">
              <Button variant="outline" asChild>
                <Link to="/login">{strings.auth.signIn}</Link>
              </Button>
              {/* En el celular no entra junto al carrito y el login; el login tiene el link para crear la cuenta */}
              <Button className="hidden sm:inline-flex" asChild>
                <Link to="/register">{strings.auth.registerNow}</Link>
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
