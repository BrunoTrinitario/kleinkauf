import { Navigate, Route, Routes } from "react-router-dom"
import { IS_PLATFORM } from "@/lib/platform"
import { PRODUCT_PANEL_AREAS } from "@/lib/permissions"
import { RequireArea, RequireAuth, RequireRole, RequireSuperadmin } from "@/components/route-guards"
import { useTenantStatus } from "@/lib/tenant-status"
import { useCatalogCategories } from "@/api/catalog"
import StoreUnavailablePage from "@/pages/StoreUnavailablePage"
import HomePage from "@/pages/HomePage"
import ProductPage from "@/pages/ProductPage"
import CatalogPage from "@/pages/CatalogPage"
import BundlesPage from "@/pages/BundlesPage"
import LegalPage from "@/pages/LegalPage"
import ForgotPasswordPage from "@/pages/ForgotPasswordPage"
import ResetPasswordPage from "@/pages/ResetPasswordPage"
import LoginPage from "@/pages/LoginPage"
import RegisterPage from "@/pages/RegisterPage"
import AdminPage from "@/pages/admin/AdminPage"
import AdminProductsPage from "@/pages/admin/AdminProductsPage"
import AdminBundlesPage from "@/pages/admin/AdminBundlesPage"
import AdminUsersPage from "@/pages/admin/AdminUsersPage"
import AdminOrdersPage from "@/pages/admin/AdminOrdersPage"
import AdminOrderPage from "@/pages/admin/AdminOrderPage"
import AdminLocalSalePage from "@/pages/admin/AdminLocalSalePage"
import AdminReportsPage from "@/pages/admin/AdminReportsPage"
import SettingsPage from "@/pages/user/SettingsPage"
import MyOrdersPage from "@/pages/user/MyOrdersPage"
import CartPage from "@/pages/CartPage"
import CheckoutPage from "@/pages/CheckoutPage"
import OrderPage from "@/pages/OrderPage"
import OrderLookupPage from "@/pages/OrderLookupPage"
import PlatformBugReportsPage from "@/pages/platform/PlatformBugReportsPage"
import PlatformDashboardPage from "@/pages/platform/PlatformDashboardPage"
import PlatformSuperadminsPage from "@/pages/platform/PlatformSuperadminsPage"
import PlatformTenantsPage from "@/pages/platform/PlatformTenantsPage"

// Platform (localhost:2999): superadmin login, overview, stores, superadmins and bug reports. No catalog or sign-up
function PlatformRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireSuperadmin />}>
        <Route path="/dashboard" element={<PlatformDashboardPage />} />
        <Route path="/tenants" element={<PlatformTenantsPage />} />
        <Route path="/superadmins" element={<PlatformSuperadminsPage />} />
        <Route path="/bug-reports" element={<PlatformBugReportsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function StoreRoutes() {
  // Public, cached query that any store page makes: if the store does not exist or is
  // disabled, the gateway says so on load (also on the login page, which does not call the API until submitting)
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
      {/* Password recovery: the email link goes to /reset-password?token= */}
      <Route path="/recuperar" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/catalogo" element={<CatalogPage />} />
      <Route path="/combos" element={<BundlesPage />} />
      <Route path="/productos/:slug" element={<ProductPage />} />
      <Route path="/privacidad" element={<LegalPage document="privacy" />} />
      <Route path="/terminos" element={<LegalPage document="terms" />} />
      {/* Online purchase: public (guest or with a session). The order is looked up with number + email */}
      <Route path="/carrito" element={<CartPage />} />
      <Route path="/checkout" element={<CheckoutPage />} />
      <Route path="/pedido" element={<OrderLookupPage />} />
      <Route path="/pedido/:number" element={<OrderPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/user/settings" element={<SettingsPage />} />
        <Route path="/mis-pedidos" element={<MyOrdersPage />} />
        {/* The store dashboard is ADMINISTRADOR-only (docs/panel-admin.md); users requires USUARIOS, which is not delegable */}
        <Route element={<RequireRole role="ADMINISTRADOR" />}>
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/admin/orders" element={<AdminOrdersPage />} />
          <Route path="/admin/orders/new" element={<AdminLocalSalePage />} />
          <Route path="/admin/reports" element={<AdminReportsPage />} />
          <Route path="/admin/orders/:number" element={<AdminOrderPage />} />
        </Route>
        <Route element={<RequireArea anyOf={PRODUCT_PANEL_AREAS} />}>
          <Route path="/admin/products" element={<AdminProductsPage />} />
          <Route path="/admin/bundles" element={<AdminBundlesPage />} />
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
