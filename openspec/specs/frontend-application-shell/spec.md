# Frontend Application Shell Specification

## Purpose

Define the phase-one application routes, authenticated shell, integration configuration, accessibility baseline, Mock boundary, and quality gates.

## Requirements

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

### Requirement: Complete asynchronous states
Every Session and data-source view SHALL represent pending, empty, success, and failure states without exposing raw exceptions.

#### Scenario: A request is pending
- **WHEN** a page is waiting for required server state
- **THEN** the page SHALL show an accessible loading state and SHALL prevent duplicate mutation submission

#### Scenario: A collection is empty
- **WHEN** a successful data-source list contains no items
- **THEN** the page SHALL show an empty state with an appropriate create action when management capability exists

#### Scenario: A request fails
- **WHEN** an API request fails
- **THEN** the page SHALL display the mapped safe message and a retry action only when retrying is safe

### Requirement: Accessibility baseline
Phase-one interactions SHALL be usable with a keyboard and SHALL not communicate state by color alone.

#### Scenario: Operate controls by keyboard
- **WHEN** a keyboard user navigates the shell, filters, forms, dialogs, and row actions
- **THEN** focus order, visible focus, labels, dialog trapping, and activation SHALL remain usable without a pointer

#### Scenario: Announce status
- **WHEN** validation, loading, connection testing, success, or error state changes
- **THEN** the application SHALL provide text/icon context and appropriate accessible status semantics in addition to color

#### Scenario: Use the supported viewport
- **WHEN** viewport width is at least 1024 pixels in current Chrome or Edge
- **THEN** all phase-one content and actions SHALL remain operable without unintended clipping

### Requirement: Environment-driven integration
Service locations and integration policy SHALL be supplied through validated Vite environment variables.

#### Scenario: Start with valid configuration
- **WHEN** required API bases and integration settings are valid
- **THEN** the application SHALL configure separate authorization and SQL clients without hard-coded environment URLs

#### Scenario: Start with invalid production configuration
- **WHEN** a production build attempts to enable API Mock or lacks required integration values
- **THEN** the build or startup SHALL fail clearly instead of silently selecting unsafe defaults

### Requirement: Development-only contract Mock
The application SHALL provide an MSW-backed SQL API simulation for development and automated tests only.

#### Scenario: Enable Mock in development
- **WHEN** the runtime is development/test and `VITE_ENABLE_API_MOCK=true`
- **THEN** MSW SHALL simulate Session and data-source endpoints using the production TypeScript contract types

#### Scenario: Run a production build
- **WHEN** the application is built for production
- **THEN** no browser Mock worker SHALL be registered and Mock activation SHALL be rejected

#### Scenario: Exercise failure contracts
- **WHEN** tests select Mock cases for 401, 404, version conflict, connection failure, or data-source-in-use
- **THEN** the handlers SHALL return the same status codes and payload shapes documented for the real SQL service

### Requirement: Phase-one quality gates
The frontend SHALL provide repeatable static, unit/component, end-to-end, and production-build verification for retained phase-one and delivered phase-two behavior.

#### Scenario: Verify a candidate implementation
- **WHEN** the project's verification commands run
- **THEN** formatting/lint, type checking, Vitest, Playwright, and Vite production build SHALL complete through documented pnpm scripts without regressing phase-one flows
