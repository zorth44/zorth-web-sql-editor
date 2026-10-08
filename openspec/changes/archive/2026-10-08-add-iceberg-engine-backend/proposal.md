## Why

现场第三种数据源标识是 **ICEBERG**（参考实现 `bddf-public-service` 的 `Constants.DBTYPE_ICEBERG = "9"`）。在参考代码里它和 `DBTYPE_HIVE_531 = "8"` 共用同一个 `TBDSSecurityHandle` 连接处理，只在 keytab 落盘目录（`iceberg` vs `hive531`）与业务语义上区分。因此它不是新连接机制，而是 change `add-kerberos-zk-discovery` 之上的**第三个引擎身份**：复用 Kerberos + ZooKeeper 连接器与 Hive 方言目录，保持目录/表单一致。

本 change 只做后端：注册 `ICEBERG` 引擎并交付目录、连接、校验与单测。前端卡片/图标在独立 change `add-iceberg-engine-frontend` 中交付，两者通过 `GET /api/v1/engines` 目录契约解耦。

## What Changes

- 注册 `ICEBERG`：`@Order(6)`、`family=HIVE_WIRE`、`editorLanguage=hive`、`identifierQuote` 反引号；目录/扫描**委托** `HiveEngineSupport`，连接复用 `KerberosHiveConnector`。
- 与 `HIVE_KERBEROS` 的差异仅在：`displayName`、keytab 子目录（`iceberg`）、默认端口/默认库；连接字段仍是 `environment`/`keytabFile`/`queueName`/可选 `defaultDatabase`。
- 不新增连接机制，不改 `EngineSupport`、`DynamicPoolManager` 或既有引擎。
- 不注册前端卡片/图标、不改前端目录（见 `add-iceberg-engine-frontend`）。

## Capabilities

### New Capabilities

- `backend-iceberg-engine`: `ICEBERG`（dbType 9）引擎注册、描述符、keytab 子目录选择与 Kerberos 短连接合同。

### Modified Capabilities

- `backend-engine-spi`: 注册表包含 `ICEBERG`；未注册引擎仍拒绝写入。
- `backend-engine-catalog`: `GET /api/v1/engines` 返回六项，含 `ICEBERG` 描述。
- `backend-data-source-management`: 允许创建/更新 `engine=ICEBERG`，字段要求与 `HIVE_KERBEROS` 相同。

## Impact

- 后端：`engine/iceberg`（`IcebergEngineSupport`，复用 `KerberosHiveConnector`）、`EngineId.ICEBERG`、`@Order(6)`、ArchUnit 禁止主干依赖 `engine.iceberg`。
- API：目录多一项 ICEBERG；CRUD/测试/元数据/执行路径不变。
- 前端：不在本 change 范围（见 `add-iceberg-engine-frontend`）。
- 部署：keytab 放 `keytab-base-path/iceberg/`；与 `HIVE_KERBEROS` 共用服务器配置与驱动。
