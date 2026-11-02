/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_PUBLIC?: string
  // Host de la plataforma (sin tienda). Por defecto "localhost"
  readonly VITE_PLATFORM_HOSTNAME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
