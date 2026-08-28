import { expect, test, type Page } from '@playwright/test'

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('normal')
  await page.getByLabel('密码').fill('ldap-e2e-secret')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/sql-editor/)
  await page.getByLabel('展开 订单测试库 mysql-a.internal:3306').click()
  await page.getByRole('button', { name: 'orders', exact: true }).click()
  await page.getByRole('button', { name: '打开 SQL 编辑器' }).click()
  await expect(page.getByRole('tab', { selected: true })).toContainText('订单测试库')
}

async function setSql(page: Page, sql: string): Promise<void> {
  const editor = page.locator('.monaco-editor').first()
  await editor.click()
  await page.keyboard.press('Control+A')
  await page.keyboard.insertText(sql)
  await page.waitForTimeout(120)
}

test.describe('sql editor copilot', () => {
  test('does not reload the resource tree when toggling Copilot', async ({ page }) => {
    await login(page)
    await expect(page.getByTestId('navigator-database-ds-orders-a-orders')).toBeVisible()
    await page.getByTestId('copilot-toggle').click()
    await expect(page.getByTestId('copilot-panel')).toBeVisible()
    await expect(page.getByTestId('navigator-database-ds-orders-a-orders')).toBeVisible()
    await expect(page.getByTestId('navigator-loading-ds-orders-a')).toHaveCount(0)
    await page.getByTestId('copilot-toggle').click()
    await expect(page.getByTestId('copilot-panel')).toHaveCount(0)
    await expect(page.getByTestId('navigator-database-ds-orders-a-orders')).toBeVisible()
    await expect(page.getByTestId('navigator-loading-ds-orders-a')).toHaveCount(0)
  })

  test('inserts generated sql without running, then insert-and-run executes it', async ({
    page,
  }) => {
    await login(page)
    await page.getByTestId('copilot-toggle').click()
    const panel = page.getByTestId('copilot-panel')
    await expect(panel).toBeVisible()
    await panel.getByTestId('copilot-input').fill('列出订单')
    await panel.getByTestId('copilot-send').click()
    await expect(panel.getByTestId('copilot-tools')).toContainText('列出数据表')
    await expect(panel.getByTestId('copilot-sql')).toContainText(
      'SELECT id, amount FROM order_item',
    )
    await panel.getByTestId('copilot-insert').click()
    await expect(page.getByTestId('result-pane')).toContainText('运行 SQL 后在这里查看结果')
    await page.getByTestId('run-button').click()
    await expect(page.getByText('9007199254740993')).toBeVisible()
  })

  test('insert and run writes sql and shows the user execution result', async ({ page }) => {
    await login(page)
    await page.getByTestId('copilot-toggle').click()
    await page.getByTestId('copilot-input').fill('列出订单')
    await page.getByTestId('copilot-send').click()
    await expect(page.getByTestId('copilot-sql')).toContainText('SELECT id, amount FROM order_item')
    await page.getByTestId('copilot-insert-and-run').click()
    await expect(page.getByText('9007199254740993')).toBeVisible()
    await expect(page.getByTestId('script-summary')).toHaveCount(0)
  })

  test('auto-sends a fix request and replaces the failed statement', async ({ page }) => {
    await login(page)
    await setSql(page, 'select * from mock_error')
    await page.getByTestId('run-button').click()
    await expect(page.getByText(/doesn't exist/)).toBeVisible()
    await page.getByTestId('copilot-fix').click()
    await expect(page.getByTestId('copilot-panel')).toBeVisible()
    await expect(page.getByTestId('copilot-messages')).toContainText('请修复下面这条失败的 SQL')
    await expect(page.getByTestId('copilot-sql')).toContainText('SELECT id, amount FROM order_item')
    await page.getByTestId('copilot-insert').click()
    await page.getByTestId('run-button').click()
    await expect(page.getByText('9007199254740993')).toBeVisible()
    await expect(page.getByText(/doesn't exist/)).toHaveCount(0)
  })

  test('keeps the thread across a follow-up, tab close, resume, and delete', async ({ page }) => {
    await login(page)
    await page.getByTestId('copilot-toggle').click()
    const panel = page.getByTestId('copilot-panel')
    await panel.getByTestId('copilot-input').fill('列出订单')
    await panel.getByTestId('copilot-send').click()
    await expect(panel.getByTestId('copilot-sql')).toContainText(
      'SELECT id, amount FROM order_item',
    )

    await panel.getByTestId('copilot-input').fill('加上时间过滤')
    await panel.getByTestId('copilot-send').click()
    await expect(panel.getByTestId('copilot-messages')).toContainText('接着「列出订单」')
    await expect(panel.getByTestId('copilot-sql').last()).toContainText('created_at')

    await page.getByLabel('新建页签').click()
    await page
      .getByRole('tab', { name: /Query 1/ })
      .locator('.tab-close')
      .click()
    await expect(panel.getByTestId('copilot-messages')).toContainText('列出订单')

    await panel.getByTestId('copilot-new').click()
    await panel.getByTestId('copilot-history-toggle').click()
    await expect(panel.getByTestId('copilot-history')).toContainText('列出订单')
    await expect(panel.getByTestId('copilot-history')).toContainText('ds-orders-a')
    await panel.locator('[data-testid^="copilot-history-item-"]').click()
    await expect(panel.getByTestId('copilot-messages')).toContainText('加上时间过滤')
    await expect(page.getByRole('tab', { selected: true })).toContainText('订单测试库')
    await expect(page.getByRole('tab', { selected: true })).toContainText('orders')

    await panel.getByTestId('copilot-new').click()
    await panel.getByTestId('copilot-history-toggle').click()
    await page.getByLabel('删除 列出订单').click()
    await page.getByRole('button', { name: '删除', exact: true }).click()
    await expect(panel.getByTestId('copilot-history-empty')).toBeVisible()

    await page.getByLabel('执行历史').click()
    await expect(page.getByText('暂无执行历史')).toBeVisible()
  })
})
