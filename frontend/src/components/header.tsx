import { useState } from "react"
import { Link } from "react-router-dom"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { useAuth } from "@/lib/auth-context"
import type { UserRole } from "@/lib/types"
import { strings } from "@/lib/strings"
import { PRODUCT_PANEL_AREAS } from "@/lib/permissions"
import { useLowStock } from "@/api/products"
import { usePendingReviewCount } from "@/api/sales"
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
import { useStoreConfig } from "@/api/store"
import { useMe } from "@/api/account"
import { UserAvatar } from "@/components/user-avatar"
import { productImageUrl } from "@/lib/api"
import { User, LogOut, Settings, Users, LayoutDashboard, Store, Briefcase, Shield, Check, Package, Boxes, ShoppingCart, ShoppingBag, Receipt, Menu } from "lucide-react"

const roles: { value: UserRole; label: string; icon: typeof User }[] = [
  { value: "CLIENTE", label: strings.roles.cliente, icon: User },
  { value: "EMPLEADO", label: strings.roles.empleado, icon: Briefcase },
  { value: "ADMINISTRADOR", label: strings.roles.administrador, icon: Shield },
]

interface NavLinksProps {
  hasBundles: boolean
  showPanel: boolean
  canManageSales: boolean
  canManageProducts: boolean
  canManageUsers: boolean
  pendingReview: number
  lowStock: number
  // In the side menu: stacked, and each link closes the menu
  vertical?: boolean
  onNavigate?: () => void
}

function CountBadge({ count, label }: { count: number; label: string }) {
  if (count <= 0) return null
  return (
    <span
      className="min-w-5 rounded-full bg-destructive px-1.5 text-center text-[11px] font-medium leading-5 text-white"
      aria-label={label}
      title={label}
    >
      {count}
    </span>
  )
}

// Store links and, separately, panel links (e.g. the store's "Combos" and the panel's "Combos")
function NavLinks({
  hasBundles,
  showPanel,
  canManageSales,
  canManageProducts,
  canManageUsers,
  pendingReview,
  lowStock,
  vertical = false,
  onNavigate,
}: NavLinksProps) {
  const link = vertical
    ? "flex items-center gap-1.5 rounded-md px-3 py-2.5 text-base text-foreground hover:bg-muted transition-colors"
    : "flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
  const panelLabel = vertical
    ? "mt-4 border-t border-border px-3 pt-4 pb-1 text-xs uppercase tracking-wider text-muted-foreground"
    : "flex items-center gap-2 border-l border-border pl-6 text-xs uppercase tracking-wider text-muted-foreground"

  return (
    <>
      <Link to="/" className={link} onClick={onNavigate}>
        {strings.nav.home}
      </Link>
      <Link to={CATALOG_PATH} className={link} onClick={onNavigate}>
        {strings.nav.catalog}
      </Link>
      {hasBundles && (
        <Link to="/combos" className={link} onClick={onNavigate}>
          {strings.nav.bundles}
        </Link>
      )}
      {showPanel && <span className={panelLabel}>{strings.nav.panel}</span>}
      {canManageSales && (
        <Link to="/admin/orders" className={link} onClick={onNavigate}>
          {strings.adminOrders.nav}
          <CountBadge count={pendingReview} label={strings.adminOrders.navBadge(pendingReview)} />
        </Link>
      )}
      {canManageProducts && (
        <Link to="/admin/products" className={link} onClick={onNavigate}>
          {strings.nav.products}
          <CountBadge count={lowStock} label={strings.adminLowStock.navBadge(lowStock)} />
        </Link>
      )}
      {canManageProducts && (
        <Link to="/admin/bundles" className={link} onClick={onNavigate}>
          {strings.nav.bundles}
        </Link>
      )}
      {canManageUsers && (
        <Link to="/admin/users" className={link} onClick={onNavigate}>
          {strings.nav.users}
        </Link>
      )}
    </>
  )
}

// Store name and, if enabled, its icon. Shows nothing while the settings load (so one name is not replaced by
// another)
function StoreBrand() {
  const config = useStoreConfig().data
  if (!config) return <span className="h-5 w-24" aria-hidden />
  return (
    <>
      {config.icon && config.showIconInHeader ? (
        <img src={productImageUrl(config.icon)} alt="" className="h-7 w-7 shrink-0 rounded-md object-contain" />
      ) : null}
      <span className="truncate text-sm font-medium">{config.name}</span>
    </>
  )
}

// Menu button (below md only) that opens the navigation in a panel from the left
function MobileNav(props: NavLinksProps) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="md:hidden -ml-2" aria-label={strings.nav.openMenu}>
          <Menu className="h-5 w-5" />
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="w-72 gap-0 p-4">
        <SheetHeader className="p-0 pb-4">
          <SheetTitle className="flex items-center gap-2 text-left">
            <StoreBrand />
          </SheetTitle>
          <SheetDescription className="sr-only">{strings.nav.menuDescription}</SheetDescription>
        </SheetHeader>
        <nav className="flex flex-col">
          <NavLinks {...props} vertical onNavigate={() => setOpen(false)} />
        </nav>
      </SheetContent>
    </Sheet>
  )
}

export function Header() {
  const { user, activeRole, isAuthenticated, logout, switchRole, can } = useAuth()
  const availableRoles = user?.roles.map(role => role.type) ?? []
  const { units } = useCart()
  // Profile photo (same cached query as the settings page)
  const me = useMe(isAuthenticated).data
  // The store's "Combos" only if there is any to buy (the same query as the home page, cached)
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

  // The same criteria as the protected routes in App.tsx (areas of the active role)
  const canManageProducts = PRODUCT_PANEL_AREAS.some((area) => can(area))
  const canManageUsers = can("USUARIOS")
  // Low stock counter next to "Productos" (the same query as the dashboard card, cached)
  const lowStock = useLowStock(isAuthenticated && can("STOCK")).data?.length ?? 0
  // Sales is ADMINISTRADOR-only (not delegable yet); the counter is the receipts to review
  const canManageSales = isAuthenticated && activeRole === "ADMINISTRADOR"
  const pendingReview = usePendingReviewCount(canManageSales).data ?? 0

  const navProps: NavLinksProps = {
    hasBundles,
    showPanel: isAuthenticated && (canManageProducts || canManageUsers || canManageSales),
    canManageSales,
    canManageProducts: isAuthenticated && canManageProducts,
    canManageUsers: isAuthenticated && canManageUsers,
    pendingReview,
    lowStock,
  }

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border bg-card">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        <div className="flex items-center gap-1">
          <MobileNav {...navProps} />
          <Link to="/" className="flex min-w-0 items-center gap-2">
            <StoreBrand />
          </Link>
        </div>

        {/* Navigation in a row from md; on phones it lives in the side menu (MobileNav) */}
        <nav className="hidden md:flex items-center gap-6">
          <NavLinks {...navProps} />
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
                    <UserAvatar name={user?.name} avatar={me?.avatar} />
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
                      <DropdownMenuItem asChild>
                        <Link to="/admin/store">
                          <Store className="mr-2 h-4 w-4" />
                          {strings.adminStore.menu}
                        </Link>
                      </DropdownMenuItem>
                      <DropdownMenuItem asChild>
                        <Link to="/admin/orders">
                          <Receipt className="mr-2 h-4 w-4" />
                          {strings.adminOrders.menu}
                          {pendingReview > 0 ? (
                            <span className="ml-auto rounded-full bg-destructive px-1.5 text-[11px] font-medium leading-5 text-white">
                              {pendingReview}
                            </span>
                          ) : null}
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
              {/* On phones it does not fit next to the cart and the login; the login has the link to create the account */}
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
