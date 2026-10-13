// SQL optimizer live acceptance, main path.
// Drives the real Vite dev server against the real SQL service, real Agent Platform and real MySQL
// with the API mock disabled: capability, datasource creation, task submission, report, page close,
// reload and rescan.
//
// Prerequisites: tools/optimizer-live/stack.sh up
// Usage: node tools/optimizer-live/live-e2e.mjs
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = join(HERE, '..', '..')
const require = createRequire(join(REPO_ROOT, 'web', 'package.json'))
const { chromium } = require('@playwright/test')

const RUNTIME = process.env.OPTIMIZER_LIVE_RUNTIME || '/private/tmp/optimizer-live'
const stack = JSON.parse(readFileSync(join(RUNTIME, 'stack.json'), 'utf8'))
const keys = JSON.parse(readFileSync(stack.keys, 'utf8'))
const BASE = `http://127.0.0.1:${stack.ports.web}`
const USERNAME = (keys.users || ['zhangsan'])[0]
const PASSWORD = stack.loginPassword
const OUT = join(RUNTIME, 'live-result.json')
const DATASOURCE_NAME = process.env.OPTIMIZER_DATASOURCE_NAME || 'optimizer-demo-mysql'
const SQL_JOIN = `SELECT c.name, o.id, o.amount, o.status
FROM orders o
JOIN customers c ON c.id = o.customer_id
WHERE o.status = 'PAID' AND o.created_at >= '2025-05-01 00:00:00'
ORDER BY o.amount DESC`

const evidence = { startedAt: new Date().toISOString(), steps: [], taskIds: {} }
const log = (step, detail) => {
  console.log(`${new Date().toISOString()} ${step}${detail === undefined ? '' : ' ' + JSON.stringify(detail)}`)
  evidence.steps.push({ step, detail, at: new Date().toISOString() })
  writeFileSync(OUT, JSON.stringify(evidence, null, 2))
}
const save = () => writeFileSync(OUT, JSON.stringify(evidence, null, 2))
const fail = (message) => {
  throw new Error(message)
}

async function login(page, username = USERNAME) {
  await page.goto(`${BASE}/sql-optimizer`, { waitUntil: 'domcontentloaded' })
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(PASSWORD)
  await page.getByRole('checkbox', { name: '在此设备上记住我' }).check()
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => url.pathname !== '/login', { timeout: 30000 })
}
async function setSql(page, sql) {
  await page.locator('.monaco-editor').first().click({ position: { x: 140, y: 24 } })
  await page.keyboard.press('Control+A')
  await page.keyboard.insertText(sql)
  await page.waitForTimeout(300)
}
async function detail(page, id) {
  return page.evaluate(async (taskId) => {
    const token = window.localStorage.getItem('zorth.sql.token') || window.sessionStorage.getItem('zorth.sql.token')
    const response = await fetch(`/sql-api/api/v1/sql-optimization/tasks/${taskId}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    return { status: response.status, body: response.ok ? await response.json() : null }
  }, id)
}
async function waitTerminal(page, id, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  let last = null
  while (Date.now() < deadline) {
    last = await detail(page, id)
    if (last.body && ['SUCCEEDED', 'FAILED'].includes(last.body.status)) return last
    await page.waitForTimeout(3000)
  }
  return last
}

const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await context.newPage()
page.on('console', (message) => {
  if (message.type() === 'error') log('console.error', message.text())
})

try {
  // 1. real login; the capability comes from the SQL service allowlist, not from a mock
  await login(page)
  const navVisible = await page.getByRole('link', { name: 'SQL 优化器', exact: true }).isVisible()
  const session = await page.evaluate(async () => {
    const token = window.localStorage.getItem('zorth.sql.token') || window.sessionStorage.getItem('zorth.sql.token')
    const response = await fetch('/sql-api/api/v1/session', { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    return response.ok ? await response.json() : { status: response.status }
  })
  log('login.capabilities', { navVisible, capabilities: session.capabilities, userId: session.user?.id })
  if (!navVisible) fail('SQL 优化器 navigation entry is missing')
  if (!session.capabilities?.includes('SQL_OPTIMIZE')) fail('SQL_OPTIMIZE is missing from the real session response')

  // 2. ensure the target datasource exists (created through the real data source UI when absent)
  await page.goto(`${BASE}/data-sources`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('link', { name: '新增数据源' }).first().waitFor({ timeout: 30000 })
  await page.waitForTimeout(1500)
  const existing = await page.getByRole('row', { name: new RegExp(DATASOURCE_NAME) }).count()
  if (existing > 0) {
    log('datasource.existing', `${DATASOURCE_NAME} already present, reusing it`)
  } else {
    const mysql = stack.mysql
    if (!process.env.MYSQL_PASSWORD) fail('MYSQL_PASSWORD is required to create the target datasource')
    await page.goto(`${BASE}/data-sources/new`, { waitUntil: 'domcontentloaded' })
    await page.locator('#ds-engine-MYSQL').check()
    await page.locator('#ds-name').fill(DATASOURCE_NAME)
    await page.locator('#ds-host').fill(mysql.host)
    await page.locator('#ds-port').fill(String(mysql.port))
    await page.locator('#ds-username').fill(mysql.user)
    await page.locator('#ds-password').fill(process.env.MYSQL_PASSWORD)
    await page.locator('#ds-defaultDatabase').fill(mysql.demoDatabase)
    await page.getByRole('button', { name: '测试连接' }).click()
    await page.getByText('连接成功', { exact: true }).waitFor({ timeout: 30000 })
    log('datasource.test', (await page.getByRole('status').innerText()).split('\n').join(' '))
    await page.getByRole('button', { name: '保存' }).click()
    await page.waitForURL(/\/data-sources$/, { timeout: 30000 })
    const row = await page.getByRole('row', { name: new RegExp(DATASOURCE_NAME) }).first().innerText()
    log('datasource.saved', row.split('\n').join(' '))
  }
  const dataSourceId = await page.evaluate(async (name) => {
    const token = window.localStorage.getItem('zorth.sql.token') || window.sessionStorage.getItem('zorth.sql.token')
    const response = await fetch('/sql-api/api/v1/data-sources?keyword=&pageSize=100', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    const body = await response.json()
    return body.items.find((item) => item.name === name)?.id ?? null
  }, DATASOURCE_NAME)
  if (!dataSourceId) fail('target datasource id not found')
  evidence.dataSourceId = dataSourceId
  save()

  // 3. submit a real optimization task from the optimizer create form
  await page.goto(`${BASE}/sql-optimizer`, { waitUntil: 'domcontentloaded' })
  const name = `真实联调 JOIN ${Date.now().toString().slice(-5)}`
  await page.getByLabel('任务名称（可选）').fill(name)
  const target = page.getByRole('combobox', { name: '数据源', exact: true })
  await page.waitForFunction(
    () => {
      const select = Array.from(document.querySelectorAll('select')).find((element) =>
        Array.from(element.labels || []).some((label) => label.textContent.includes('数据源')),
      )
      return Boolean(select && select.options.length > 1)
    },
    { timeout: 30000 },
  )
  await target.selectOption(dataSourceId)
  await page.waitForTimeout(500)
  await page.getByLabel('数据库 / 命名空间').selectOption(stack.mysql.demoDatabase)
  await setSql(page, SQL_JOIN)
  await page.getByRole('button', { name: '提交扫描' }).click()
  await page.waitForURL(/\/sql-optimizer\/tasks\//, { timeout: 30000 })
  const taskUrl = page.url()
  const taskId = taskUrl.split('/').pop()
  evidence.taskIds.primary = taskId
  log('task.created', { taskId, name, url: taskUrl })
  await page.screenshot({ path: join(RUNTIME, 'live-01-submitted.png') })

  // 4. watch the real task reach a terminal state
  const statuses = []
  const deadline = Date.now() + 240000
  let terminal = null
  while (Date.now() < deadline) {
    const state = await detail(page, taskId)
    const status = state.body?.status ?? `HTTP_${state.status}`
    if (statuses[statuses.length - 1] !== status) statuses.push(status)
    if (['SUCCEEDED', 'FAILED'].includes(status)) {
      terminal = state.body
      break
    }
    await page.waitForTimeout(3000)
  }
  log('task.statuses', statuses)
  if (!terminal) fail('task did not reach a terminal state in time')
  evidence.task = terminal
  log('task.terminal', {
    status: terminal.status,
    error: terminal.error,
    reportSchemaVersion: terminal.reportSchemaVersion,
    summary: terminal.summary,
    issueCount: terminal.report?.issues?.length ?? null,
    suggestionCount: terminal.report?.suggestions?.length ?? null,
    limitationCount: terminal.report?.limitations?.length ?? null,
    startedAt: terminal.startedAt,
    completedAt: terminal.completedAt,
  })

  // 5. render the persisted report and screenshot it
  await page.goto(taskUrl, { waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: '总体结论' }).waitFor({ timeout: 30000 })
  writeFileSync(join(RUNTIME, 'live-report.txt'), await page.locator('main').innerText())
  await page.setViewportSize({ width: 1440, height: 2200 })
  await page.screenshot({ path: join(RUNTIME, 'live-02-report.png'), fullPage: true })
  log('report.rendered', { characters: (await page.locator('main').innerText()).length })

  // 6. close the page and return: the task must still be there for a fresh tab
  await page.close()
  const fresh = await context.newPage()
  await fresh.goto(`${BASE}/sql-optimizer`, { waitUntil: 'domcontentloaded' })
  await fresh.getByRole('link', { name, exact: true }).waitFor({ timeout: 60000 })
  log('task.visible-after-page-close', { name })
  await fresh.getByRole('link', { name, exact: true }).click()
  await fresh.getByRole('heading', { name: '总体结论' }).waitFor({ timeout: 30000 })
  await fresh.reload()
  await fresh.getByRole('heading', { name: '总体结论' }).waitFor({ timeout: 30000 })
  log('task.reload', { ok: true })

  // 7. rescan a terminal task
  await fresh.getByRole('button', { name: '再次扫描', exact: true }).click()
  await fresh.waitForURL((url) => url.pathname.startsWith('/sql-optimizer/tasks/') && url.href !== taskUrl, { timeout: 30000 })
  const retryId = fresh.url().split('/').pop()
  evidence.taskIds.retry = retryId
  await fresh.getByRole('link', { name: '原任务' }).waitFor({ timeout: 30000 })
  log('task.retry', { source: taskId, retry: retryId })
  const retryTerminal = await waitTerminal(fresh, retryId, 240000)
  log('task.retry-terminal', {
    status: retryTerminal.body?.status,
    sourceTaskId: retryTerminal.body?.sourceTaskId,
    error: retryTerminal.body?.error,
    summary: retryTerminal.body?.summary,
  })
  if (retryTerminal.body?.sourceTaskId !== taskId) fail('rescan did not link back to the original task')
  evidence.retryTask = {
    id: retryTerminal.body?.id,
    status: retryTerminal.body?.status,
    sourceTaskId: retryTerminal.body?.sourceTaskId,
    summary: retryTerminal.body?.summary,
    error: retryTerminal.body?.error,
  }

  evidence.finishedAt = new Date().toISOString()
  save()
  log('done', { ok: true })
} catch (error) {
  log('error', String(error && error.stack ? error.stack.split('\n').slice(0, 4).join(' | ') : error))
  await page.screenshot({ path: join(RUNTIME, 'live-error.png') }).catch(() => {})
  save()
  process.exitCode = 1
} finally {
  await browser.close()
}
