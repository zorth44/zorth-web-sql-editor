# SQL 优化器生产待验收项

更新：2026-10-13。本地开发与关闭 Mock 的真实服务联调已验收，生产未启用。以下是当前生产接入的维护清单，不以历史任务归档表示完成；仅在取得对应证据后更新状态。

| 项 | 状态 | 已有证据与所需输入 |
| --- | --- | --- |
| 生产 CurrentUserPort | 未验收 | 本地 dev 开发头身份已证明 Bearer 传播和数据库回调；需正式身份适配器的部署位置及生产验证，不能把开发头当正式身份。 |
| 凭证托管与保留 | 未验收 | 本地独立密钥、到期与清理已验收；需短期 Token 加密落库政策批准、真实 tokenExpiresAt、独立安全密钥来源/版本/轮换流程和任务/报告 90 天保留确认。 |
| Agent 持久会话保留 | 未验收 | releaseSession 不等于删除持久状态；需平台侧会话状态保留与清理策略。 |
| 生产配置与容量 | 未验收 | 需正式 Agent 地址/账户、用户与产品白名单、内部 caller key 配对、容量预算与上线审批；本地联调结果不替代生产验证。 |

功能默认关闭。enabled、integration-ready、明确 user/product allowlist 和所需独立密钥应按正式配置验证；未满足条件不授予 SQL_OPTIMIZE，不通过绕过认证接入。Agent 仅启用所需只读取证工具，优化器不需要 execute_query。

凭证长期委派、退出后继续或排队数小时不是当前交付承诺；若新增此范围，需要授权服务与内部 API 的独立设计，不能用共享服务账号代替最终用户身份。

维护入口见[SQL 优化器](subsystems/sql-optimizer.md)，部署与轮换见 [service/docs](../service/docs/deployment.md)和[凭据轮换](../service/docs/credential-rotation.md)。历史证据见[真实联调归档](archive/2026-10-13-sql-optimizer-live-acceptance.md)与[交付归档](archive/2026-10-13-sql-optimizer-delivery.md)。
