## ADDED Requirements

### Requirement: GBase 8a JDBC configuration
The GBASE_8A engine SHALL build target JDBC URLs with the official GBase connector: `jdbc:gbase://` scheme, MYSQL property allow-list, and MYSQL SSL flags. Orchestrators SHALL NOT special-case GBase.

#### Scenario: Accept an allowed GBase 8a property
- **WHEN** the engine is `GBASE_8A` and `properties` contains `serverTimezone`, `characterSetResults`, `zeroDateTimeBehavior`, `tinyInt1isBit`, or `sendFractionalSeconds` with an allowed value
- **THEN** the GBASE_8A engine SHALL include it in the generated JDBC configuration

#### Scenario: Reject GBase 8a-unsafe properties
- **WHEN** a GBASE_8A `properties` map contains a PostgreSQL-only key, SSL override, or any undeclared key
- **THEN** the service SHALL return `400 VALIDATION_FAILED` and SHALL NOT open a connection

#### Scenario: Format a GBase 8a IPv6 URL
- **WHEN** the pinned target address is IPv6 and the engine is GBASE_8A
- **THEN** the JDBC URL SHALL be `jdbc:gbase://[<ipv6>]:<port>/<database>`
