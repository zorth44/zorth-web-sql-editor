## ADDED Requirements

### Requirement: Current-user script workspace
The SQL workspace sidebar SHALL offer a Scripts rail beside Database and execution History when `SCRIPT_MANAGE` is present. The rail SHALL list the current user's saved scripts, open them into SQL tabs, and SHALL NOT mix in execution-history rows or Copilot conversations.

#### Scenario: Open the scripts workspace
- **WHEN** a user with `SCRIPT_MANAGE` selects the Scripts rail
- **THEN** the frontend SHALL load the current-user script list and SHALL NOT show execution history or Copilot conversations in that pane

#### Scenario: Search scripts
- **WHEN** the user types a keyword in the scripts rail
- **THEN** the frontend SHALL request a server-filtered page and render name, statement summary, connection snapshot, and updated time so scripts that share a name remain distinguishable

#### Scenario: Open an available script
- **WHEN** the user opens a script whose detail reports `connectionAvailable=true`
- **THEN** the workspace SHALL focus an existing SQL tab bound to that `scriptId` if one is open, otherwise create a SQL tab with the saved name, SQL, data source, and NAMESPACE, and SHALL NOT execute the SQL automatically

#### Scenario: Open a script whose connection is gone
- **WHEN** script detail reports `connectionAvailable=false`
- **THEN** the workspace SHALL restore name and SQL only and SHALL require a new connection selection before execution

#### Scenario: Delete a script
- **WHEN** the user confirms deletion of a listed script
- **THEN** the frontend SHALL call delete with the current `version`, remove it from the list, and SHALL close or unbind any open tab that referenced that `scriptId` without discarding unrelated tabs

### Requirement: Save SQL tabs as scripts
The workspace SHALL persist the active SQL tab through create or update of a current-user script, bind the tab to the returned `scriptId` and `version`, and keep a dirty flag from the last successful save snapshot.

#### Scenario: First save
- **WHEN** the user saves a SQL tab that has no `scriptId` and provides a non-blank name
- **THEN** the frontend SHALL POST a new script with the tab SQL and current connection binding, set the tab title to the script name, and mark the tab clean

#### Scenario: Save an already bound tab
- **WHEN** the user saves a SQL tab that already has a `scriptId`
- **THEN** the frontend SHALL PUT that script with the tab's current `version`, SQL, name, and connection binding, and on success SHALL store the new `version` and mark the tab clean

#### Scenario: Save as a new script
- **WHEN** the user chooses Save As on a SQL tab and provides a name
- **THEN** the frontend SHALL POST a new script and rebind the current tab to the new `scriptId` without modifying the previous script row

#### Scenario: Conflict on save
- **WHEN** save returns `409 VERSION_CONFLICT`
- **THEN** the frontend SHALL keep the local editor text, SHALL NOT mark the tab clean, and SHALL tell the user to reload the script or Save As

#### Scenario: Hide save without capability
- **WHEN** Session lacks `SCRIPT_MANAGE`
- **THEN** the frontend SHALL hide the Scripts rail and save actions and SHALL keep preventing the browser save dialog

### Requirement: Rename a saved script
The workspace SHALL let the user rename a script from the Scripts rail and from a bound SQL tab title. A rename of a bound script SHALL persist immediately and SHALL send the last successful save snapshot for statement and connection so unsaved editor text is not written. Duplicate names SHALL remain allowed.

#### Scenario: Rename from the scripts rail
- **WHEN** the user sets a listed script's name to a non-blank value of at most 100 characters
- **THEN** the frontend SHALL PUT that script with the new name, current `version`, and unchanged statement and connection, SHALL update any open tab bound to that `scriptId` (title and stored `version`), and SHALL refresh the list timestamp

#### Scenario: Rename a bound tab title
- **WHEN** the user edits the title of a SQL tab that has a `scriptId`
- **THEN** the frontend SHALL persist that name as a script rename, keep the same `scriptId`, and SHALL NOT mark unsaved SQL as saved

#### Scenario: Rename an unsaved tab
- **WHEN** the user edits the title of a SQL tab that has no `scriptId`
- **THEN** the frontend SHALL change only the local tab title and SHALL use that title as the default name on first save

#### Scenario: Rename to a name that already exists
- **WHEN** the new name matches another script owned by the same user
- **THEN** the frontend SHALL keep both rows and SHALL continue to show updated time so they can be told apart

## MODIFIED Requirements

### Requirement: Recoverable SQL tabs
The workspace SHALL open on a welcome page when no editor tab is open, support multiple connection-bound tabs, confirm close of dirty SQL tabs, and persist only bounded SQL draft state in Session Storage. Drafts MAY include `scriptId` and `version`. Results, Tokens, and credentials SHALL NOT be stored. Server-side scripts SHALL survive logout.

#### Scenario: Start the editor
- **WHEN** no recoverable draft exists
- **THEN** the workspace SHALL show a welcome page with an action to open a SQL editor and SHALL NOT auto-select a data source or create `Query 1`

#### Scenario: Close the last tab
- **WHEN** a user closes the last editor tab
- **THEN** the workspace SHALL return to the welcome page and SHALL NOT immediately create a replacement tab

#### Scenario: Open SQL from the welcome page
- **WHEN** a user chooses to open a SQL editor from the welcome page
- **THEN** the workspace SHALL create a query tab bound to the currently selected connection, which MAY be unbound until a data source and database are chosen in the resource tree

#### Scenario: Reload drafts
- **WHEN** the page reloads with valid bounded draft state
- **THEN** tab names, SQL text, `scriptId`, `version`, and non-sensitive connection identifiers MAY be restored while results, Tokens, and credentials SHALL NOT be restored

#### Scenario: Logout or receive 401
- **WHEN** authenticated teardown occurs
- **THEN** all editor draft, active execution, result, and query-cache state SHALL be cleared and server-side scripts SHALL remain stored for the user

#### Scenario: Close a dirty tab
- **WHEN** a user closes a SQL tab whose SQL, name, or connection binding differs from the last successful save, or that has never been saved and contains SQL
- **THEN** the frontend SHALL require confirmation before discarding it

#### Scenario: Close a clean saved tab
- **WHEN** a user closes a SQL tab that is bound to a script and matches the last successful save snapshot
- **THEN** the frontend SHALL close the tab without confirmation

### Requirement: Monaco MySQL editing
The frontend SHALL wrap Monaco with the bound engine's catalog language (MYSQL: `mysql`), formatting, metadata completion, and documented keyboard commands, and SHALL expose whether a selection exists so run affordances can label themselves.

#### Scenario: Execute selection or current statement
- **WHEN** a user presses Cmd/Ctrl+Enter
- **THEN** the frontend SHALL execute the non-empty selection or the statement containing the cursor as determined by a scanner that ignores delimiters in quotes and comments

#### Scenario: Execute a script
- **WHEN** a user presses Cmd/Ctrl+Shift+Enter
- **THEN** the frontend SHALL execute every statement in the selection when one exists, otherwise every statement in the editor, and SHALL NOT block multiple statements with “暂不支持批量执行”

#### Scenario: Label the run action from the selection
- **WHEN** the editor selection changes between empty and non-empty
- **THEN** the workspace run action SHALL relabel itself between running the whole editor and running the selection so the current target is visible without hovering

#### Scenario: Save shortcut
- **WHEN** a user presses Cmd/Ctrl+S on a SQL tab and `SCRIPT_MANAGE` is present
- **THEN** the frontend SHALL prevent the browser save dialog and SHALL save the active tab as a current-user script

#### Scenario: Request completion
- **WHEN** completion is requested for the active connection/NAMESPACE
- **THEN** Monaco SHALL offer known NAMESPACE, table/view, and column names without inserting credentials or untrusted executable snippets

### Requirement: SQL editor quality gates
The second-stage frontend SHALL extend production contract mocks and repeatable unit, component, E2E, static, and build checks.

#### Scenario: Verify editor behavior
- **WHEN** frontend verification runs
- **THEN** tests SHALL cover SQL extraction, special values, DDL metadata invalidation, SELECT/DML/DDL/error/cancel, CSV, metadata, history, and script save/open/delete core flows
