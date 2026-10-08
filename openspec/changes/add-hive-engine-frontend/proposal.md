## Why

后端 change `add-hive-engine-backend` 会注册 `HIVE` 引擎，`GET /api/v1/engines` 目录随即多出 Hive 描述符。前端表单、资源树、编辑器语言都由目录描述符驱动，因此前端只需让目录可被消费：MSW 目录加 HIVE、补类型卡片图标、注册 `hive` 编辑器语言。前端测试跑在 MSW 上，不依赖后端实现，后端也可独立先发。

本 change 只做前端，与后端 change 通过 `GET /api/v1/engines` 目录契约解耦，互不影响。

## What Changes

- MSW 目录（`web/src/mocks/engines.ts`）新增 Hive 描述符：`id=HIVE`、`displayName=Hive`、`family=HIVE_WIRE`、`defaultPort=10000`、`editorLanguage=hive`、反引号、NAMESPACE 文案「数据库」、`defaultDatabase` 可选、`propertyFields` 仅含 `hive.metastore.uris`。
- `web/src/components/EngineTypeIcon.vue` 增加 HIVE 卡片/树图标分支与资源；表单引擎卡片、资源树图标随之显示。
- `web/src/data-sources/catalog.ts` 把 `hive` 注册进 `REGISTERED_EDITOR_LANGUAGES`，并让 `formatterLanguageFor('hive')` 回退到 `mysql`（sql-formatter 无 hive 方言）。
- `web/src/components/editor/SqlMonacoEditor.vue` 注册 `hive` 语言定义（无原生时按现有回退规则映射到 `mysql`/`sql`），保持可编辑。
- 表单与资源树不写 Hive 专用分支：端口默认 10000、属性控件、NAMESPACE 标签均由描述符渲染。
- 不注册后端引擎，不改 `GET /api/v1/engines` 形状，不引入真实 Hive 连接。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `data-source-management`: 类型可选 Hive；表单按目录渲染，默认端口 10000，`defaultDatabase` 可选，仅显示 Hive `propertyFields`。
- `frontend-sql-editor-workbench`: Hive 资源树标签跟随目录，编辑器语言为 `hive`（未注册时回退 `mysql`）。

## Impact

- 前端：`web/src/mocks/engines.ts`、`web/src/components/EngineTypeIcon.vue` + `web/src/assets/engines/*`、`web/src/data-sources/catalog.ts`、`web/src/components/editor/SqlMonacoEditor.vue` 及其对应测试。
- 测试：先写前端单元测试（catalog 语言映射、图标、表单 HIVE 卡片、资源树标签、编辑器语言），再改实现；不做 E2E/集成测试。
- 后端：不在本 change 范围（见 `add-hive-engine-backend`）。本 change 用 MSW 目录自证，前端单测无需后端。
