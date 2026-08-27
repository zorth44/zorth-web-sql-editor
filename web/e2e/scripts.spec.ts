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

async function openScripts(page: Page): Promise<void> {
  if ((await page.getByTestId('script-panel').count()) === 0) {
    await page.getByTestId('scripts-rail').click()
  }
  await expect(page.getByTestId('script-panel')).toBeVisible()
}

test.describe('sql script persistence', () => {
  test('saves, renames, reopens after reload, allows duplicate names, and deletes', async ({
    page,
  }) => {
    await login(page)
    await setSql(page, 'select 1 from order_item')
    await page.getByTestId('save-script').click()
    await page.getByTestId('save-script-name').fill('月报')
    await page
      .getByRole('dialog', { name: '保存脚本' })
      .getByRole('button', { name: '保存' })
      .click()
    await expect(page.getByRole('tab', { selected: true })).toContainText('月报')

    await openScripts(page)
    const panel = page.getByTestId('script-panel')
    await expect(panel).toContainText('月报')
    await expect(panel).toContainText('select 1 from order_item')

    await panel.getByLabel('重命名 月报').click()
    await page.getByTestId('rename-script-name').fill('月报对账')
    await page
      .getByRole('dialog', { name: '重命名脚本' })
      .getByRole('button', { name: '重命名' })
      .click()
    await expect(page.getByRole('tab', { selected: true })).toContainText('月报对账')

    await page.reload()
    await expect(page).toHaveURL(/\/sql-editor/)
    await openScripts(page)
    await page.getByLabel('打开脚本 月报对账').click()
    await expect(page.getByRole('tab', { selected: true })).toContainText('月报对账')
    await expect(page.getByRole('tab', { selected: true })).toContainText('订单测试库')

    await page.getByTestId('save-script-as').click()
    await page.getByTestId('save-script-name').fill('月报对账')
    await page
      .getByRole('dialog', { name: '保存脚本' })
      .getByRole('button', { name: '保存' })
      .click()
    await openScripts(page)
    await expect(page.getByTestId('script-panel').getByText('月报对账')).toHaveCount(2)

    await page.getByTitle('执行历史').click()
    await expect(page.getByText('暂无执行历史')).toBeVisible()
    await expect(page.getByTestId('script-panel')).toHaveCount(0)

    await openScripts(page)
    await page.getByLabel('删除 月报对账').first().click()
    await page.getByRole('button', { name: '删除', exact: true }).click()
    await expect(page.getByTestId('script-panel').getByText('月报对账')).toHaveCount(1)
  })
})
