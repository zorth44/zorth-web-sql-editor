import { describe, expect, it } from 'vitest'
import {
  SIDEBAR_DEFAULT_PX,
  SIDEBAR_MAX_PX,
  SIDEBAR_MIN_PX,
  fitSidebarWidth,
  pxToPanePercent,
} from '@/sql-editor/sidebar-width'

describe('sidebar width', () => {
  it('uses the spec default when there are no sources', () => {
    expect(fitSidebarWidth([])).toBe(SIDEBAR_DEFAULT_PX)
  })

  it('fits typical source labels tighter than a wide percentage pane', () => {
    const width = fitSidebarWidth([
      { name: '订单测试库', host: 'mysql-a.internal', port: 3306 },
      { name: '并发更新演示库', host: 'conflict.internal', port: 3306 },
    ])
    expect(width).toBeGreaterThanOrEqual(SIDEBAR_MIN_PX)
    expect(width).toBeLessThanOrEqual(320)
  })

  it('clamps very long labels to the spec maximum', () => {
    expect(
      fitSidebarWidth([
        {
          name: '非常非常非常长的数据源名称用于撑开侧边栏',
          host: 'very-long-database-hostname.internal.example.com',
          port: 3306,
        },
      ]),
    ).toBe(SIDEBAR_MAX_PX)
  })

  it('converts pixels to a pane percentage', () => {
    expect(pxToPanePercent(280, 1400)).toBe(20)
    expect(pxToPanePercent(280, 0)).toBe(0)
  })
})
