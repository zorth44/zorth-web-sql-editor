## 1. Catalog mocks and engine icon

- [ ] 1.1 Add failing `mocks`/`catalog` tests asserting the catalog contains `HIVE_KERBEROS` with `family=HIVE_WIRE`, `editorLanguage=hive`, and connection fields `environment` (SELECT, required) + `keytabFile` (TEXT, required) + `queueName` (TEXT, optional) + optional `defaultDatabase`, and no host/port/username/password/sslMode
- [ ] 1.2 Add the `HIVE_KERBEROS` descriptor to `web/src/mocks/engines.ts` (environment options dev/func/pro, NAMESPACE 数据库/筛选数据库) and include it in `mockEngineCatalog.items`; make 1.1 pass
- [ ] 1.3 Add failing `EngineTypeIcon.test.ts` cases for `HIVE_KERBEROS` in card and tree variants
- [ ] 1.4 Add the `HIVE_KERBEROS` branch and assets (`web/src/assets/engines/*`) to `EngineTypeIcon.vue`; make 1.3 pass

## 2. Dynamic connection-field model

- [ ] 2.1 Add failing `model.test.ts` cases: `emptyDataSourceForm(descriptor)` seeds descriptor defaults; `detailToForm` fills descriptor-declared fields; create/update/test requests serialize only descriptor-declared connection fields
- [ ] 2.2 Generalize `DataSourceFormModel`/`model.ts` to a descriptor-declared connection-field collection (no hardcoded host/port/username/password); make 2.1 pass while keeping MYSQL/POSTGRESQL/GBASE_8A/HIVE output equivalent

## 3. Descriptor-driven validation

- [ ] 3.1 Add failing `validation.test.ts` cases: descriptor `required`/`requiredOnCreate`/`maxLength` drive connection-field errors; `HIVE_KERBEROS` produces no password error; `queueName` length is validated when present
- [ ] 3.2 Rewrite `web/src/data-sources/validation.ts` to validate connection fields per descriptor; keep name/description rules; make 3.1 pass

## 4. Data-source form rendering

- [ ] 4.1 Add failing `DataSourceFormPage.test.ts` cases: selecting HIVE_KERBEROS shows environment/keytabFile/queueName/defaultDatabase and hides host/port/username/password/SSL; submit sends descriptor-declared fields only
- [ ] 4.2 Update `DataSourceForm.vue` to write/read the generic connection collection by `widget`/`kind` (SELECT options, NUMBER, TEXT); make 4.1 pass without a Kerberos-specific branch

## 5. Verification

- [ ] 5.1 Update frontend `data-source-management` spec delta for descriptor-driven dynamic fields and Kerberos fields
- [ ] 5.2 Run frontend typecheck and unit tests; confirm all pass against MSW without a backend
- [ ] 5.3 Confirm MYSQL/POSTGRESQL/GBASE_8A/HIVE card, field rendering, validation, and submission behavior is unchanged
