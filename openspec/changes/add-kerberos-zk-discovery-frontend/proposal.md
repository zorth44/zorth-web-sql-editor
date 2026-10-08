## Why

后端 change `add-kerberos-zk-discovery-backend` 会注册 `HIVE_KERBEROS` 引擎，`GET /api/v1/engines` 目录随即多出第 5 项 Kerberos 描述符（`family=HIVE_WIRE`、`editorLanguage=hive`、字段 `environment`/`keytabFile`/`queueName`/`defaultDatabase`）。前端表单、资源树、编辑器语言都由目录描述符驱动，但现有表单模型写死了 host/port/username/password，且不认识新字段种类（`ENVIRONMENT`/`KEYTAB`/`QUEUE`）。因此前端需要一个独立 change：让目录可被消费、按描述符渲染与提交 Kerberos 字段。

前端测试跑在 MSW 上，不依赖后端实现，因而本 change 可以先于/并行于后端落地，与后端通过 `GET /api/v1/engines` 目录契约解耦。本 change 只做前端。

## What Changes

- MSW 目录（`web/src/mocks/engines.ts`）新增 `HIVE_KERBEROS` 描述符：`id=HIVE_KERBEROS`、Kerberos Hive `displayName`、`family=HIVE_WIRE`、`editorLanguage=hive`、反引号、`environment`（SELECT, required, 环境选项）、`keytabFile`（TEXT, required）、`queueName`（TEXT, optional）、可选 `defaultDatabase`、NAMESPACE 文案「数据库」；不含 `host`/`port`/`username`/`password`/`sslMode`。
- `web/src/components/EngineTypeIcon.vue` 增加 `HIVE_KERBEROS` 卡片/树图标分支与资源。
- `web/src/data-sources/model.ts` 把连接字段改为**描述符声明的动态通用字段**：表单模型与提交只承载描述符声明的 `connectionFields`，主干不再写死具体字段名。
- `web/src/data-sources/validation.ts` 按描述符的 `required`/`requiredOnCreate`/`maxLength` 校验；`HIVE_KERBEROS` 不要求密码，`queueName` 有值时校验长度。
- `web/src/components/DataSourceForm.vue` 按描述符 `widget`（TEXT/NUMBER/SELECT）渲染连接字段，含环境下拉；不写 Kerberos 专用分支。
- 编辑器语言与资源树不写 Kerberos 专用分支：`family=HIVE_WIRE`/`editorLanguage=hive` 已由 `add-hive-engine-frontend` 注册的 `hive` 语言与描述符驱动的 NAMESPACE 覆盖，本 change 不改 `frontend-sql-editor-workbench`。
- 不注册后端引擎、不连真实 Kerberos、不做 E2E/集成测试。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `data-source-management`: 表单按描述符动态渲染与校验连接字段；新增/编辑/测试支持 Kerberos 字段（环境、keytab 文件名、queue），不要求密码。

## Impact

- 前端：`web/src/mocks/engines.ts`、`web/src/components/EngineTypeIcon.vue` + `web/src/assets/engines/*`、`web/src/data-sources/model.ts`、`web/src/data-sources/validation.ts`、`web/src/components/DataSourceForm.vue` 及其对应测试。
- 测试：先写前端单元测试（MSW 目录、表单渲染/校验/提交、图标），再改实现；不做 E2E/集成测试。
- 依赖：编辑器语言 `hive` 的注册与描述符驱动的树/语言行为由 `add-hive-engine-frontend` 提供；后端目录由 `add-kerberos-zk-discovery-backend` 提供，本 change 用 MSW 目录自证，无需后端运行。
