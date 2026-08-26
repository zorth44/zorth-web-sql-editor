import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'

function validateBuildEnvironment(mode: string): void {
  const env = loadEnv(mode, process.cwd(), '')
  if (mode === 'production') {
    const required = [
      'VITE_SQL_API_BASE',
      'VITE_AUTH_API_BASE',
      'VITE_AI_API_BASE',
      'VITE_LEGACY_PORTAL_URL',
    ]
    const missing = required.filter((key) => !env[key]?.trim())
    if (missing.length) throw new Error(`生产环境缺少配置：${missing.join(', ')}`)
    if (env.VITE_ENABLE_API_MOCK === 'true') throw new Error('生产环境禁止启用 API Mock')
  }
}

export default defineConfig(({ mode }) => {
  validateBuildEnvironment(mode)
  const env = loadEnv(mode, process.cwd(), '')
  const proxy: Record<string, ProxyOptions> = {}
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
  if (env.VITE_DEV_AI_PROXY_TARGET)
    proxy['/ai-api'] = {
      target: env.VITE_DEV_AI_PROXY_TARGET,
      changeOrigin: true,
      timeout: 310_000,
      proxyTimeout: 310_000,
      rewrite: (path) => path.replace(/^\/ai-api/, ''),
      configure(proxyServer) {
        proxyServer.on('proxyRes', (proxyRes, _request, response) => {
          const type = String(proxyRes.headers['content-type'] || '')
          if (!type.includes('text/event-stream')) return
          proxyRes.headers['cache-control'] = 'no-cache, no-transform'
          proxyRes.headers['x-accel-buffering'] = 'no'
          response.setHeader('Cache-Control', 'no-cache, no-transform')
          response.setHeader('X-Accel-Buffering', 'no')
        })
      },
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
