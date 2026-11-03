import { Navigate, Route, Routes } from "react-router-dom"
import { IS_PLATFORM } from "@/lib/platform"
import { PRODUCT_PANEL_AREAS } from "@/lib/permissions"
import { RequireArea, RequireAuth, RequireRole, RequireSuperadmin } from "@/components/route-guards"
import { useTenantStatus } from "@/lib/tenant-status"
import { useCatalogCategories } from "@/api/catalog"
import StoreUnavailablePage from "@/pages/StoreUnavailablePage"
import HomePage from "@/pages/HomePage"
import LoginPage from "@/pages/LoginPage"
import RegisterPage from "@/pages/RegisterPage"
import AdminPage from "@/pages/admin/AdminPage"
import AdminProductsPage from "@/pages/admin/AdminProductsPage"
import AdminUsersPage from "@/pages/admin/AdminUsersPage"
import SettingsPage from "@/pages/user/SettingsPage"
import PlatformDashboardPage from "@/pages/platform/PlatformDashboardPage"
import PlatformSuperadminsPage from "@/pages/platform/PlatformSuperadminsPage"
import PlatformTenantsPage from "@/pages/platform/PlatformTenantsPage"

// Plataforma (localhost:2999): login del superadmin, resumen, tiendas y superadmins. Sin catalogo ni registro
function PlatformRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireSuperadmin />}>
        <Route path="/dashboard" element={<PlatformDashboardPage />} />
        <Route path="/tenants" element={<PlatformTenantsPage />} />
        <Route path="/superadmins" element={<PlatformSuperadminsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function StoreRoutes() {
  // Consulta publica y en cache que hace cualquier pagina de la tienda: si la tienda no existe o esta
  // deshabilitada, el gateway lo dice al abrir (tambien en el login, que no llama a la API hasta enviar)
  useCatalogCategories()
  const tenantStatus = useTenantStatus()
  if (tenantStatus !== "ok") {
    return <StoreUnavailablePage status={tenantStatus} />
  }

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/user/settings" element={<SettingsPage />} />
        {/* El panel de la tienda es solo del ADMINISTRADOR (docs/panel-admin.md); usuarios pide USUARIOS, que no se delega */}
        <Route element={<RequireRole role="ADMINISTRADOR" />}>
          <Route path="/admin" element={<AdminPage />} />
        </Route>
        <Route element={<RequireArea anyOf={PRODUCT_PANEL_AREAS} />}>
          <Route path="/admin/products" element={<AdminProductsPage />} />
        </Route>
        <Route element={<RequireArea anyOf={["USUARIOS"]} />}>
          <Route path="/admin/users" element={<AdminUsersPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return IS_PLATFORM ? <PlatformRoutes /> : <StoreRoutes />
}
