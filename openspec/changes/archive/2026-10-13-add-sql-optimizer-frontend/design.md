## Context

Vue 3、Vue Query、Pinia、Monaco 和 sql-client 已提供登录与数据源基础。共享契约 v1 为唯一 DTO 基线；仅前端文件归本 change。

## Goals / Non-Goals

Goals: 完成 FE-01～FE-05 的可运行 Mock 扫描闭环、幂等恢复、查询隔离、安全报告与验收。
Non-Goals: FE-06 真实联调、生产授权、后台执行、批量扫描、SQL 执行/改写。

## Decisions

- 四个接口通过 sql-client 使用 sqlApiBase，复用 401 teardown 与安全回跳，不直连 Agent。
- 创建/重扫以一个不可变 payload 与 UUID 为一次操作，网络不确定时显式恢复；输入变化才创建新的操作，mutation 不自动重试。
- Vue Query key 包含用户/产品与筛选/游标/任务 ID，退出使用现有 clear；活跃任务 5 秒查询，后台与卸载暂停，聚焦刷新。
- 游标栈只记录不透明 token，不依赖数字总页数。报告进行运行时形状检查，所有内容用 Vue 文本绑定，不解析 HTML。
- MSW 服务状态与页面生命周期分离；开发刷新夹具采用仅 Mock 的 sessionStorage 状态，不保存生产草稿或凭证。真实持久化仍归后端。
- 可选编辑器送去优化器暂不增加，避免扩大编辑器交互；独立表单复用 Monaco 与既有 SQL scanner。

## Risks / Trade-offs

- Mock 不能验证 Worker 或真实能力授予 → FE-06 明确留待后端联调。
- 基线 Copilot v-html lint 警告 → 在前端归属内解决安全展示门禁，保留 Markdown 与现有测试。
- 响应丢失仍可能已创建 → 保存操作 key 与原输入，不将其显示为任务 FAILED。

## Migration Plan

本地阶段提交及门禁验收；后端 capability 未开启时隐藏入口。生产构建继续禁止 Mock。回滚本侧提交即可。

## Open Questions

本地实施无阻塞问题。真实身份、API、凭证政策与联合验收为外部依赖。
