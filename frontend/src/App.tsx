import { Navigate, Route, Routes } from "react-router-dom"
import { IS_PLATFORM } from "@/lib/platform"
import HomePage from "@/pages/HomePage"
import LoginPage from "@/pages/LoginPage"
import RegisterPage from "@/pages/RegisterPage"
import AdminPage from "@/pages/admin/AdminPage"
import AdminProductsPage from "@/pages/admin/AdminProductsPage"
import AdminUsersPage from "@/pages/admin/AdminUsersPage"
import SettingsPage from "@/pages/user/SettingsPage"
import PlatformTenantsPage from "@/pages/platform/PlatformTenantsPage"

// Plataforma (localhost:2999): login del superadmin y panel de tiendas. Sin catalogo ni registro
function PlatformRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/tenants" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/tenants" element={<PlatformTenantsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function StoreRoutes() {
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
