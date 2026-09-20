# Zorth SQL Editor 前端

阶段一前端提供 LDAP 登录、Session 验证和当前产品下的 MySQL 数据源管理。SQL 服务尚未实现时，可以使用与生产契约共用类型的 MSW 模拟接口。

## 本地启动

要求 Node.js 20.19+ 和 pnpm 10。首次运行：

```bash
pnpm install
cp .env.example .env.local
pnpm dev
```

`.env.example` 默认建议开启 `VITE_ENABLE_API_MOCK=true`。Mock 仅在 development 或 test mode 启动；production mode 配为 `true` 会直接失败。Mock 登录可使用任意非空账号，特殊用户名为：

- `multi`：多账号选择。
- `bind`：需要前往老系统绑定。
- `failed`：授权业务失败。

Mock 数据包含重复名称、连接失败、不可见 404、版本冲突和使用中无法删除等确定性场景。数据源 Host 含 `fail` 或 `timeout` 时，测试连接返回对应失败结果。

## 环境变量

开发、测试和打包兜底仍可读 `VITE_*`。生产部署请用 `app-config.json`，由 Nginx 按环境挂载，不必为每个环境重新打包。

| 变量                               | 说明                                              |
| ---------------------------------- | ------------------------------------------------- |
| `VITE_SQL_API_BASE`                | SQL 服务 base URL；同源部署可用 `/sql` 等相对路径 |
| `VITE_AUTH_API_BASE`               | 现有授权服务 base URL                             |
| `VITE_AI_API_BASE`                 | AI 服务 base URL；同源部署可用 `/ai-api`          |
| `VITE_AUTH_PRODUCT_TYPE`           | LDAP 产品类型，默认 `chinaBank`                   |
| `VITE_AUTH_BRIDGE_ALLOWED_ORIGINS` | 老系统 bridge sender Origin 白名单，逗号分隔      |
| `VITE_LEGACY_PORTAL_URL`           | `needBind` 时前往老系统的地址                     |
| `VITE_ENABLE_API_MOCK`             | 仅开发/测试允许为 `true`                          |

`VITE_DEV_*_PROXY_TARGET` 只用于本地 Vite 代理，不会打进生产包，也不能写进 `app-config.json`。生产禁止开启 Mock，运行时配置也无法打开它。

## 运行时配置

前端每次启动读取 `${BASE_URL}app-config.json`（默认构建会复制 `public/app-config.json`）。JSON 覆盖打包兜底值，改文件后刷新即可，无需重编。

```json
{
  "sqlApiBase": "/sql",
  "authApiBase": "/auth",
  "aiApiBase": "/ai",
  "authProductType": "chinaBank",
  "legacyPortalUrl": "http://81.88.161.186:8505/account/bind",
  "authBridgeAllowedOrigins": ["http://81.88.161.187:18088"]
}
```

- `sqlApiBase` / `authApiBase` / `aiApiBase` 可以是同源绝对路径，对应 Nginx 的 `/sql/`、`/auth/`、`/ai/` 反代。
- `legacyPortalUrl` 是账号绑定页，可以和门户前端不在同一个 Origin。
- `authBridgeAllowedOrigins` 必须是打开 SQL Editor 的那个前端 Origin（协议、主机、端口，不含路径）。免密跳转的 opener 是门户，不是授权服务。
- 读取失败或 5 秒超时则回退到打包时的 `VITE_*`；生产环境回退后仍缺必填项会停止启动。

建议 Nginx 用外部文件，避免发布 dist 覆盖配置：

```nginx
location = /app-config.json {
    alias /etc/nginx/sql-editor/app-config.json;
    default_type application/json;
    add_header Cache-Control "no-store" always;
    expires off;
}
```

该 `location =` 必须保留，不能让 `/` 的 SPA 回退或 `/auth/` 反代吃掉这个路径。修改 JSON 后刷新浏览器即可，不必 reload Nginx。

## 验证命令

```bash
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm exec playwright install chromium
pnpm e2e
pnpm build
```

## 切换真实后端

1. 按 `docs/backend-development-spec.md` 实现 Session 和数据源 API。
2. 将两个 API base 指向集成环境，并把 `VITE_ENABLE_API_MOCK` 设为 `false`。
3. 检查网关 CORS、Bearer Token 和 `X-Request-Id` 透传。
4. 用同一套 Playwright 流程验证真实接口，尤其是 404 隐藏产品边界、乐观锁和空密码复用。

`/auth/bridge` 只实现接收方。老系统仍需按约定通过新窗口 `postMessage` 发送 `{ type: "ZORTH_SQL_AUTH_TOKEN", version: 1, token }`，并把新站点 Origin 作为明确的 `targetOrigin`。

## 上线阻塞项

当前授权服务部分登录分支可能序列化 `ldapUser.pwd`。本前端在 API 边界立即丢弃未知字段，也不会缓存、渲染或记录密码，但无法消除密码已进入网络响应的风险。生产联调前，授权服务必须禁止序列化该字段并改用最小响应 DTO。
