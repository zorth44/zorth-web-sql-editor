# 第二阶段本地完整链路

本地链路为 `Vue (5173) → auth-service (8090) / SQL service (8080) → MySQL`。临时授权服务只用于开发；生产必须配置真实 `bddf-authorization-service`。

## 1. 准备 MySQL

创建一个供 SQL service 保存配置/历史的 `sql_editor` 数据库，并准备一个可作为编辑器目标的数据源。两者可以是同一 MySQL 实例。元数据库用户需要 `sql_editor` 的 DDL/DML 权限；目标账号权限决定编辑器能执行的 SQL。

## 2. 启动临时授权服务

```bash
cd auth-service
AUTH_SERVICE_INTERNAL_KEY=local-sql-editor-key node server.js
```

## 3. 启动 SQL service

以下密钥仅是本地示例。目标库位于本机时，需要显式允许 loopback，并从 denied CIDR 中移除 loopback；生产不可照抄。

```bash
cd service
SQL_EDITOR_METADATA_URL='jdbc:mysql://127.0.0.1:3306/sql_editor?serverTimezone=UTC' \
SQL_EDITOR_METADATA_USERNAME=root \
SQL_EDITOR_METADATA_PASSWORD='<mysql-password>' \
SQL_EDITOR_AUTH_CONTEXT_URL='http://127.0.0.1:8090/internal/api/v1/auth/context' \
SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY='local-sql-editor-key' \
SQL_EDITOR_CREDENTIAL_CURRENT_KEY='MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=' \
SQL_EDITOR_CURSOR_SIGNING_KEY='YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=' \
SQL_EDITOR_NETWORK_ALLOWED_CIDRS='127.0.0.0/8' \
SQL_EDITOR_NETWORK_DENIED_CIDRS='169.254.0.0/16,::1/128,fe80::/10' \
mvn spring-boot:run
```

## 4. 启动 Web

```bash
cd web
VITE_SQL_API_BASE='/sql-api' \
VITE_AUTH_API_BASE='/auth-api' \
VITE_AI_API_BASE='/ai-api' \
VITE_DEV_SQL_PROXY_TARGET='http://127.0.0.1:8080' \
VITE_DEV_AUTH_PROXY_TARGET='http://127.0.0.1:8090' \
VITE_DEV_AI_PROXY_TARGET='http://127.0.0.1:8081' \
VITE_LEGACY_PORTAL_URL='http://localhost:8080/account/bind' \
VITE_AUTH_BRIDGE_ALLOWED_ORIGINS='http://localhost:8080' \
VITE_ENABLE_API_MOCK=false \
pnpm dev
```

打开 `http://localhost:5173/login`，输入任意非空用户名和密码。登录后进入 SQL 编辑器；先在“数据源管理”中新增目标 MySQL，再回到编辑器选择数据源和数据库。

若要连接真实 GBase 8a，先按 `service/third-party/gbase/README.md` 放入官方 `gbase-connector-java.jar`，再重新启动 SQL service。未放入时 MYSQL / PostgreSQL 不受影响，GBase 8a 测试连接会失败。

Vite 开发代理会把 `/sql-api`、`/auth-api` 和 `/ai-api` 分别转发到本地 SQL、授权和 AI 服务，从而保持浏览器同源。生产不得配置 `VITE_DEV_*_PROXY_TARGET`，应由真实网关提供同源路径。

## 5. 对接 AI Agent Platform：`bddf-agentscope`

编辑器 Copilot 的模型、Tool、只读试跑由 sibling 仓库 **`bddf-agentscope`**（`/Users/zorth/Code/ai/bddf-agentscope`）实现，已联调验证通过。

SQL service 占 `8080` 时，把 Agent Platform 放到 `8081`（其 `dev` profile 默认监听 `8081`），并设置 `VITE_AI_API_BASE=/ai-api` 与 `VITE_DEV_AI_PROXY_TARGET=http://127.0.0.1:8081`。Agent Platform 需开启数据源集成，使其能回调本仓库 SQL service 的 `/internal/api/v1/agent/**`：

```bash
export DATASOURCE_SERVICE_ENABLED=true
export DATASOURCE_SERVICE_BASE_URL=http://127.0.0.1:8080
```

编辑器右侧 Copilot 调用 `POST /api/v1/ai/agent/stream`（SSE：`start` / `delta` / `tool` / `completed` / `error`），Bearer Token 与 SQL 请求相同。旧的同步 `POST /api/v1/ai/agent` 仅作 404 回退。启动方式、工具集、提示词与对话历史契约见该仓库 `README.md`、`docs/sql-editor-chat-agent.md`；本仓库侧的面板行为契约见 `openspec/specs/frontend-sql-copilot/spec.md`，SSE 客户端实现见 `web/src/api/ai-agent.ts`。

## 6. SQL 优化器真实链路

SQL 优化器默认关闭。开启需要同时满足：`SQL_EDITOR_OPTIMIZATION_ENABLED=true`、`SQL_EDITOR_OPTIMIZATION_INTEGRATION_READY=true`、显式 user/product 白名单、独立的 32 字节 Base64 凭证密钥与 ≥32 字节游标密钥；本地还需 `SQL_EDITOR_OPTIMIZATION_DEVELOPMENT_USER_HEADER=true` 与 `dev`/`test`/`local-optimization` 之一的活动 profile。缺任一项服务启动即失败。

本地一键拉起整套真实链路（auth-service + SQL service + Agent Platform + 关闭 Mock 的 Web）、跑完验收场景并留证的方式见 [`tools/optimizer-live/README.md`](../tools/optimizer-live/README.md)，验收范围与结果见 [`docs/sql-optimizer-live-acceptance.md`](./sql-optimizer-live-acceptance.md)。该链路里 Worker 用当前任务凭证调用 Agent Platform 的 `POST /api/v1/agents/sql-lite-optimizer/chat`，Agent 再带同一 Bearer 加配对的 `X-Internal-Service-Key` 回调本服务 `/internal/api/v1/agent/**` 取计划与表结构证据；Agent 侧只挂只读工具，不执行原 SQL。

功能未开启时不要授予 `SQL_OPTIMIZE`：能力由后端白名单决定，前端隐藏入口不替代后端校验。
