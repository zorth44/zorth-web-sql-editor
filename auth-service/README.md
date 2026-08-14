# Temporary local auth service

这是仅供本地开发和 E2E 的零依赖授权假服务，不得部署到生产。Node.js 20+ 下运行：

```bash
cd auth-service
AUTH_SERVICE_INTERNAL_KEY=local-sql-editor-key node server.js
```

它实现 `/ldap/login`、`/logout`、`/internal/api/v1/auth/context` 和 `/health`。登录接受任意非空用户名以及前端生成的兼容密码串（长度须大于 12），Token 仅保存在当前进程内；重启即失效。

所有配置都必须显式传入真实环境。SQL service 的生产配置不会自动发现或回退到该服务。
