## MODIFIED Requirements

### Requirement: Safe JDBC configuration
The SQL service SHALL build target JDBC URLs exclusively through the data source's registered engine from structured validated fields and SHALL apply user properties before that engine's non-overridable security properties.

#### Scenario: Accept an allowed MySQL property
- **WHEN** the engine is `MYSQL` and `properties` contains `serverTimezone`, `characterSetResults`, `zeroDateTimeBehavior`, `tinyInt1isBit`, or `sendFractionalSeconds` with an allowed value
- **THEN** the MYSQL engine SHALL include it in the generated JDBC configuration

#### Scenario: Reject an unknown or unsafe property
- **WHEN** `properties` contains an undeclared key, invalid value, credentials, SSL override, timeout override, multi-query/local-file/deserialization option, proxy/socket factory, connection attribute, or session initialization
- **THEN** the service SHALL return `400 VALIDATION_FAILED` and SHALL NOT open a connection

#### Scenario: Enforce fixed MySQL security properties
- **WHEN** a MYSQL JDBC configuration is built
- **THEN** it SHALL end with non-overridable `allowMultiQueries=false`, `allowLoadLocalInfile=false`, `allowUrlInLocalInfile=false`, `autoDeserialize=false`, `useUnicode=true`, and `characterEncoding=UTF-8`

#### Scenario: Map the SSL mode
- **WHEN** the engine is `MYSQL` and SSL mode is `DISABLED`, `PREFERRED`, or `REQUIRED`
- **THEN** the MYSQL engine SHALL apply the documented Connector/J flags and SHALL NOT claim CA or hostname verification

#### Scenario: Format an IPv6 address
- **WHEN** the pinned target address is IPv6
- **THEN** the JDBC URL SHALL surround it with brackets

#### Scenario: Orchestrators do not hardcode the MySQL URL
- **WHEN** a JDBC target is built for any saved or tested configuration
- **THEN** data-source, pool, and connection-test orchestrators SHALL call the registered engine and SHALL NOT concatenate a `jdbc:mysql://` prefix themselves
