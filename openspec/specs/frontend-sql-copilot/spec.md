# Frontend SQL Copilot Specification

## Purpose

Define the right-hand Copilot panel on `/sql-editor`: SQL generation and repair, insert into the active tab, streamed Agent replies, and user-scoped conversation history as a dedicated panel view.

## Requirements

### Requirement: Right-hand Copilot panel
The SQL workspace SHALL host a right-hand Copilot panel on `/sql-editor`. The panel SHALL be toggleable from the toolbar and `Cmd/Ctrl+L`. Input SHALL be disabled until the active tab is a SQL tab bound to a data source and NAMESPACE. Welcome and table-object tabs SHALL NOT offer Copilot send. The panel SHALL stream Agent replies, render assistant Markdown, extract `sql` fences for **插入** and **插入并运行**, and SHALL keep **用 AI 修复** on failed results as an automatic send.

#### Scenario: Open Copilot on a bound SQL tab
- **WHEN** the user opens Copilot on a SQL tab that has a data source and NAMESPACE
- **THEN** the panel input is enabled and shows that connection on the context bar

#### Scenario: Unbound tab cannot send
- **WHEN** the active tab has no data source or no NAMESPACE
- **THEN** Copilot input is disabled with a reason to select a connection first

### Requirement: User-scoped Copilot conversations
Copilot conversations SHALL be identified by the AI service `conversationId`, not by SQL tab id. The frontend SHALL call Agent and conversation APIs with the existing user Bearer token and MUST NOT send `userId` in JSON. Closing a SQL tab MUST NOT delete the current conversation or its messages. Switching SQL tabs MUST keep the current conversation and MUST use the newly active tab for this-turn editor context.

#### Scenario: Close a SQL tab without dropping chat
- **WHEN** the user has messages in Copilot and closes that SQL tab while another bound tab remains
- **THEN** the Copilot messages remain and no conversation delete request is sent

#### Scenario: Agent request omits userId
- **WHEN** the user sends a Copilot message
- **THEN** the Agent JSON body includes `message`, `userText`, `datasourceId`, `database`, and optional `conversationId`, and does not include `userId`

#### Scenario: Tab switch keeps the thread
- **WHEN** the user switches from SQL tab A to SQL tab B with the same Copilot conversation open
- **THEN** the panel still shows that conversation and the next send uses tab B's data source, NAMESPACE, and editor SQL in `message`

### Requirement: Visible text vs editor context
Each Copilot send SHALL put the user's typed sentence (or the fix template) in `userText` and SHALL put editor dialect, current SQL, and last-error context in `message` via the existing context builder. User bubbles SHALL display `userText`, not the full `message`.

#### Scenario: Bubble shows the typed sentence
- **WHEN** the user types "列出订单" and the builder prepends editor SQL to `message`
- **THEN** the user bubble text is "列出订单"

### Requirement: Conversation history in the Copilot panel
The Copilot panel SHALL list the current user's conversations from `GET /api/v1/ai/agent/conversations`, allow opening one via `GET .../{id}`, starting a new conversation, and deleting one via `DELETE .../{id}` after confirm. Opening `/sql-editor` SHALL NOT auto-resume the latest conversation. Logout SHALL clear local Copilot state so a later login cannot see the previous user's bubbles.

The panel SHALL treat conversation history as a dedicated view, not a strip above the chat. The 历史 control SHALL toggle that view on and off, SHALL appear selected while the history view is showing, and SHALL work the same when the current chat is empty and when it has messages. The history view SHALL replace the message list and composer, SHALL use the remaining panel height, and SHALL scroll when the list does not fit. Opening a conversation or starting a new conversation SHALL return to the chat view. An empty chat view SHALL NOT show the conversation list until the user opens 历史.

#### Scenario: Resume a past conversation
- **WHEN** the user opens a conversation from the Copilot list
- **THEN** the panel shows that conversation's stored user and assistant messages and the next send reuses its `conversationId`

#### Scenario: New conversation
- **WHEN** the user starts a new conversation and sends a message
- **THEN** the Agent request omits `conversationId` or sends none, and the panel stores the id returned on `start` or `completed`

#### Scenario: Delete a conversation
- **WHEN** the user confirms delete on a listed conversation
- **THEN** the frontend calls DELETE for that id and, if it was current, shows the empty Copilot state

#### Scenario: Logout drops local chat
- **WHEN** the user logs out with Copilot messages on screen
- **THEN** local Copilot messages and current conversation id are cleared

#### Scenario: Other user's history is not shown
- **WHEN** tests serve two users' conversation lists from MSW
- **THEN** each session's list contains only that user's items

#### Scenario: Empty chat does not show history
- **WHEN** the Copilot panel has no messages and the user has not opened 历史
- **THEN** the conversation list is not shown and the composer remains available

#### Scenario: History button opens the list from an empty chat
- **WHEN** the Copilot panel has no messages and the user chooses 历史
- **THEN** the conversation list replaces the composer, the 历史 control appears selected, and choosing 历史 again returns to the empty chat view

#### Scenario: History button opens the list while chatting
- **WHEN** the Copilot panel is showing messages and the user chooses 历史
- **THEN** the conversation list replaces the messages and composer, and the 历史 control appears selected

#### Scenario: Opening a conversation leaves the history view
- **WHEN** the user is on the history view and opens a listed conversation
- **THEN** the panel returns to the chat view with that conversation's messages

#### Scenario: New conversation leaves the history view
- **WHEN** the user is on the history view and starts a new conversation
- **THEN** the panel returns to the empty chat view with the composer available

### Requirement: Multi-turn follow-up
After the first successful turn, subsequent sends in the same current conversation SHALL include the returned `conversationId`. A `404 CONVERSATION_NOT_FOUND` on send or GET SHALL start a new conversation and show a short notice. Cancelled in-flight replies SHALL drop the partial assistant bubble locally.

#### Scenario: Follow-up reuses conversation id
- **WHEN** the user sends a second message after a completed first turn
- **THEN** the Agent request `conversationId` equals the id from the first turn's `start` or `completed` event

#### Scenario: Missing conversation on resume
- **WHEN** GET or send returns 404 for the current conversation id
- **THEN** the panel treats it as gone, does not keep sending that id, and shows a short notice
