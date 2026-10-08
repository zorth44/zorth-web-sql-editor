## Why

后端 change `add-iceberg-engine-backend` 会注册 `ICEBERG` 引擎，`GET /api/v1/engines` 目录随即多出第六项 Iceberg 描述符：`family=HIVE_WIRE`、`editorLanguage=hive`、字段 `environment`/`keytabFile`/`queueName`/`defaultDatabase`，与 `HIVE_KERBEROS` 同形。前端表单、资源树、编辑器语言都由目录描述符驱动，Kerberos 字段控件与 `hive` 编辑器语言已由 `add-kerberos-zk-discovery-frontend`、`add-hive-engine-frontend` 支持，因此前端只需让目录可被消费：MSW 目录加 ICEBERG、补类型卡片图标。

前端测试跑在 MSW 上，不依赖后端实现，因而本 change 可以先于/并行于后端落地，与后端通过 `GET /api/v1/engines` 目录契约解耦。本 change 只做前端。

## What Changes

- MSW 目录（`web/src/mocks/engines.ts`）新增 Iceberg 描述符：`id=ICEBERG`、`displayName=Iceberg`、`family=HIVE_WIRE`、`defaultPort=10000`、`editorLanguage=hive`、反引号、NAMESPACE 文案「数据库」、`environment`（SELECT, required）、`keytabFile`（TEXT, required）、`queueName`（TEXT, optional）、可选 `defaultDatabase`；不含 `host`/`port`/`username`/`password`/`sslMode`。
- `web/src/mocks/handlers.ts` 允许 `engine=ICEBERG` 免密码测试，与 `HIVE_KERBEROS` 相同。
- `web/src/components/EngineTypeIcon.vue` 增加 `ICEBERG` 卡片/树图标分支与资源。
- 表单与资源树不写 Iceberg 专用分支：Kerberos 字段控件、NAMESPACE 文案、`hive` 语言均由描述符与既有实现覆盖。
- 不注册后端引擎、不连真实 Iceberg、不做 E2E/集成测试。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `data-source-management`: 类型可选 Iceberg；表单按目录渲染 Kerberos 字段。

## Impact

- 前端：`web/src/mocks/engines.ts`、`web/src/mocks/handlers.ts`、`web/src/components/EngineTypeIcon.vue` + `web/src/assets/engines/iceberg*.svg` 及其对应测试。
- 测试：先写前端单元测试（MSW 目录、表单 Iceberg 卡片、图标），再改实现；不做 E2E/集成测试。
- 依赖：Kerberos 字段渲染与 `hive` 编辑器语言由 `add-kerberos-zk-discovery-frontend`/`add-hive-engine-frontend` 提供；后端目录由 `add-iceberg-engine-backend` 提供，本 change 用 MSW 目录自证，无需后端运行。
