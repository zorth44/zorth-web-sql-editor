# 应用与认证

## 代码与规格

- 前端：`web/src/router/`、`layouts/`、`auth/`、`stores/auth.ts`、`api/auth-client.ts`、`api/session.ts`、`env.ts`。
- 后端：`service/src/main/java/com/bocsoft/sqleditor/auth/` 与 `config/`。本地授权替身为 `auth-service/server.js`。
- 规格：[应用外壳](../../openspec/specs/frontend-application-shell/spec.md)、[前端认证](../../openspec/specs/frontend-auth-session/spec.md)、[后端认证](../../openspec/specs/backend-auth-session/spec.md)、[本地授权服务](../../openspec/specs/local-auth-service/spec.md)。

## 维护边界与验证

用户/产品由 Bearer 鉴权派生；登录回跳与 bridge origin 保持受控。退出时清理用户相关 store/query 缓存。运行时 `app-config.json` 与构建环境变量优先级见 [web README](../../web/README.md)，不把本地代理配置当成生产配置。

本地 auth-service 仅用于开发/E2E，Token 仅保存在进程内，不能作为生产身份服务；运行见 [auth-service README](../../auth-service/README.md)和[本地开发](../local-development.md)。

验证 auth、router、env 相关单测及 `web/e2e/phase-one.spec.ts`；后端鉴权改动验证上下文、产品隔离与内部 caller key 边界。
