import path from "node:path"
import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(__dirname, "./src") },
  },
  server: {
    port: 2999,
    // El gateway reconoce la tienda por el Origin: en localhost:2999 (la plataforma) no hay tienda,
    // asi que se abre la tienda demo que crea el seed del backend
    open: "http://demo.localhost:2999",
  },
})
