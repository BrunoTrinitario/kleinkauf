import { Navigate, Route, Routes } from "react-router-dom"
import { IS_PLATFORM } from "@/lib/platform"
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
      <Route path="/dashboard" element={<PlatformDashboardPage />} />
      <Route path="/tenants" element={<PlatformTenantsPage />} />
      <Route path="/superadmins" element={<PlatformSuperadminsPage />} />
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
      <Route path="/admin" element={<AdminPage />} />
      <Route path="/admin/products" element={<AdminProductsPage />} />
      <Route path="/admin/users" element={<AdminUsersPage />} />
      <Route path="/user/settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return IS_PLATFORM ? <PlatformRoutes /> : <StoreRoutes />
}
