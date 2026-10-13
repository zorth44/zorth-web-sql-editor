# 数据源、引擎与元数据

## 代码与规格

- 前端：`web/src/data-sources/`、`components/DataSourceForm.vue`、数据源 pages、`api/data-sources.ts`、`api/engines.ts`、`api/metadata.ts`、资源树与表查看器组件。
- 后端：`service/src/main/java/com/bocsoft/sqleditor/datasource/`、`engine/`、`metadata/`（连接安全位于 `datasource/connection/`），迁移与 mapper 位于 `service/src/main/resources/`。
- 规格：[产品行为](../../openspec/specs/data-source-management/spec.md)、[后端管理](../../openspec/specs/backend-data-source-management/spec.md)、[引擎目录](../../openspec/specs/backend-engine-catalog/spec.md)、[Engine SPI](../../openspec/specs/backend-engine-spi/spec.md)、[连接安全](../../openspec/specs/backend-connection-security/spec.md)。各引擎专项规格见 `openspec/specs/backend-*-engine/`，Kerberos 连接另见[连接规格](../../openspec/specs/backend-kerberos-zk-connection/spec.md)。

## 维护边界与验证

数据源按产品权限过滤，凭据加密保存，响应不能泄露密码/JDBC 敏感属性；保留网络 CIDR/地址校验、连接池生命周期与并发边界。NAMESPACE 由引擎解释，不自创 PostgreSQL 的 database.schema 拼接协议。

动态连接表单以引擎目录为准；新增引擎同步描述、能力、连接/元数据实现与实际驱动验证。缺失元数据须明确表达 UNAVAILABLE/TRUNCATED，不能把不支持伪装成完整空集合。

验证数据源/目录/元数据相关单测、`web/e2e/phase-one.spec.ts` 和受影响引擎的容器/真实驱动测试。厂商 JAR 交付见[依赖说明](../../service/docs/vendor-dependencies.md)，凭据维护见[轮换说明](../../service/docs/credential-rotation.md)。
