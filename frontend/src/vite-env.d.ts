/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_PUBLIC?: string
  // Platform host (no store). "localhost" by default
  readonly VITE_PLATFORM_HOSTNAME?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
