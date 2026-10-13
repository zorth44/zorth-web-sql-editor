# 代码与审查标准

## 实现与审查

- 修改前阅读对应[子系统入口](subsystems/README.md)与现行规格，沿用已有组件、API client、错误处理和引擎扩展机制。行为或接口变更同步规格、调用方与相关测试，不能用前端兼容分支掩盖契约差异。
- 前端沿用 Vue 3、TypeScript、Pinia、Vue Query 和现有 Monaco 封装；服务端保持 Java 8 字节码与 API 兼容，用 JDK 17 构建验证。依赖按 lockfile 安装，配置与格式沿用仓库工具。
- SQL 分句与安全判定复用 scanner/evaluator，不用分号 split 或正则替代 SQL 解析。数据库引擎差异通过 EngineSupport 扩展，不能假设所有引擎都是 MySQL。
- 用户与产品身份由服务端鉴权派生；界面隐藏入口不能替代服务端授权。保留数据源可见性、网络访问边界、只读限制、并发配额与错误脱敏。
- 不将 Token、密码、内部密钥放入 URL、日志、业务 DTO 或提交；SQL 和完整报告不进入常规日志。外部文本按安全文本或已有受控渲染器呈现，不直接注入 HTML。
- 审查重点为行为正确性、权限隔离、状态与并发、资源释放、失败恢复及有效测试覆盖；报告明确文件位置、触发条件和影响。不要通过删除测试、放宽断言或关闭保护掩盖失败。

## 验证命令

前端改动在 `web/` 运行：

```bash
pnpm install --frozen-lockfile
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

再运行受影响的 Playwright 场景；优化器为 `pnpm e2e e2e/sql-optimizer.spec.ts`，涉及 Copilot 时补充 `e2e/copilot.spec.ts`。完整场景和环境说明见 [web README](../web/README.md)。生产禁止启用 Mock，真实联调显式设置 `VITE_ENABLE_API_MOCK=false`。

后端改动在 `service/` 使用 JDK 17：

```bash
mvn test
mvn verify
```

数据库集成测试使用 Testcontainers 独立容器。Docker 29 协商失败时按 [service README](../service/README.md) 加 `-Dapi.version=1.44`；容器测试跳过须报告，不能视作集成验收通过。部署、凭据轮换和厂商依赖还需核对 `service/docs/` 对应操作说明。

测试优先覆盖实际行为、边界与回归，纯文档调整无需新增实现镜像测试。失败需核对基线与环境，报告实际结果，不能以历史记录替代当前验证。
