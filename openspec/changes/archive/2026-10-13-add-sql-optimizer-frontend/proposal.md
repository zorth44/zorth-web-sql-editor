## Why

用户需要提交单条 SQL 后离开页面，并能回来查看扫描状态、证据与建议。前端按共享契约 v1 独立实现可验收的 MSW 闭环，为后端真实联调提供稳定界面。

## What Changes

- 增加 SQL_OPTIMIZE 能力控制的导航、创建、游标列表和任务详情。
- 复用 sql-client、Monaco、数据源命名空间接口与登录回跳。
- 提供安全文本报告、证据缺口、终态重扫和响应丢失后的幂等恢复。
- 增加契约 Mock、单元/组件测试和浏览器验收。

## Capabilities

### New Capabilities
- `sql-optimizer-ui`: 前端单 SQL 异步扫描、查询、报告和重扫交互。

### Modified Capabilities

无。

## Impact

仅修改 web/**、本 change 与前端实施方案。共享契约、后端、根配置不变，无新增生产依赖。FE-06 真实联调在本地交付之外，Mock 不能证明 Worker 或真实授权链路。
