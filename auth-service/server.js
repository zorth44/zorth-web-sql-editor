import http from 'node:http'
import { createHash, randomUUID } from 'node:crypto'

const port = numberEnv('AUTH_SERVICE_PORT', 8090)
const internalKey = process.env.AUTH_SERVICE_INTERNAL_KEY || 'local-sql-editor-key'
const tokenTtlSeconds = numberEnv('AUTH_SERVICE_TOKEN_TTL_SECONDS', 8 * 60 * 60)
const allowedOrigins = new Set(
  (process.env.AUTH_SERVICE_ALLOWED_ORIGINS || 'http://localhost:5173')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean),
)
const product = {
  id: process.env.AUTH_SERVICE_PRODUCT_ID || 'local-product',
  name: process.env.AUTH_SERVICE_PRODUCT_NAME || '本地开发产品',
}
const tokens = new Map()

function numberEnv(name, fallback) {
  const value = Number(process.env[name] || fallback)
  if (!Number.isFinite(value) || value <= 0) throw new Error(`${name} must be a positive number`)
  return value
}

function cors(request, response) {
  const origin = request.headers.origin
  if (origin && allowedOrigins.has(origin)) {
    response.setHeader('Access-Control-Allow-Origin', origin)
    response.setHeader('Vary', 'Origin')
    response.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, X-Request-Id')
    response.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
  }
}

function json(request, response, status, body) {
  cors(request, response)
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(body))
}

async function readJson(request) {
  const chunks = []
  let size = 0
  for await (const chunk of request) {
    size += chunk.length
    if (size > 64 * 1024) throw new Error('REQUEST_TOO_LARGE')
    chunks.push(chunk)
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')
}

function bearer(request) {
  const value = request.headers.authorization || ''
  return value.startsWith('Bearer ') ? value.slice(7).trim() : ''
}

function active(token) {
  const session = tokens.get(token)
  if (!session) return null
  if (session.expiresAt <= Date.now()) {
    tokens.delete(token)
    return null
  }
  return session
}

function userId(username) {
  return createHash('sha256').update(`local:${username}`).digest('hex').slice(0, 16)
}

const server = http.createServer(async (request, response) => {
  try {
    if (request.method === 'OPTIONS') {
      cors(request, response)
      response.writeHead(204)
      response.end()
      return
    }
    if (request.method === 'GET' && request.url === '/health') {
      json(request, response, 200, { status: 'UP', service: 'temporary-local-auth-service' })
      return
    }
    if (request.method === 'POST' && request.url === '/ldap/login') {
      const body = await readJson(request)
      const username = typeof body.username === 'string' ? body.username.trim() : ''
      const password = typeof body.password === 'string' ? body.password : ''
      const types = new Set(['synthetical', 'chinaBank', 'oversea'])
      if (!username || password.length <= 12 || (body.productType && !types.has(body.productType))) {
        json(request, response, 200, { code: 400, msg: '本地开发用户名或密码格式不正确' })
        return
      }
      const token = `local-${randomUUID()}`
      const expiresAt = Date.now() + tokenTtlSeconds * 1000
      tokens.set(token, { userId: userId(username), username, displayName: username, expiresAt })
      json(request, response, 200, { code: 200, msg: '登录成功', token })
      return
    }
    if (request.method === 'POST' && request.url === '/logout') {
      tokens.delete(bearer(request))
      json(request, response, 200, { code: 200, msg: '退出成功' })
      return
    }
    if (request.method === 'GET' && request.url === '/internal/api/v1/auth/context') {
      if (request.headers['x-internal-service-key'] !== internalKey) {
        json(request, response, 403, { code: 'FORBIDDEN', message: '内部服务凭据无效' })
        return
      }
      const session = active(bearer(request))
      if (!session) {
        json(request, response, 401, { code: 'UNAUTHENTICATED', message: 'Token 无效或已过期' })
        return
      }
      json(request, response, 200, {
        userId: session.userId,
        username: session.username,
        displayName: session.displayName,
        product,
        tokenExpiresAt: new Date(session.expiresAt).toISOString(),
      })
      return
    }
    json(request, response, 404, { code: 'NOT_FOUND', message: '接口不存在' })
  } catch (error) {
    const status = error instanceof SyntaxError ? 400 : error?.message === 'REQUEST_TOO_LARGE' ? 413 : 500
    json(request, response, status, { code: status === 500 ? 'INTERNAL_ERROR' : 'VALIDATION_FAILED', message: status === 500 ? '服务暂时不可用' : '请求格式不合法' })
  }
})

server.listen(port, '127.0.0.1', () => {
  process.stdout.write(`Temporary auth service listening on http://127.0.0.1:${port}\n`)
})
