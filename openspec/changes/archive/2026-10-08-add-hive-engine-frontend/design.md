## Context

前端自 `engine-catalog-dynamic-forms` 起就按 `GET /api/v1/engines` 描述符驱动：表单控件、资源树层级与标签、Monaco 语言都取自目录，组件里不再写死 `mysql`。GBase 8a 已验证「同族新产品」只需后端注册引擎、前端补图标即可（见归档 `add-gbase-8a-engine`）。

Hive 是**新协议族**（`HIVE_WIRE`），且引入了全新的编辑器语言 `hive`，所以前端除图标外还需注册语言。前端单测跑在 MSW 上（`web/src/mocks/engines.ts`），不依赖后端运行，因而本 change 可以先于/并行于后端落地。

约束不变：Vue 3 + TypeScript、Vite、Vitest、MSW；`Engine = string`，`EngineFieldKind`/`ResourceTreeKind` 均为字符串开放，无需改类型联合。

## Goals / Non-Goals

**Goals:**

- MSW 目录出现第 4 项 HIVE，契约形状与后端 `EngineDescriptor` 一致。
- 表单选中 Hive 时：默认端口 10000、`defaultDatabase` 可选、仅显示 Hive `propertyFields`（`hive.metastore.uris`），不残留 POSTGRESQL 键。
- 类型卡片与资源树显示 Hive 图标。
- 资源树 NAMESPACE 标签用 Hive 目录文案（数据库 / 筛选数据库）。
- 编辑器语言为 `hive`；未注册时按现有回退规则退回 `mysql`，保持可编辑。
- 先写测试后写实现：catalog 语言映射、图标、表单卡片、资源树标签、Monaco 语言均有单测。
- 不依赖后端：单测用 MSW 目录自证。

**Non-Goals:**

- 不注册后端引擎，不连真实 Hive，不做 E2E/集成测试。
- 不写 Hive 专用表单/树分支（一律走描述符）。
- 不改 `GET /api/v1/engines` 形状或 `Engine` 类型联合。
- 不引入新依赖或 Monaco 定制 hive 语法（无原生则回退）。

## Decisions

### 1. 只动目录消费面，不加 Hive 专用分支

改动点限定为四处：MSW 目录、`EngineTypeIcon`（唯一非目录驱动的分支点）、`catalog.ts` 的语言注册/回退映射、`SqlMonacoEditor` 的语言注册。表单（`DataSourceForm.vue`）与资源树（`ResourceBrowser.vue`）已按描述符渲染，**不改**。

### 2. 编辑器语言 `hive` 的注册与回退

`catalog.ts` 的 `editorLanguageFor(descriptor)` 只在该语言已注册时采用，否则回退 `mysql`。把 `hive` 加入 `REGISTERED_EDITOR_LANGUAGES` 即可让 Hive 标签用 `hive`。`formatterLanguageFor('hive')` 返回 `mysql`（sql-formatter 无 Hive 方言，格式化用 mysql 规则即可）。`SqlMonacoEditor` 的 `resolvedLanguage()` 与 `installCompletion` 需认识 `hive`：若 Monaco 有可用的 hive/sql 基础语法则注册，否则按现有「未知 → mysql」回退，保证可编辑。

### 3. 验证策略（前端，先测后码）

用 Vitest + MSW，覆盖：
- `catalog.ts` 语言映射（新增专门单测，补上此前只被间接覆盖的缺口）。
- `EngineTypeIcon.test.ts` 的引擎列表加入 `HIVE`。
- `DataSourceFormPage.test.ts` 的 HIVE 卡片选中后端口 10000、`defaultDatabase` 可选、`hive.metastore.uris` 可见、PG 键消失。
- `ResourceBrowser.test.ts` 断言 HIVE 数据源的 NAMESPACE 标签为「数据库 / 筛选数据库」，并保留「忽略未知树层级」用例。
- `SqlMonacoEditor.test.ts` 断言 Hive 数据源绑定标签使用 `hive`（或按回退映射）。

不做 E2E、不做真实后端联调。

## Risks / Trade-offs

- [Monaco 无原生 hive 语法导致语言注册不生效] → 按现有回退规则映射到 `sql`/`mysql`，断言「可编辑且不崩溃」，不强求高亮。
- [MSW 目录与后端描述符漂移] → 以后端 `EngineDescriptor` 形状为准，字段名/取值对齐；本 change 的目录即为契约副本。
- [sql-formatter 无 hive 方言] → `formatterLanguageFor` 回退 `mysql`，格式化不报错。
- [误改表单/树写死 Hive 分支] → 任务明确只改目录消费面，表单/树保持描述符驱动。

## Migration Plan

1. 前端可与后端独立发布：以 MSW 目录自证；接后端后目录自然返回 HIVE，无需前端改动。
2. 回滚：回退前端版本即可，后端目录多出的 HIVE 不被旧前端渲染为可选项也不影响既有引擎。

## Open Questions

- Monaco 是否注册原生 `hive` 语言？决定：优先尝试注册；无可用定义时按回退规则映射到 `sql`/`mysql`，保持可编辑。与后端目录声明的 `editorLanguage=hive` 不冲突。
