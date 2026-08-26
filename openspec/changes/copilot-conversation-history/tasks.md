## 1. Agent client and MSW

- [x] 1.1 Extend `AgentRequest` with optional `userText`; keep `conversationId`; do not add `userId`
- [x] 1.2 Add conversation API client: list, get detail, delete, using the existing AI base URL and Bearer fetch
- [x] 1.3 MSW: per-mock-user conversation store; stream handler records `userText`/`conversationId` and on follow-up with the same id returns a reply that reflects the previous user sentence; 404 for the other user's ids

## 2. Copilot store

- [x] 2.1 Replace `threads[tabId]` with current conversation id + messages + list; first send omits `conversationId` and stores the id from `start`/`completed`
- [x] 2.2 Send both `userText` and `buildCopilotMessage` `message`; user bubbles show `userText`
- [x] 2.3 Remove tab-id `retain()` dropping conversations; `reset()` on logout; 404 on send/GET starts a new thread
- [x] 2.4 Unit tests: tab close keeps messages; second send reuses server id; two users' lists stay isolated; logout clears state; request body has no `userId`

## 3. Copilot panel and workbench

- [x] 3.1 Panel chrome: new conversation, list (title + time), open, delete with confirm; empty state plus recent list; do not auto-resume latest on `/sql-editor` load
- [x] 3.2 `SqlEditorPage`: stop `copilot.retain(tabIds)`; tab switch keeps the thread and uses the active tab for this-turn context; logout calls `copilot.reset()`
- [x] 3.3 Load conversation list when the panel opens and after a successful turn; optional short hint if active connection differs from the conversation's last datasource/database, without blocking send

## 4. Tests and verification

- [x] 4.1 Update `CopilotPanel` tests for history actions (new / open / delete)
- [x] 4.2 E2E: send, follow-up reuses id, close SQL tab keeps chat, resume from list, delete; still insert SQL from a fence
- [x] 4.3 Typecheck and unit tests; confirm left-rail History still lists SQL executions only
