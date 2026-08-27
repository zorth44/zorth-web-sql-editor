## MODIFIED Requirements

### Requirement: Query export workflow
The frontend SHALL offer CSV export only for a successful query result and SHALL send only its execution ID and requested row limit. When the browser exposes File System Access, the frontend SHALL ask the user for a save location during the confirm gesture, then write the response body to that file in chunks without assembling a complete Blob. When that API is unavailable, the frontend SHALL keep the CSV as a Blob and start a download without converting the payload to text.

#### Scenario: Start an export
- **WHEN** a user confirms that export re-executes the query and File System Access is unavailable
- **THEN** the frontend SHALL fetch the CSV without automatic retry, keep it as a Blob, derive a safe filename, and start download without converting the payload to text

#### Scenario: Stream an export to a chosen file
- **WHEN** a user confirms that export re-executes the query and `showSaveFilePicker` is available
- **THEN** the frontend SHALL open the save picker before starting the request, using a suggested `{dataSource}-{database}.csv` name, fetch without automatic retry, and write response body chunks to the chosen file without buffering the complete payload as a Blob or string

#### Scenario: Cancel the save picker
- **WHEN** the user dismisses the save picker
- **THEN** the frontend SHALL NOT send the export request and SHALL remain in an idle export state

#### Scenario: Cancel an export
- **WHEN** a user cancels an in-progress download or leaves the page
- **THEN** its AbortSignal SHALL stop the request, any open file writable SHALL be aborted so a partial file is not kept as a successful download, and the UI SHALL return to an idle export state
