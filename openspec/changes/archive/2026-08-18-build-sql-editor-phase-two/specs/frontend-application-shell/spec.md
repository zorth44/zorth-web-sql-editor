## MODIFIED Requirements

### Requirement: Phase-one route set
The application SHALL retain all phase-one routes and enable the reviewed phase-two SQL editor and history routes.

#### Scenario: Navigate to a phase-one page
- **WHEN** a user visits `/login`, `/auth/bridge`, `/data-sources`, `/data-sources/new`, or `/data-sources/:id/edit`
- **THEN** the router SHALL resolve the corresponding existing page with the documented authentication requirement

#### Scenario: Navigate to the phase-two editor
- **WHEN** an authenticated user visits `/sql-editor` or `/sql-editor/history/:historyId`
- **THEN** the application SHALL resolve the SQL workspace and optionally open the requested history rather than redirecting to data sources

#### Scenario: Navigate to the protected root
- **WHEN** an authenticated user visits `/` after phase two is enabled
- **THEN** the router SHALL redirect to `/sql-editor`

#### Scenario: Navigate to an unknown route
- **WHEN** a route does not match a defined route
- **THEN** the application SHALL render a 404 page

### Requirement: Authenticated application layout
Protected pages SHALL use a consistent desktop-oriented shell that makes both SQL editing and data-source management reachable.

#### Scenario: Render the shell
- **WHEN** Session validation succeeds
- **THEN** the top navigation SHALL display the application identity, current product name, SQL editor and data-source links, and a user menu containing logout

#### Scenario: Product context is read-only
- **WHEN** the shell displays the current product
- **THEN** it SHALL NOT present a product switcher or use product selection as a client-side data-source filter

#### Scenario: Render the editor shell
- **WHEN** the active route is the SQL editor at a supported desktop viewport
- **THEN** the shell SHALL provide a compact 48px navigation and allow the workspace to use the remaining viewport without the data-source page width constraint

### Requirement: Phase-one quality gates
The frontend SHALL provide repeatable static, unit/component, end-to-end, and production-build verification for retained phase-one and delivered phase-two behavior.

#### Scenario: Verify a candidate implementation
- **WHEN** the project's verification commands run
- **THEN** formatting/lint, type checking, Vitest, Playwright, and Vite production build SHALL complete through documented pnpm scripts without regressing phase-one flows

