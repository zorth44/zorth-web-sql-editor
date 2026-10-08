## 1. Catalog mocks

- [ ] 1.1 Add failing tests asserting the MSW catalog includes `ICEBERG` with the `HIVE_KERBEROS` field shape
- [ ] 1.2 Add the Iceberg descriptor to `web/src/mocks/engines.ts` and include it in `mockEngineCatalog.items`; make 1.1 pass
- [ ] 1.3 Allow `engine=ICEBERG` without a password in `web/src/mocks/handlers.ts`, matching `HIVE_KERBEROS`

## 2. Engine card icon

- [ ] 2.1 Add failing `EngineTypeIcon.test.ts` cases for `ICEBERG` in the card and tree variants
- [ ] 2.2 Add the ICEBERG branch and assets (`web/src/assets/engines/iceberg*.svg`) to `EngineTypeIcon.vue`; make 2.1 pass

## 3. Data-source form (descriptor-driven)

- [ ] 3.1 Add failing `DataSourceFormPage.test.ts` cases: selecting the ICEBERG card shows environment/keytab/optional queue/optional default database and hides host, port, username, password, and SSL, matching `HIVE_KERBEROS`
- [ ] 3.2 Verify the descriptor-driven form satisfies 3.1 (no Iceberg-specific branch); fix descriptor defaults if needed

## 4. Docs and verification

- [ ] 4.1 Update the frontend `data-source-management` spec for the Iceberg descriptor
- [ ] 4.2 Run frontend typecheck and unit tests; confirm all pass against MSW without a backend
- [ ] 4.3 Confirm MYSQL/POSTGRESQL/GBASE_8A/HIVE/HIVE_KERBEROS card, tree, and language behavior is unchanged
