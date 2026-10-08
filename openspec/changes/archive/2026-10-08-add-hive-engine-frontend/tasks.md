## 1. Catalog mocks and editor-language logic

- [x] 1.1 Add failing tests for `web/src/data-sources/catalog.ts`: `editorLanguageFor` returns `hive` for a HIVE descriptor and `formatterLanguageFor('hive')` returns `mysql`
- [x] 1.2 Add the Hive descriptor to `web/src/mocks/engines.ts` (`id=HIVE`, `displayName=Hive`, `family=HIVE_WIRE`, port 10000, `editorLanguage=hive`, backtick quote, NAMESPACE 数据库/筛选数据库, optional `defaultDatabase`, `propertyFields` = `hive.metastore.uris`) and include it in `mockEngineCatalog.items`; make 1.1 pass by registering `hive` in `REGISTERED_EDITOR_LANGUAGES` and mapping it in `formatterLanguageFor`

## 2. Engine card icon

- [x] 2.1 Add failing `EngineTypeIcon.test.ts` cases for `HIVE` in the card and tree variants
- [x] 2.2 Add the HIVE branch and assets (`web/src/assets/engines/hive*.svg`) to `EngineTypeIcon.vue`; make 2.1 pass

## 3. Data-source form (descriptor-driven)

- [x] 3.1 Add failing `DataSourceFormPage.test.ts` cases: selecting the HIVE card defaults port 10000, keeps `defaultDatabase` optional, shows `hive.metastore.uris`, and drops POSTGRESQL keys such as `ApplicationName`
- [x] 3.2 Verify the descriptor-driven form satisfies 3.1 (no Hive-specific branch); fix descriptor defaults if needed

## 4. Resource tree and editor language

- [x] 4.1 Add failing `ResourceBrowser.test.ts` cases: a HIVE data source renders NAMESPACE labels 数据库/筛选数据库; unknown tree kinds still skipped
- [x] 4.2 Add failing `SqlMonacoEditor.test.ts` cases: a HIVE-bound tab uses `hive` (or the fallback mapping) and stays editable
- [x] 4.3 Register `hive` in `SqlMonacoEditor.vue` (`resolvedLanguage`/`installCompletion`) with the existing fallback; make 4.1/4.2 pass

## 5. Docs and verification

- [x] 5.1 Update frontend specs (data-source-management, frontend-sql-editor-workbench) for the Hive descriptor and language
- [x] 5.2 Run frontend typecheck and unit tests; confirm all pass against MSW without a backend
- [x] 5.3 Confirm MYSQL/POSTGRESQL/GBASE_8A card, tree, and language behavior is unchanged
