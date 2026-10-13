# Agent 数据库 API

## 代码与规格

- 入口：`service/src/main/java/com/bocsoft/sqleditor/agentapi/`，复用 auth、datasource、metadata、execution 与 engine。
- 行为规格：[backend-agent-database-api](../../openspec/specs/backend-agent-database-api/spec.md)。
- 消费方接口参考：[Agent Database Tool API](../agent-database-api.md)，包含路径、字段、鉴权、限制、错误与黑盒测试说明。

## 维护边界与验证

内部 key 是附加 caller 身份，不能代替用户 Bearer 或提供裸 user/product。复用服务端权限和连接能力，不在 adapter 内另开 JDBC。database/schema 是同一个 NAMESPACE 的别名，冲突双值必须拒绝。

EXPLAIN 仅计划分析，禁止 ANALYZE；query 端点受只读、行数、字节数、超时及并发限制。优化器 Agent 不挂载 execute_query。关系是有证据的一跳外键边，不能从普通索引推测关系。

修改接口时同步消费者参考和规格，运行 Agent API 契约/集成测试；跨仓库消费者需要固定 provider 版本的黑盒验收，不能拿本仓库单测代替。真实鉴权边界记录见[历史联调](../archive/2026-10-13-sql-optimizer-live-acceptance.md)。
