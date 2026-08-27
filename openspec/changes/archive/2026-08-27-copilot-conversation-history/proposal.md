## Why

Copilot currently keeps bubbles in Pinia keyed by SQL tab id, never writes them, and drops the thread when the tab closes. The Agent call sends that tab id as `conversationId` and does not send a user identity, so follow-ups rely on stuffing the current editor SQL into `message`, and two users (or two logins on one browser) have no durable, isolated history.

## What Changes

- Treat Copilot conversations as first-class, user-owned threads from `zorth-ai-service`, not as tab-local state. `conversationId` is the server id, never the SQL tab id.
- Load, list, resume, start, and delete conversations through the AI conversation APIs under the existing user Bearer token. Do not send `userId` in the Agent body; isolation is the service's job.
- Keep sending per-turn editor context (`dialect`, current SQL, last error) on `message`, and send the visible prompt separately as `userText` so history and model memory do not accumulate editor dumps.
- Panel: conversation list, new conversation, open a past thread, delete. Closing or switching SQL tabs does not destroy the active conversation; the active tab still supplies this-turn SQL context.
- On logout, drop local Copilot state. Switching accounts MUST NOT show the previous user's bubbles.
- **Not in this change:** SQL execution history, SQL service APIs, inline Cmd+K, persisting conversations in the editor backend, or sending `userId` as an authorization field.

Depends on sibling AI change `agent-conversation-history` (auth-resolved user, Agent Chat Memory, conversation CRUD). Editor MSW covers the new contract so unit/e2e can land before a live AI jar.

## Capabilities

### New Capabilities

- `frontend-sql-copilot`: Right-hand Copilot panel: generate/repair SQL, insert into the active tab, stream Agent replies, and now user-scoped conversation history with multi-turn follow-up.

### Modified Capabilities

- `frontend-sql-editor-workbench`: Copilot is no longer discarded with SQL tabs; the workbench hosts one user-scoped Copilot session while tabs only provide connection and SQL context.

## Impact

- `web/src/stores/copilot.ts`: current conversation + list, not `threads[tabId]`.
- `web/src/api/ai-agent.ts`: `userText`; conversation list/get/delete client.
- `web/src/components/copilot/CopilotPanel.vue`: history chrome (new / list / delete).
- `web/src/pages/SqlEditorPage.vue`: stop `retain()`-by-tab-id; tab switch does not wipe chat.
- MSW, unit tests, e2e: isolation by user session, resume after reload mock, tab close does not delete history.
- No Java service changes in this repository.
