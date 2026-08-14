import { expect, test, type Page } from '@playwright/test'

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('normal')
  await page.getByLabel('密码').fill('ldap-e2e-secret')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/sql-editor/)
  await expect(page.getByLabel('数据源')).toHaveValue('ds-orders-a')
  await expect(page.getByTestId('navigator-source-ds-orders-a')).toBeVisible()
  await expect(page.getByTestId('navigator-source-ds-orders-b')).toBeVisible()
  await expect(page.getByTestId('navigator-database-ds-orders-b-orders')).toHaveCount(0)
  await page.getByRole('button', { name: 'orders', exact: true }).click()
}

async function setSql(page: Page, sql: string): Promise<void> {
  const editor = page.locator('.monaco-editor').first()
  await editor.click()
  await page.keyboard.press('Control+A')
  await page.keyboard.insertText(sql)
  await page.waitForTimeout(120)
}

test.describe('phase-two SQL editor', () => {
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
    await page.getByRole('button', { name: 'order_item', exact: true }).click()
    await expect(page.getByText('amount', { exact: true })).toBeVisible()

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
    await expect(page.getByRole('button', { name: /History / })).toBeVisible()

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
})
