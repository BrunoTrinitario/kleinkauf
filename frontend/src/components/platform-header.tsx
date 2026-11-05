import { Link, NavLink } from "react-router-dom"
import { useAuth } from "@/lib/auth-context"
import { strings } from "@/lib/strings"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Activity, Bug, Building2, LayoutDashboard, LogOut, ScrollText, ShieldCheck, Store, User } from "lucide-react"

const NAV = [
  { to: "/dashboard", label: strings.platform.dashboard, icon: LayoutDashboard },
  { to: "/estado", label: strings.platform.health, icon: Activity },
  { to: "/tenants", label: strings.platform.tenants, icon: Store },
  { to: "/superadmins", label: strings.platform.superadmins, icon: ShieldCheck },
  { to: "/auditoria", label: strings.platform.audit, icon: ScrollText },
  { to: "/bug-reports", label: strings.platform.bugReports, icon: Bug },
]

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `text-sm transition-colors ${isActive ? "text-foreground font-medium" : "text-muted-foreground hover:text-foreground"}`

// Platform dashboard header: no catalog, store roles or account settings
export function PlatformHeader() {
  const { user, logout } = useAuth()

  return (
    <header className="sticky top-0 z-40 w-full border-b border-border bg-card">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2">
          <Building2 className="h-5 w-5" strokeWidth={1.5} />
          <span className="text-sm font-medium">{strings.platform.name}</span>
        </Link>

        <nav className="hidden md:flex items-center gap-6">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className={navLinkClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="flex items-center gap-2 px-2">
              <div className="h-8 w-8 rounded-full bg-muted flex items-center justify-center">
                <User className="h-4 w-4" />
              </div>
              <div className="hidden sm:flex flex-col items-start">
                <span className="text-sm font-medium leading-none">{user?.name}</span>
                <span className="text-[10px] uppercase tracking-wider px-1.5 py-0.5 rounded mt-1 bg-foreground text-background">
                  {strings.platform.superadmin}
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
            {/* On mobile the navigation goes in this menu */}
            {NAV.map((item) => (
              <DropdownMenuItem key={item.to} asChild className="md:hidden">
                <Link to={item.to}>
                  <item.icon className="mr-2 h-4 w-4" />
                  {item.label}
                </Link>
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator className="md:hidden" />
            <DropdownMenuItem onClick={() => logout()} className="text-destructive focus:text-destructive">
              <LogOut className="mr-2 h-4 w-4" />
              {strings.auth.signOut}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
