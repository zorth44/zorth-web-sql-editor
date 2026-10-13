// SQL optimizer live acceptance, part 2: page-close completion, capability enforcement,
// credential expiry after a real logout, cross-user isolation and HTTP contract checks.
//
// Usage:
//   node tools/optimizer-live/live-failures.mjs pageclose
//   # restart the stack with the scheduler disabled, then:
//   node tools/optimizer-live/live-failures.mjs stage-expiry
//   # restart the stack with the scheduler enabled again, then:
//   node tools/optimizer-live/live-failures.mjs verify
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = join(HERE, '..', '..')
const require = createRequire(join(REPO_ROOT, 'web', 'package.json'))
const { chromium } = require('@playwright/test')

const PHASE = process.argv[2]
const RUNTIME = process.env.OPTIMIZER_LIVE_RUNTIME || '/private/tmp/optimizer-live'
const stack = JSON.parse(readFileSync(join(RUNTIME, 'stack.json'), 'utf8'))
const keys = JSON.parse(readFileSync(stack.keys, 'utf8'))
const BASE = `http://127.0.0.1:${stack.ports.web}`
const API = `http://127.0.0.1:${stack.ports.api}`
const AUTH = `http://127.0.0.1:${stack.ports.auth}`
const INTERNAL_KEY = 'local-sql-editor-key'
const STATE = join(RUNTIME, 'failure-state.json')
const PASSWORD = stack.loginPassword
const users = keys.users || ['zhangsan']
const USERS = { capable: users[0], other: users[1] || users[0], incapable: keys.denied_user || 'wangwu' }
const DATASOURCE_NAME = process.env.OPTIMIZER_DATASOURCE_NAME || 'optimizer-demo-mysql'
const SQL = `SELECT o.id, o.amount, o.status FROM orders o WHERE o.status = 'PAID' ORDER BY o.created_at DESC`

const file = (() => {
  try {
    return JSON.parse(readFileSync(STATE, 'utf8'))
  } catch {
    return { steps: [] }
  }
})()
const log = (step, detail) => {
  console.log(`${new Date().toISOString()} ${step}${detail === undefined ? '' : ' ' + JSON.stringify(detail)}`)
  file.steps.push({ step, detail, at: new Date().toISOString() })
  writeFileSync(STATE, JSON.stringify(file, null, 2))
}
const fail = (message) => {
  throw new Error(message)
}
async function session(username) {
  const response = await fetch(`${AUTH}/ldap/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PASSWORD, productType: 'chinaBank' }),
  })
  const body = await response.json()
  if (body.code !== 200) fail(`login for ${username} failed: ${JSON.stringify(body)}`)
  return body.token
}
async function dataSourceId(token) {
  const response = await fetch(`${API}/api/v1/data-sources?keyword=&pageSize=100`, { headers: { Authorization: `Bearer ${token}` } })
  const body = await response.json()
  return body.items.find((item) => item.name === DATASOURCE_NAME)?.id ?? null
}
async function createTask(token, datasourceId, name) {
  const response = await fetch(`${API}/api/v1/sql-optimization/tasks`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'X-Request-Id': crypto.randomUUID(),
      'Idempotency-Key': crypto.randomUUID(),
    },
    body: JSON.stringify({ name, datasourceId, database: stack.mysql.demoDatabase, sql: SQL }),
  })
  return { status: response.status, location: response.headers.get('location'), body: await response.json().catch(() => null) }
}
async function detail(token, id) {
  const response = await fetch(`${API}/api/v1/sql-optimization/tasks/${id}`, { headers: { Authorization: `Bearer ${token}` } })
  return { status: response.status, body: await response.json().catch(() => null) }
}
async function waitTerminal(token, id, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  let last = null
  while (Date.now() < deadline) {
    last = await detail(token, id)
    if (last.body && ['SUCCEEDED', 'FAILED'].includes(last.body.status)) return last
    await new Promise((resolve) => setTimeout(resolve, 3000))
  }
  return last
}
async function browserLogin(page, username) {
  await page.goto(`${BASE}/sql-optimizer`, { waitUntil: 'domcontentloaded' })
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(PASSWORD)
  await page.getByRole('checkbox', { name: '在此设备上记住我' }).check()
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30000 })
}
const browser = await chromium.launch()

try {
  if (PHASE === 'pageclose') {
    // The task must finish on the server after the browser page that started it disappears.
    const page = await (await browser.newContext()).newPage()
    await browserLogin(page, USERS.capable)
    const token = await page.evaluate(() => window.localStorage.getItem('zorth.sql.token') || window.sessionStorage.getItem('zorth.sql.token'))
    const datasourceId = await dataSourceId(token)
    if (!datasourceId) fail('target datasource not found; run live-e2e.mjs first')
    const task = await createTask(token, datasourceId, '关页后继续（真实联调）')
    log('page-close.create', { status: task.status, location: task.location, id: task.body?.id })
    if (task.status !== 202) fail(`expected 202, got ${task.status}`)
    if (!task.location || !task.location.includes(task.body.id)) fail('202 response is missing a Location header for the new task')
    file.openTaskId = task.body.id
    await page.waitForFunction(
      async (id) => {
        const bearer = window.localStorage.getItem('zorth.sql.token') || window.sessionStorage.getItem('zorth.sql.token')
        const response = await fetch(`/sql-api/api/v1/sql-optimization/tasks/${id}`, { headers: { Authorization: `Bearer ${bearer}` } })
        return response.ok ? (await response.json()).status === 'RUNNING' : false
      },
      task.body.id,
      { timeout: 60000 },
    )
    await page.close()
    log('page-close.closed', { id: task.body.id })
    const finished = await waitTerminal(token, task.body.id, 240000)
    log('page-close.terminal', { id: task.body.id, status: finished.body?.status, error: finished.body?.error })
    if (finished.body?.status !== 'SUCCEEDED') fail(`task did not succeed after the page closed: ${finished.body?.status}`)

    // An account without the capability keeps the entry hidden and the API closed.
    const incapable = await (await browser.newContext()).newPage()
    await browserLogin(incapable, USERS.incapable)
    const navCount = await incapable.getByRole('link', { name: 'SQL 优化器', exact: true }).count()
    await incapable.goto(`${BASE}/sql-optimizer`, { waitUntil: 'domcontentloaded' })
    await incapable.getByText('当前账号没有 SQL 优化器能力。').waitFor({ timeout: 30000 })
    await incapable.screenshot({ path: join(RUNTIME, 'live-05-forbidden.png'), fullPage: true })
    const incapableToken = await incapable.evaluate(() => window.localStorage.getItem('zorth.sql.token') || window.sessionStorage.getItem('zorth.sql.token'))
    const forbidden = await createTask(incapableToken, datasourceId, '无能力账号（真实联调）')
    log('capability.enforced', { navLinks: navCount, createStatus: forbidden.status, code: forbidden.body?.code })
    if (navCount !== 0) fail('optimizer entry must stay hidden for an account without the capability')
    if (forbidden.status !== 403) fail(`create without capability expected 403, got ${forbidden.status}`)
  } else if (PHASE === 'stage-expiry') {
    // Queue work whose bearer is revoked before any worker can claim it (scheduler disabled).
    const capableToken = await session(USERS.capable)
    const otherToken = await session(USERS.other)
    const datasourceId = await dataSourceId(capableToken)
    if (!datasourceId) fail('target datasource not found; run live-e2e.mjs first')

    const expiryTask = await createTask(capableToken, datasourceId, '凭证过期（真实联调）')
    const otherTask = await createTask(otherToken, datasourceId, '其他用户任务（真实联调）')
    log('stage.create', { expiry: expiryTask.body?.id, other: otherTask.body?.id })
    if (expiryTask.status !== 202 || otherTask.status !== 202) fail('staged task creation did not return 202')
    file.expiryTaskId = expiryTask.body.id
    file.otherTaskId = otherTask.body.id

    const crossRead = await detail(capableToken, otherTask.body.id)
    log('cross-user.read', { status: crossRead.status, code: crossRead.body?.code })
    if (crossRead.status !== 404) fail(`cross-user read expected 404, got ${crossRead.status}`)

    for (const [name, bearer] of [
      [USERS.capable, capableToken],
      [USERS.other, otherToken],
    ]) {
      const response = await fetch(`${AUTH}/logout`, { method: 'POST', headers: { Authorization: `Bearer ${bearer}` } })
      log('logout', { user: name, status: response.status })
    }
    // The authority is the authorization service; the SQL service caches its session answer for a
    // minute, while the worker reauthorizes through the uncached client.
    const afterRevoke = await fetch(`${AUTH}/internal/api/v1/auth/context`, {
      headers: { Authorization: `Bearer ${capableToken}`, 'X-Internal-Service-Key': INTERNAL_KEY },
    })
    log('revoked-token.rejected', { status: afterRevoke.status })
    if (afterRevoke.status !== 401) fail(`revoked token expected 401 at the authorization service, got ${afterRevoke.status}`)
  } else if (PHASE === 'verify') {
    const token = await session(USERS.capable)
    const { expiryTaskId, otherTaskId } = file

    const expired = await waitTerminal(token, expiryTaskId, 180000)
    log('expiry.terminal', {
      httpStatus: expired.status,
      status: expired.body?.status,
      error: expired.body?.error,
      startedAt: expired.body?.startedAt,
      completedAt: expired.body?.completedAt,
      report: expired.body?.report,
      summary: expired.body?.summary,
    })
    if (expired.status !== 200) fail(`FAILED task must still answer HTTP 200, got ${expired.status}`)
    if (expired.body?.status !== 'FAILED' || expired.body?.error?.code !== 'CREDENTIAL_EXPIRED')
      fail(`expected FAILED/CREDENTIAL_EXPIRED, got ${expired.body?.status}/${expired.body?.error?.code}`)
    if (expired.body?.report !== null || expired.body?.summary !== null) fail('FAILED task must not expose a report')

    const other = await detail(token, otherTaskId)
    log('cross-user.after-restart', { status: other.status, code: other.body?.code })
    if (other.status !== 404) fail(`cross-user read after restart expected 404, got ${other.status}`)

    // HTTP contract: cursor binding, page size, retry of a FAILED task.
    const listPage = await fetch(`${API}/api/v1/sql-optimization/tasks?pageSize=2`, { headers: { Authorization: `Bearer ${token}` } })
    const listBody = await listPage.json()
    log('list.page', { status: listPage.status, items: listBody.items?.length, nextPageToken: Boolean(listBody.nextPageToken) })
    if (listPage.status !== 200 || listBody.items.length !== 2 || !listBody.nextPageToken) fail('list paging contract failed')
    const tampered = `${listBody.nextPageToken.slice(0, -2)}zz`
    const badToken = await fetch(`${API}/api/v1/sql-optimization/tasks?pageToken=${encodeURIComponent(tampered)}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    log('list.bad-token', { status: badToken.status, code: (await badToken.json().catch(() => null))?.code })
    if (badToken.status !== 400) fail(`tampered cursor expected 400, got ${badToken.status}`)
    const mismatched = await fetch(`${API}/api/v1/sql-optimization/tasks?status=FAILED&pageToken=${encodeURIComponent(listBody.nextPageToken)}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    log('list.mismatched-token', { status: mismatched.status, code: (await mismatched.json().catch(() => null))?.code })
    if (mismatched.status !== 400) fail(`cursor reused with another filter expected 400, got ${mismatched.status}`)

    const retry = await fetch(`${API}/api/v1/sql-optimization/tasks/${expiryTaskId}/retry`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'X-Request-Id': crypto.randomUUID(), 'Idempotency-Key': crypto.randomUUID() },
    })
    const retryBody = await retry.json().catch(() => null)
    log('retry.failed-task', { status: retry.status, location: retry.headers.get('location'), id: retryBody?.id, source: expiryTaskId })
    if (retry.status !== 202) fail(`retry of a FAILED task expected 202, got ${retry.status}`)
    file.retryTaskId = retryBody?.id

    // The internal Agent API must not accept the paired key as a user identity.
    const datasourceId = await dataSourceId(token)
    const internalUrl = `${API}/internal/api/v1/agent/data-sources/${datasourceId}`
    const boundaries = {
      keyOnly: (await fetch(internalUrl, { headers: { 'X-Internal-Service-Key': keys.agent_internal_caller_key } })).status,
      keyAndBearer: (await fetch(internalUrl, { headers: { 'X-Internal-Service-Key': keys.agent_internal_caller_key, Authorization: `Bearer ${token}` } })).status,
      wrongKey: (await fetch(internalUrl, { headers: { 'X-Internal-Service-Key': 'wrong-key', Authorization: `Bearer ${token}` } })).status,
      noKey: (await fetch(internalUrl, { headers: { Authorization: `Bearer ${token}` } })).status,
    }
    log('internal-api.boundary', boundaries)
    if (boundaries.keyOnly !== 401) fail(`paired key alone must not authenticate a user, got ${boundaries.keyOnly}`)
    if (boundaries.wrongKey !== 401 || boundaries.noKey !== 401) fail('the paired caller key must be enforced')
    if (boundaries.keyAndBearer !== 200) fail(`valid bearer with the paired key expected 200, got ${boundaries.keyAndBearer}`)

    // The UI must render the server-side failure without inventing a report.
    const page = await (await browser.newContext()).newPage()
    await browserLogin(page, USERS.capable)
    await page.goto(`${BASE}/sql-optimizer/tasks/${expiryTaskId}`, { waitUntil: 'domcontentloaded' })
    await page.getByRole('heading', { name: '扫描失败' }).waitFor({ timeout: 30000 })
    const failureText = await page.locator('main').innerText()
    writeFileSync(join(RUNTIME, 'live-failure.txt'), failureText)
    await page.screenshot({ path: join(RUNTIME, 'live-03-credential-expired.png'), fullPage: true })
    log('expiry.ui', { heading: '扫描失败', characters: failureText.length })

    await page.goto(`${BASE}/sql-optimizer/tasks/${otherTaskId}`, { waitUntil: 'domcontentloaded' })
    await page.getByText('任务不存在或不可见。').waitFor({ timeout: 30000 })
    await page.screenshot({ path: join(RUNTIME, 'live-04-not-found.png'), fullPage: true })
    log('cross-user.ui', { visible: true })
    file.finishedAt = new Date().toISOString()
  } else {
    fail('usage: node live-failures.mjs <pageclose|stage-expiry|verify>')
  }
  writeFileSync(STATE, JSON.stringify(file, null, 2))
  log('done', { phase: PHASE })
} catch (error) {
  log('error', String(error && error.message ? error.message : error))
  process.exitCode = 1
} finally {
  await browser.close()
}
