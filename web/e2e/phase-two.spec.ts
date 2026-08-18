import { expect, test, type Page } from '@playwright/test'

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('normal')
  await page.getByLabel('密码').fill('ldap-e2e-secret')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/sql-editor/)
  await expect(page.getByTestId('welcome-start')).toBeVisible()
  await expect(page.getByTestId('navigator-source-ds-orders-a')).toBeVisible()
  await expect(page.getByTestId('navigator-source-ds-orders-b')).toBeVisible()
  await expect(page.getByTestId('navigator-database-ds-orders-b-orders')).toHaveCount(0)
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

test.describe('phase-two SQL editor', () => {
  test('shows a welcome page until a SQL tab is opened and after the last tab is closed', async ({
    page,
  }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('normal')
    await page.getByLabel('密码').fill('ldap-e2e-secret')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/sql-editor/)
    await expect(page.getByTestId('welcome-start')).toBeVisible()
    await expect(page.getByRole('tablist', { name: '编辑器页签' })).toHaveCount(0)
    await page.getByRole('button', { name: '打开 SQL 编辑器' }).click()
    await expect(page.getByRole('tab', { selected: true })).toContainText('Query 1')
    await page.locator('.tab-close').click()
    await expect(page.getByTestId('welcome-start')).toBeVisible()
    await expect(page.getByRole('tablist', { name: '编辑器页签' })).toHaveCount(0)
  })

  test('metadata, SELECT result, CSV export, history reopen, DDL, error, and cancel', async ({
    page,
  }) => {
    await login(page)
    await expect(page.getByTestId('navigator-database-ds-orders-a-orders')).toBeVisible()
    await page.getByLabel('折叠 订单测试库 mysql-a.internal:3306').click()
    await expect(page.getByTestId('navigator-database-ds-orders-a-orders')).toHaveCount(0)
    await page.getByLabel('展开 订单测试库 mysql-a.internal:3306').click()
    await expect(page.getByTestId('navigator-database-ds-orders-a-orders')).toBeVisible()
    await page.getByRole('button', { name: 'orders', exact: true }).click()
    await expect(page.getByText('order_item', { exact: true })).toBeVisible()
    await page.getByTestId('navigator-table-ds-orders-a-orders-order_item').dblclick()
    await expect(page.getByTestId('table-viewer')).toBeVisible()
    await expect(page.getByTestId('table-viewer-tab-data')).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText('9007199254740993')).toBeVisible()
    await expect(page.getByText('Diagram')).toHaveCount(0)
    await page.getByTestId('table-viewer-tab-properties').click()
    await page.getByTestId('table-properties-nav-columns').click()
    await expect(page.getByText('amount', { exact: true })).toBeVisible()
    await page.getByTestId('table-properties-nav-ddl').click()
    await expect(page.getByTestId('table-properties-ddl')).toContainText(
      'CREATE TABLE `order_item`',
    )
    await page.getByRole('tab', { name: /Query 1/ }).click()

    await setSql(page, 'select * from order_item')
    await page.getByRole('button', { name: '运行' }).click()
    await expect(page.getByText('9007199254740993')).toBeVisible()
    await expect(page.getByText('BINARY · 12 bytes')).toBeVisible()

    const download = page.waitForEvent('download')
    await page.getByRole('button', { name: '导出' }).click()
    await page.getByRole('button', { name: '继续导出' }).click()
    expect((await download).suggestedFilename()).toContain('mock-orders')

    await page.getByTitle('执行历史').click()
    await expect(page.getByText('select * from order_item', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: /select \* from order_item SUCCESS/ }).click()
    await expect(page.getByRole('tab', { name: /History / })).toBeVisible()

    await setSql(page, 'create table demo(id int)')
    await page.getByRole('button', { name: '运行' }).click()
    await expect(page.getByText('执行成功', { exact: true })).toBeVisible()

    await setSql(page, 'select * from mock_error')
    await page.getByRole('button', { name: '运行' }).click()
    await expect(page.getByText(/doesn't exist/)).toBeVisible()

    await setSql(page, 'select sleep(10)')
    await page.getByRole('button', { name: '运行' }).click()
    await expect(page.getByRole('button', { name: '停止' })).toBeVisible()
    await page.getByRole('button', { name: '停止' }).click()
    await expect(page.getByRole('button', { name: '运行' })).toBeVisible()
  })

  test('runs a multi-statement script, browses each result, and stops on the first error', async ({
    page,
  }) => {
    await login(page)
    await setSql(
      page,
      'select * from order_item;\nupdate order_item set amount = 1;\nselect * from order_item',
    )
    await expect(page.getByTestId('run-button')).toContainText('运行')
    await expect(page.getByTestId('run-button')).not.toContainText('运行选中')
    await page.getByTestId('run-button').click()

    const summary = page.getByTestId('script-summary')
    await expect(summary).toContainText('共 3 条语句，成功 3 条')
    await expect(summary).toContainText('UPDATE')
    await expect(summary).toContainText('影响 1 行')
    await page.getByRole('tab', { name: '第 1 条语句，成功' }).click()
    await expect(page.getByText('9007199254740993')).toBeVisible()
    await page.getByRole('tab', { name: '第 2 条语句，成功' }).click()
    await expect(page.getByText('执行成功', { exact: true })).toBeVisible()
    await page.getByTestId('script-summary-tab').click()
    await expect(summary).toContainText('共 3 条语句')

    await setSql(page, 'select * from order_item;\nselect * from mock_error;\nselect 1')
    await page.getByTestId('run-button').click()
    await expect(page.getByTestId('script-summary')).toContainText('第 2 条失败后已停止')
    await expect(page.getByTestId('script-summary')).toContainText('已执行的语句不会回滚')
    await expect(page.getByTestId('script-summary')).toContainText('未执行')
    await expect(page.getByRole('tab', { name: '第 3 条语句，未执行' })).toBeVisible()
  })

  test('runs only the selected statements and says so on the run button', async ({ page }) => {
    await login(page)
    await setSql(page, 'select * from order_item;\nselect * from mock_error')
    await page.locator('.monaco-editor').first().click()
    // Select the first line only, so the failing second statement stays out of the run.
    await page.keyboard.press('Control+Home')
    await page.keyboard.press('Shift+ArrowDown')
    await expect(page.getByTestId('run-button')).toContainText('运行选中')
    await page.getByTestId('run-button').click()
    await expect(page.getByText('9007199254740993')).toBeVisible()
    await expect(page.getByTestId('script-summary')).toHaveCount(0)
    await expect(page.getByText(/doesn't exist/)).toHaveCount(0)
  })
})
