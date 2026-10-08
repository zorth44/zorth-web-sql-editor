## Context

前端自 `engine-catalog-dynamic-forms` 起就按 `GET /api/v1/engines` 描述符驱动：表单控件、资源树层级与标签、Monaco 语言都取自目录，组件里不再写死 `mysql`。Kerberos + ZooKeeper 引擎 `HIVE_KERBEROS` 已由 `add-kerberos-zk-discovery-frontend` 落地，前端已支持 `ENVIRONMENT`/`KEYTAB`/`QUEUE` 字段控件与 `hive` 编辑器语言（见 `add-hive-engine-frontend`）。

`ICEBERG` 与 `HIVE_KERBEROS` 同族同形（`family=HIVE_WIRE`、`editorLanguage=hive`、连接字段完全相同），前端无需新控件或新语言，只是目录多一项、类型卡片多一个图标。前端单测跑在 MSW 上（`web/src/mocks/engines.ts`），不依赖后端运行，因而本 change 可以先于/并行于后端落地。

约束不变：Vue 3 + TypeScript、Vite、Vitest、MSW；`Engine = string`，`EngineFieldKind`/`ResourceTreeKind` 均为字符串开放，无需改类型联合。

## Goals / Non-Goals

**Goals:**

- MSW 目录出现第 6 项 ICEBERG，契约形状与后端 `EngineDescriptor` 一致，字段与 HIVE_KERBEROS 相同。
- 表单选中 Iceberg 时显示 environment/keytab/可选 queue/可选 defaultDatabase，隐藏 host/port/username/password/SSL。
- 类型卡片与资源树显示 Iceberg 图标。
- 先写测试后写实现：MSW 目录、图标、表单卡片均有单测。
- 不依赖后端：单测用 MSW 目录自证。

**Non-Goals:**

- 不注册后端引擎，不连真实 Iceberg，不做 E2E/集成测试。
- 不写 Iceberg 专用表单/树分支（一律走描述符）。
- 不新增编辑器语言（复用已注册的 `hive`）、不改 `GET /api/v1/engines` 形状或 `Engine` 类型联合。
- 不改表单模型/校验（Kerberos 字段能力已由 `add-kerberos-zk-discovery-frontend` 提供）。

## Decisions

### 1. 只动目录消费面，不加 Iceberg 专用分支

改动点限定为三处：MSW 目录、MSW handlers 的免密码分支、`EngineTypeIcon`（唯一非目录驱动的分支点）。表单（`DataSourceForm.vue`）与资源树（`ResourceBrowser.vue`）已按描述符渲染 Kerberos 字段，**不改**。

### 2. 编辑器语言与字段复用

`ICEBERG` 的 `editorLanguage=hive`，`hive` 已在 `REGISTERED_EDITOR_LANGUAGES` 注册（`add-hive-engine-frontend`），无需前端改动。连接字段 `environment`/`keytabFile`/`queueName`/`defaultDatabase` 的控件与校验已在 `add-kerberos-zk-discovery-frontend` 实现，描述符声明后即自动渲染。

### 3. 验证策略（前端，先测后码）

用 Vitest + MSW，覆盖：

- MSW 目录包含 ICEBERG 且字段与 HIVE_KERBEROS 同形。
- `EngineTypeIcon.test.ts` 的引擎列表加入 `ICEBERG`。
- `DataSourceFormPage.test.ts` 的 ICEBERG 卡片选中后显示 environment/keytab/可选 queue/可选 defaultDatabase，不显示 host/port/username/password/SSL。

不做 E2E、不做真实后端联调。

## Risks / Trade-offs

- [MSW 目录与后端描述符漂移] → 以后端 `EngineDescriptor` 形状为准，字段名/取值对齐；本 change 的目录即为契约副本。
- [误改表单/树写死 Iceberg 分支] → 任务明确只改目录消费面，表单/树保持描述符驱动。

## Migration Plan

1. 前端可与后端独立发布：以 MSW 目录自证；接后端后目录自然返回 ICEBERG，无需前端改动。
2. 回滚：回退前端版本即可，后端目录多出的 ICEBERG 不被旧前端渲染为可选项也不影响既有引擎。
