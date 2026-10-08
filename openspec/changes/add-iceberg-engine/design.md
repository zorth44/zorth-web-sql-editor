## Context

`add-kerberos-zk-discovery` 落地了 Kerberos + ZooKeeper 服务发现连接机制：`EngineSupport` 的连接获取模型（`requiresHostResolution`/`usesPooledConnections`/`openConnection`）、`KerberosHiveConnector`（单锁登录 + 全局状态还原 + CIDR 校验）、服务端 `sql-editor.kerberos` 环境配置、Flyway `V7` 的 Kerberos 列，以及第一个 Kerberos 引擎 `HIVE_KERBEROS`（dbType 8）。

参考实现里 `DBTYPE_ICEBERG = "9"` 与 `DBTYPE_HIVE_531 = "8"` 共用同一连接与浏览处理，只在 keytab 目录与业务语义上区分。

约束不变：Java 8、Spring Boot 2.7、单实例；池化引擎行为不变。

## Goals / Non-Goals

**Goals:**

- 注册 `ICEBERG`（dbType 9）：连接、测试、资源树、执行、导出都走该实现，复用 Kerberos 连接器与 Hive 目录。
- 与 `HIVE_KERBEROS` 只差展示名、keytab 子目录、默认端口/默认库。
- 池化引擎（MYSQL/PG/GBASE_8A/HIVE）行为不变。

**Non-Goals:**

- 不引入新的连接机制、不新增 Kerberos 配置项（复用 `add-kerberos-zk-discovery`）。
- 不改 `KerberosHiveConnector` 的全局状态策略。
- 不抽 Hive 族公共基类（只委托）。

## Decisions

### 1. ICEBERG 是挂在 Kerberos 机制上的第三个引擎身份

| 概念 | HIVE_KERBEROS (dbType 8) | ICEBERG (dbType 9) |
| --- | --- | --- |
| `engine` id | HIVE_KERBEROS | ICEBERG |
| `family` | HIVE_WIRE | HIVE_WIRE |
| 连接机制 | Kerberos + ZooKeeper | 同一 `KerberosHiveConnector` |
| 连接字段 | environment / keytabFile / queueName / defaultDatabase | 同左 |
| keytab 子目录 | `hive531` | `iceberg` |
| 目录/扫描 | Hive 方言（委托 `HiveEngineSupport`） | 同左 |
| 展示名/默认端口 | Kerberos Hive / 10000 | Iceberg / 10000 |

`IcebergEngineSupport` `@Order(6)` 委托 `HiveEngineSupport` 的目录/扫描/explain，连接复用 `KerberosHiveConnector`；`requiresHostResolution=false`、`usesPooledConnections=false`，与 `HIVE_KERBEROS` 一致。

### 2. keytab 子目录由引擎决定

连接器解析 keytab 路径时按引擎给出子目录名：`HIVE_KERBEROS → hive531`、`ICEBERG → iceberg`。这样运维的落盘布局与参考实现一致，且数据源表无需存子目录。

### 3. ArchUnit 与目录

新增 `orchestratorsDoNotDependOnIcebergEngine`。目录稳定为 MYSQL、POSTGRESQL、GBASE_8A、HIVE、HIVE_KERBEROS、ICEBERG。

### 4. 验证策略

本机连不到集群。集成测试只验证目录第 6 项与 `engine=ICEBERG` 创建持久化；keytab 子目录选择、URL 组装（含 queue）、失败分类用单元测试钉死（复用 `HIVE_KERBEROS` 的连接器测试）。

## Risks / Trade-offs

- [与 HIVE_KERBEROS 高度相似导致重复] → 只委托 + 复用连接器，杜绝复制粘贴；差异集中在展示名、子目录、默认端口。
- [目录顺序带偏既有引擎回归] → `@Order(6)`；省略 engine 仍默认 MYSQL。
- [误加新的连接机制] → 任务明确禁止；本变更只注册引擎。

## Migration Plan

1. 依赖 `add-kerberos-zk-discovery` 已发布（连接机制与 `V7`）。
2. 运维在 `keytab-base-path/iceberg/` 放入 keytab 文件，环境配置复用已有 `sql-editor.kerberos`。
3. 前后端同发：目录第 6 项、Iceberg 卡片。
4. 回滚：回退应用版本；已存 `engine=ICEBERG` 的行在旧版本 `ENGINE_NOT_SUPPORTED`（fail-closed）。
