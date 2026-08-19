## ADDED Requirements

### Requirement: Database list is the NAMESPACE adapter
`GET /api/v1/data-sources/{id}/databases` SHALL remain the list endpoint for the product `NAMESPACE` layer. The service SHALL NOT add a `/namespaces` or generic `/resources` tree in this change.

#### Scenario: Tag database items as NAMESPACE
- **WHEN** databases are listed for a visible MYSQL data source
- **THEN** each item SHALL include `name` as today and `kind` equal to `NAMESPACE`

#### Scenario: Keep tables bound to the NAMESPACE name
- **WHEN** tables or table detail are requested
- **THEN** the query parameter and `TableItem.database` / `TableDetail.database` SHALL still carry the parent NAMESPACE name, and the paths SHALL remain `/tables` and `/table-detail`
