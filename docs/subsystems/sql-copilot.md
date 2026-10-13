# SQL Copilot

## 代码与规格

- 前端：`web/src/components/copilot/`、`stores/copilot.ts`、`api/ai-agent.ts`、`sql-editor/copilot-*`、`sql-editor/sse.ts`。
- 规格：[SQL Copilot](../../openspec/specs/frontend-sql-copilot/spec.md)。AI Agent Platform 为外部服务，接入见[本地开发](../local-development.md)。

## 维护边界与验证

对话与工具事件沿用现有 AI API/SSE 协议；维护会话恢复、身份隔离、取消与错误显示。上下文按现有受控范围传递，不将凭据加入提示词。Markdown 走已有受控 Vue 渲染器，不能直接注入上游 HTML。

Copilot 对话不是优化器持久任务；不要把文本事件拼成优化器结构化报告。验证 Copilot store、API、SSE、Markdown/工具展示测试与 `pnpm e2e e2e/copilot.spec.ts`（在 web/）。
