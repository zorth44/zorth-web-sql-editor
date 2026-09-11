## ADDED Requirements

### Requirement: Qualified column completion after a table name

When the SQL editor caret is in SQL code immediately after a qualifier and a `.` (optionally followed by a column-name prefix), Monaco SHALL offer columns of the resolved table from the current tab's bound data source and NAMESPACE. Resolution SHALL prefer an exact loaded object name, then a unique case-insensitive loaded object name. The frontend SHALL load those columns on demand through the existing table-detail API, cache them in memory for that connection, and SHALL NOT prefetch table-detail for every table in the NAMESPACE. Unresolved, ambiguous, string-contained, or comment-contained qualifiers SHALL NOT cause a metadata request.

#### Scenario: Trigger column completion after a table name

- **WHEN** the current SQL tab is bound to a data source and NAMESPACE whose table list includes `orders`, and the user types `orders.` in the editor
- **THEN** Monaco SHALL open completion showing that table's column names and SHALL NOT list other tables as completions for that request

#### Scenario: Filter columns by prefix after the dot

- **WHEN** the caret is after `orders.id` and column completion is offered for table `orders`
- **THEN** Monaco SHALL replace only the text after the `.` and SHALL offer columns that match the prefix `id`

#### Scenario: Accept an engine-quoted table qualifier

- **WHEN** the bound engine quotes identifiers with backticks and the user types `` `orders`. ``
- **THEN** the frontend SHALL resolve the qualifier as table `orders` and offer that table's columns
- **WHEN** the bound engine quotes identifiers with double quotes and the user types `"orders".`
- **THEN** the frontend SHALL resolve the qualifier as table `orders` and offer that table's columns

#### Scenario: Resolve the identifier immediately before the dot

- **WHEN** the user types `sales.orders.` while the tab is bound to NAMESPACE `sales` whose tables include `orders`
- **THEN** the frontend SHALL treat `orders` as the table qualifier and SHALL look that name up in the current NAMESPACE table list

#### Scenario: Prefer an exact table-name match

- **WHEN** the loaded object list contains names that differ only by case and the qualifier exactly matches one of them
- **THEN** the frontend SHALL resolve the exact match and SHALL NOT treat the other case-insensitive matches as an ambiguity

#### Scenario: Resolve a unique case-insensitive table-name match

- **WHEN** no table or view name exactly matches the qualifier and exactly one loaded object name matches it case-insensitively
- **THEN** the frontend SHALL resolve that unique object

#### Scenario: Reject an ambiguous case-insensitive table-name match

- **WHEN** no object name exactly matches the qualifier and more than one loaded object name matches it case-insensitively
- **THEN** the frontend SHALL NOT call table-detail and SHALL offer no qualified column completions

#### Scenario: Fetch columns once per table

- **WHEN** column completion is requested for a resolved table whose columns are not yet cached
- **THEN** the frontend SHALL request table-detail for that table, cache the column names, and reuse the cache for later completion on the same data source, NAMESPACE, and table
- **WHEN** two completion requests for that uncached table overlap
- **THEN** the frontend SHALL issue a single table-detail request

#### Scenario: Skip metadata when the qualifier is not a loaded table

- **WHEN** the text before the caret matches `qualifier.` but `qualifier` does not uniquely match a loaded table or view name in the current NAMESPACE
- **THEN** the frontend SHALL NOT call table-detail and SHALL NOT offer table or NAMESPACE names as if this were unqualified completion

#### Scenario: Ignore qualifier-like text in strings and comments

- **WHEN** completion is requested after text such as `orders.` while the caret is inside a SQL string, line comment, or block comment
- **THEN** the frontend SHALL offer no metadata completions, SHALL NOT resolve `orders` as a table qualifier, and SHALL NOT call table-detail for that text

#### Scenario: Retain a loaded table directory while the resource browser is unmounted

- **WHEN** the current tab's binding has previously loaded its table list and the user switches to a sidebar that unmounts the resource browser
- **THEN** qualified completion SHALL continue resolving tables from the binding-tagged directory without calling the table-list API from the typing path

#### Scenario: Keep unqualified metadata completion

- **WHEN** completion is requested in SQL code and the text before the caret is not a table qualifier followed by `.`
- **THEN** Monaco SHALL offer known NAMESPACE names, loaded table and view names, and already-cached column names without inserting credentials or untrusted executable snippets

#### Scenario: Insert a column name safely

- **WHEN** the user accepts a column completion whose name is a simple unquoted identifier
- **THEN** the editor SHALL insert that name as-is
- **WHEN** the user accepts a column completion whose name is not a simple unquoted identifier
- **THEN** the editor SHALL insert it wrapped in the bound engine's identifier quote, double every occurrence of that quote inside the name, and SHALL NOT insert extra SQL

#### Scenario: Drop cached columns on metadata refresh or connection change

- **WHEN** the user refreshes the resource tree, or the current SQL tab binds a different data source or NAMESPACE
- **THEN** the frontend SHALL discard cached columns that no longer belong to that binding before the next qualified completion

#### Scenario: Ignore a stale table-detail response after invalidation

- **WHEN** a table-detail request is in flight and metadata refresh or a tab binding change invalidates its completion generation before the response arrives
- **THEN** the frontend SHALL NOT write that response into the active column cache or expose its columns through completion

## MODIFIED Requirements

### Requirement: Monaco MySQL editing

The frontend SHALL wrap Monaco with the bound engine's catalog language (MYSQL: `mysql`), formatting, and metadata completion, and SHALL expose whether a selection exists so run affordances can label themselves.

#### Scenario: Execute from the run action

- **WHEN** a user chooses Run
- **THEN** the frontend SHALL execute every statement in the selection when one exists, otherwise every statement in the editor, and SHALL NOT block multiple statements with “暂不支持批量执行”

#### Scenario: Label the run action from the selection

- **WHEN** the editor selection changes between empty and non-empty
- **THEN** the workspace run action SHALL relabel itself between running the whole editor and running the selection so the current target is visible without hovering

#### Scenario: Request completion

- **WHEN** completion is requested for the active connection/NAMESPACE, the caret is in SQL code, and it is not after a table qualifier and `.`
- **THEN** Monaco SHALL offer known NAMESPACE, table/view, and already-cached column names without inserting credentials or untrusted executable snippets
- **WHEN** completion is requested in SQL code immediately after a loaded table or view name and `.`
- **THEN** Monaco SHALL offer that object's column names as specified by qualified column completion
