## Context

前端自 `engine-catalog-dynamic-forms` 起就按 `GET /api/v1/engines` 描述符驱动：表单控件、资源树层级与标签、Monaco 语言都取自目录。GBase 8a、Hive 已验证「后端注册引擎 + 前端补目录消费面」的模式：`add-hive-engine-frontend` 已把 `hive` 注册进 `REGISTERED_EDITOR_LANGUAGES`、让资源树/编辑器按 `family`/描述符渲染。

但表单模型仍是写死的：`web/src/data-sources/model.ts` 的 `DataSourceFormModel` 固定 `host/port/username/password/sslMode/defaultDatabase/connectTimeoutSeconds`，`DataSourceForm.vue` 的 `updateField` 也只按 NUMBER/其它二分。Kerberos 引入了 `environment`/`keytabFile`/`queueName` 与新的字段种类，落在描述符驱动之外，必须把连接字段模型泛化。

前端单测跑在 MSW 上（`web/src/mocks/engines.ts`），不依赖后端运行，因而本 change 可以先于/并行于后端落地。约束不变：Vue 3 + TypeScript、Vite、Vitest、MSW；`Engine = string`、`EngineFieldKind`/`ResourceTreeKind` 均为字符串开放，无需改类型联合。

## Goals / Non-Goals

**Goals:**

- MSW 目录出现第 5 项 `HIVE_KERBEROS`，契约形状与后端 `EngineDescriptor` 一致。
- 表单把连接字段改为**描述符声明的动态通用字段**：选中引擎时按描述符初始化、渲染、提交，主干无 `if (engine === ...)` 或字段名硬编码。
- 表单选中 HIVE_KERBEROS 时：显示 environment 下拉、keytab 文件名、可选 queue、可选 defaultDatabase，不显示 host/port/username/password/SSL。
- 校验按描述符：`required`/`requiredOnCreate`/`maxLength` 驱动；Kerberos 不要求密码。
- 类型卡片显示 Kerberos Hive 图标。
- 先写测试后写实现：MSW 目录、表单渲染、校验、提交、图标均有单测。
- 不依赖后端：单测用 MSW 目录自证。

**Non-Goals:**

- 不注册后端引擎，不连真实 Kerberos，不做 E2E/集成测试。
- 不改 `frontend-sql-editor-workbench`：语言 `hive` 与 NAMESPACE 已由 `add-hive-engine-frontend`/描述符覆盖，无需 Kerberos 专用分支。
- 不改 `GET /api/v1/engines` 形状或 `Engine` 类型联合。
- 不引入新依赖或 Monaco 定制语法。

## Decisions

### 1. 连接字段模型泛化为描述符声明的动态字段

`DataSourceFormModel` 把固定的 host/port/username/... 收敛为一个通用连接字段集合（如 `connection: Record<string, string | number>`），键来自当前描述符的 `connectionFields[].name`：

- `emptyDataSourceForm(descriptor?)`：按描述符字段的 `defaultValue`/默认端口初始化。
- `detailToForm(detail, descriptor?)`：把详情中描述符声明的字段填入 `connection`。
- `connectionFields(form, descriptor)`：只序列化描述符声明的字段名，`trim`/`Number` 由字段 `widget` 决定。

这样 `HIVE_KERBEROS` 的三个字段无需在 model 中具名，未来引擎同理。`properties`（JDBC 高级配置）保持现状，仍走 `sanitizeProperties`。

### 2. 校验由描述符驱动

`validation.ts` 不再硬编码 host/username/password 规则，而是遍历描述符 `connectionFields`：`required`（或非编辑态 `requiredOnCreate`）非空、`maxLength` 上限、NUMBER 范围；`name`/`description` 保持核心规则。Kerberos 因描述符未声明 password，自然不校验密码。

### 3. 表单只按 widget 渲染，不加引擎分支

`DataSourceForm.vue` 现有 `v-for="field in descriptor?.connectionFields"` 已是描述符驱动，只需让 `updateField` 按 `widget`/`kind` 写入通用 `connection` 集合；SELECT 渲染已经支持，environment 的环境选项来自描述符 `options`。不加 Kerberos 专用模板。

### 4. 只动目录消费面，语言/资源树不重复改

Kerberos 唯一非描述符驱动的分支点是 `EngineTypeIcon`（引擎图标）与 MSW 目录。语言 `hive` 与 NAMESPACE 行为已由 `add-hive-engine-frontend` 提供，`HIVE_KERBEROS` 作为同 `family` 的引擎自动复用，故本 change 不动 workbench 组件与其 spec。

### 5. 验证策略（前端，先测后码）

用 Vitest + MSW，覆盖：

- `mocks/engines.ts`：目录含 `HIVE_KERBEROS`，字段与后端描述符一致。
- `EngineTypeIcon.test.ts`：`HIVE_KERBEROS` 卡片/树图标渲染。
- `DataSourceFormPage.test.ts`：选中 HIVE_KERBEROS 卡片后显示 environment/keytabFile/queueName/defaultDatabase，隐藏 host/port/username/password/SSL；提交只带描述符声明字段。
- `model.test.ts`/`validation.test.ts`：按描述符初始化和校验；Kerberos 不要求密码；queueName 长度校验。
- 既有引擎（MYSQL/POSTGRESQL/GBASE_8A/HIVE）卡片、字段渲染与提交不变。

不做 E2E、不做真实后端联调。

## Risks / Trade-offs

- [动态字段重构触及既有引擎路径] → 用既有引擎的现有单测作回归保护；`connection` 集合对既有引擎产出与旧 `connectionFields()` 等价。
- [MSW 目录与后端描述符漂移] → 以后端 `EngineDescriptor` 形状为准，字段名/取值对齐；本 change 的目录即为契约副本。
- [误改表单/树写死 Kerberos 分支] → 任务明确只改目录消费面与字段模型，表单/树保持描述符驱动。
- [详情回填字段名不一致] → `detailToForm` 以描述符字段名从 detail 取值，缺省为空。

## Migration Plan

1. 前端可与后端独立发布：以 MSW 目录自证；接后端后目录自然返回 `HIVE_KERBEROS`，无需前端改动。
2. 回滚：回退前端版本即可；后端目录多出的 `HIVE_KERBEROS` 不被旧前端渲染为可选项，也不影响既有引擎。
