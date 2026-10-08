## MODIFIED Requirements

### Requirement: Target network policy
The SQL service SHALL resolve a submitted host once, evaluate every A and AAAA result against configured allowed and denied CIDRs, and connect only to an address from that evaluated result set. For engines that do not use a submitted host, it SHALL instead evaluate every configured ZooKeeper quorum endpoint against the same CIDR policy before connecting.

#### Scenario: Resolve only allowed addresses
- **WHEN** every resolved address is allowed and none is denied
- **THEN** the service SHALL pin an address from that resolution for the connection attempt without resolving the hostname again

#### Scenario: Resolve any forbidden address
- **WHEN** any resolved address falls outside allowed CIDRs or inside denied CIDRs
- **THEN** the service SHALL reject the request before connecting with `400 VALIDATION_FAILED` and a safe host field error

#### Scenario: Enforce CIDR on Kerberos ZooKeeper endpoints
- **WHEN** a Kerberos engine connects and its configured ZooKeeper quorum contains one or more endpoints
- **THEN** the service SHALL evaluate every quorum endpoint against the allowed and denied CIDRs and SHALL reject the attempt with a safe error before connecting if any endpoint is forbidden

#### Scenario: Network policy is unsafe or DNS fails
- **WHEN** allowed CIDRs are empty, CIDR configuration is invalid, the host cannot be resolved, or resolution returns no address
- **THEN** the service SHALL fail startup for invalid policy or reject the operation safely without revealing internal network topology

### Requirement: Dynamic pool lifecycle foundation
The SQL service SHALL provide bounded, lazy Hikari pools keyed by data-source ID for later SQL consumers while keeping connection tests outside those pools. Engines that report `usesPooledConnections()=false` (Kerberos service discovery) SHALL bypass the dynamic pool entirely and use short-lived connections.

#### Scenario: Create a target pool lazily
- **WHEN** an internal business consumer first requests a saved data source pool for an engine that uses pooled connections
- **THEN** the manager SHALL decrypt current configuration, enforce network policy, and create a pool with maximum size 5, minimum idle 0, bounded global pool/connection limits, and configured idle retirement

#### Scenario: Kerberos engine bypasses the pool
- **WHEN** a consumer requests a connection for a data source whose engine reports `usesPooledConnections()=false`
- **THEN** the provider SHALL open a short-lived connection through the engine and SHALL NOT create or consult a Hikari pool for that data source

#### Scenario: Configuration changes or deletion commits
- **WHEN** a data-source update or deletion transaction commits
- **THEN** the manager SHALL close and remove the old pool so the next consumer cannot use stale credentials or network settings

#### Scenario: Return a borrowed connection
- **WHEN** an internal consumer finishes using a pooled connection
- **THEN** it SHALL rollback defensively when needed, restore auto-commit, restore the configured catalog only when `defaultDatabase` is non-empty, clear warnings, and release resources with deterministic close semantics

#### Scenario: Release a short-lived connection
- **WHEN** a consumer finishes using a non-pooled Kerberos connection
- **THEN** the service SHALL close the physical connection and SHALL NOT attempt to return it to a pool

#### Scenario: Test a connection
- **WHEN** either connection-test endpoint runs
- **THEN** it SHALL use and close a short-lived connection and SHALL NOT create or populate a dynamic business pool

## ADDED Requirements

### Requirement: Kerberos connection secret confidentiality
Kerberos material MUST stay server-side. The SQL service SHALL NOT persist or return the resolved keytab path, krb5.conf path, realm, principal, or ZooKeeper quorum in any API response, log, metric, or exception. Only the environment selector, keytab file name, and queue name SHALL be persisted with the data source, and the keytab file name SHALL be redacted from list/detail error messages where it could reveal server layout.

#### Scenario: Serialize a Kerberos data source
- **WHEN** a `HIVE_KERBEROS` data source list or detail response is produced
- **THEN** it SHALL contain the environment, keytab file name reference, queue, and `passwordConfigured=false`, and SHALL NOT contain a keytab absolute path, krb5.conf path, realm, principal, or ZooKeeper quorum

#### Scenario: Handle a Kerberos failure
- **WHEN** a Kerberos login or connect fails
- **THEN** the sanitized message and the log SHALL NOT expose the keytab path, krb5.conf path, realm, principal, ZooKeeper quorum, or ticket material
