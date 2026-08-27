## MODIFIED Requirements

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
