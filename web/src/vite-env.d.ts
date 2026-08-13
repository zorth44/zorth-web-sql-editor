/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly MODE: string
  readonly PROD: boolean
  readonly VITE_SQL_API_BASE?: string
  readonly VITE_AUTH_API_BASE?: string
  readonly VITE_AUTH_PRODUCT_TYPE?: string
  readonly VITE_AUTH_BRIDGE_ALLOWED_ORIGINS?: string
  readonly VITE_LEGACY_PORTAL_URL?: string
  readonly VITE_ENABLE_API_MOCK?: string
}
