import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

function validateBuildEnvironment(mode: string): void {
  const env = loadEnv(mode, process.cwd(), '')
  if (mode === 'production') {
    const required = ['VITE_SQL_API_BASE', 'VITE_AUTH_API_BASE', 'VITE_LEGACY_PORTAL_URL']
    const missing = required.filter((key) => !env[key]?.trim())
    if (missing.length) throw new Error(`生产环境缺少配置：${missing.join(', ')}`)
    if (env.VITE_ENABLE_API_MOCK === 'true') throw new Error('生产环境禁止启用 API Mock')
  }
}

export default defineConfig(({ mode }) => {
  validateBuildEnvironment(mode)
  const env = loadEnv(mode, process.cwd(), '')
  const proxy: Record<
    string,
    { target: string; changeOrigin: boolean; rewrite: (path: string) => string }
  > = {}
  if (env.VITE_DEV_SQL_PROXY_TARGET)
    proxy['/sql-api'] = {
      target: env.VITE_DEV_SQL_PROXY_TARGET,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/sql-api/, ''),
    }
  if (env.VITE_DEV_AUTH_PROXY_TARGET)
    proxy['/auth-api'] = {
      target: env.VITE_DEV_AUTH_PROXY_TARGET,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/auth-api/, ''),
    }
  return {
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    optimizeDeps: { exclude: ['monaco-editor'] },
    worker: { format: 'es' },
    server: { port: 5173, proxy },
    build: { sourcemap: false, target: 'es2022' },
  }
})
