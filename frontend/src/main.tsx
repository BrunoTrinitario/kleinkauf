import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter } from "react-router-dom"
import { QueryClientProvider } from "@tanstack/react-query"
import { Toaster } from "@/components/ui/sonner"
import { AuthProvider } from "@/lib/auth-context"
import { CartProvider } from "@/lib/cart"
import { queryClient } from "@/api/query-client"
import { IS_PLATFORM } from "@/lib/platform"
import { applyRememberedStoreTheme } from "@/lib/store-themes"
import App from "./App"
import "./index.css"

// Store theme before the first paint (useStoreBranding confirms it once the settings arrive)
if (!IS_PLATFORM) applyRememberedStoreTheme()

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <CartProvider>
            <App />
            <Toaster position="top-center" />
          </CartProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>
)
