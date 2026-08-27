## ADDED Requirements

### Requirement: Copilot session outlives SQL tabs
The workbench SHALL keep the Copilot conversation independent of SQL tab identity. Closing or switching SQL tabs MUST NOT discard Copilot history. SQL tabs SHALL continue to supply the bound data source, NAMESPACE, and editor text used as this-turn Copilot context. Copilot history MUST NOT be mixed into the left-rail SQL execution history workspace.

#### Scenario: Close a SQL tab
- **WHEN** the user closes a SQL tab that was used to send Copilot messages
- **THEN** the workbench SHALL NOT delete that Copilot conversation and SHALL NOT clear Copilot messages solely because the tab id is gone

#### Scenario: Left rail history stays SQL executions
- **WHEN** the user opens the sidebar History workspace
- **THEN** the list SHALL remain current-user SQL execution history and SHALL NOT list Copilot conversations
