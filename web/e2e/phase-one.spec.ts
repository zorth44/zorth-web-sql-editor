import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, remember = false): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('normal')
  await page.getByLabel('密码').fill('ldap-e2e-secret')
  if (remember) await page.getByLabel('在此设备上记住我').check()
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/sql-editor(?:\?|$)/)
  await expect(page.getByTestId('welcome-start')).toBeVisible()
  await page.goto('/data-sources')
  await expect(page.getByRole('heading', { name: '数据源' })).toBeVisible()
}

test.describe('phase-one data-source management', () => {
  test('login, exclusive Token persistence, list, search, cursor navigation, saved test, and logout', async ({
    page,
  }) => {
    await login(page)
    const persistence = await page.evaluate(() => ({
      local: JSON.stringify(localStorage),
      session: JSON.stringify(sessionStorage),
    }))
    expect(persistence.local).not.toContain('mock-token')
    expect(persistence.session).toContain('mock-token')
    expect(`${persistence.local}${persistence.session}`).not.toContain('ldap-e2e-secret')

    await expect(page.getByText('订单测试库', { exact: true })).toHaveCount(2)
    await page.getByLabel('每页').selectOption('2')
    await page.getByRole('button', { name: '下一页' }).click()
    await expect(page.getByText('第 2 页')).toBeVisible()
    await page.getByRole('button', { name: '上一页' }).click()
    await expect(page.getByText('第 1 页')).toBeVisible()

    await page.getByPlaceholder('搜索名称或 Host').fill('mysql-b')
    await expect(page.getByTestId('data-source-ds-orders-b')).toBeVisible()
    await expect(page.getByTestId('data-source-ds-orders-a')).toHaveCount(0)
    await page.getByPlaceholder('搜索名称或 Host').fill('')
    await expect(page.getByTestId('data-source-ds-orders-a')).toBeVisible()

    await page.getByRole('button', { name: '测试 订单测试库' }).first().click()
    await expect(page.getByText(/连接成功.*MySQL 8\.0\.36/)).toBeVisible()
    await page.getByRole('button', { name: '退出' }).click()
    await expect(page).toHaveURL(/\/login$/)
    expect(
      await page.evaluate(() => JSON.stringify({ local: localStorage, session: sessionStorage })),
    ).not.toContain('mock-token')
  })

  test('create/test, detail-driven edit with saved password reuse, and secret cleanup', async ({
    page,
  }) => {
    await login(page, true)
    expect(await page.evaluate(() => localStorage.getItem('zorth.sql.token'))).toBe('mock-token')
    expect(await page.evaluate(() => sessionStorage.getItem('zorth.sql.token'))).toBeNull()

    await page.getByRole('link', { name: '新增数据源' }).first().click()
    await page.getByLabel('数据源名称 *').fill('订单测试库')
    await page.getByLabel('Host *').fill('new-mysql.internal')
    await page.getByLabel('用户名 *').fill('new_user')
    await page.getByLabel('密码 *').fill('db-e2e-secret')
    await page.getByLabel('默认数据库').fill('new_orders')
    await page.getByRole('button', { name: '测试连接' }).click()
    await expect(page.getByText('连接成功', { exact: true })).toBeVisible()
    await expect(page.getByLabel('密码 *')).toHaveValue('')
    await page.getByLabel('密码 *').fill('db-e2e-secret')
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page).toHaveURL(/\/data-sources$/)
    await expect(page.getByText('new-mysql.internal:3306')).toBeVisible()

    const browserState = await page.evaluate(() =>
      JSON.stringify({ local: localStorage, session: sessionStorage }),
    )
    expect(browserState).not.toContain('db-e2e-secret')
    expect(browserState).not.toContain('ldap-e2e-secret')

    await page.goto('/data-sources/ds-orders-a/edit')
    await expect(page.getByLabel('Host *')).toHaveValue('mysql-a.internal')
    await expect(page.getByLabel('密码')).toHaveValue('')
    await expect(page.getByText('留空将保留现有密码')).toBeVisible()
    await page.getByLabel('描述').fill('详情接口回填后更新')
    await page.getByRole('button', { name: '测试连接' }).click()
    await expect(page.getByText('连接成功', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page).toHaveURL(/\/data-sources$/)
  })

  test('version conflict, typed duplicate-name deletion, in-use recovery, and invisible 404', async ({
    page,
  }) => {
    await login(page)
    await page.goto('/data-sources/ds-conflict/edit')
    await expect(page.getByLabel('Host *')).toHaveValue('conflict.internal')
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('此数据源已被其他用户更新')).toBeVisible()
    await expect(page.getByRole('button', { name: '重新加载当前详情' })).toBeVisible()

    await page.goto('/data-sources')
    const rowA = page.getByTestId('data-source-ds-orders-a')
    await rowA.getByRole('button', { name: '删除 订单测试库' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('输入完整名称以确认')).toBeFocused()
    await expect(dialog.getByRole('button', { name: '确认' })).toBeDisabled()
    await dialog.getByLabel('输入完整名称以确认').fill('订单测试库')
    await dialog.getByRole('button', { name: '确认' }).click()
    await expect(page.getByTestId('data-source-ds-orders-a')).toHaveCount(0)
    await expect(page.getByTestId('data-source-ds-orders-b')).toBeVisible()

    const inUseRow = page.getByTestId('data-source-ds-in-use')
    await inUseRow.getByRole('button', { name: '删除 报表生产库' }).click()
    await dialog.getByLabel('输入完整名称以确认').fill('报表生产库')
    await dialog.getByRole('button', { name: '确认' }).click()
    await expect(dialog.getByText('2 个任务正在执行')).toBeVisible()
    await dialog.getByRole('button', { name: '取消' }).click()
    await expect(inUseRow).toBeVisible()

    await page.goto('/data-sources/invisible/edit')
    await expect(page.getByText('数据源不存在或已不再可见。')).toBeVisible()
  })
})
