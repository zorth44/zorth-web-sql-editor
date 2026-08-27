## Context

Copilot lives in `web/` only. `useCopilotStore` maps `tabId → { conversationId: tabId, messages }`. Closing a tab runs `retain(openTabIds)` and drops that thread. Nothing is written to session storage (tested). `streamAgent` sends `conversationId` equal to the tab id and never sends `userId`. Visible text is `userText` locally; the Agent `message` is `buildCopilotMessage` (editor SQL + last error).

`zorth-ai-service` change `agent-conversation-history` will: resolve user from Bearer, persist turns, opt Agent into namespaced Chat Memory, and expose list/get/delete. This editor change is the client of that contract. SQL service APIs stay as they are. Left-rail 「历史」 remains SQL execution history.

Session already exposes `session.user.id`. It is not sent on Agent calls.

## Goals / Non-Goals

**Goals:**

- One user-scoped Copilot conversation at a time, identified by the server `conversationId`.
- History list, new conversation, resume, delete in the Copilot panel.
- Multi-turn follow-up by reusing that id; still attach this-turn editor context on `message` and visible text on `userText`.
- Tab close / tab switch does not delete history. Logout clears local Copilot state.
- MSW implements the new APIs so tests do not need a live AI jar.

**Non-Goals:**

- Java changes in this repo.
- Putting Copilot rows into SQL execution history.
- Sending `userId` in JSON.
- Cmd+K, welcome-page Copilot, or locking a conversation to one datasource.

## Decisions

### 1. Conversations are independent of SQL tabs

The panel has `currentConversationId` (or a pending new thread). The active SQL tab only supplies `datasourceId`, `database`, dialect, and current/failed SQL for **this turn**.

Rejected: keep `threads[tabId]`. That fights durable history and makes "resume last night's SQL help" require the same tab id.

Switching tabs keeps the same Copilot thread; the next send uses the new tab's connection and SQL. If that connection differs from the conversation's last `datasourceId`/`database`, the panel MAY show a short hint; it MUST NOT block send.

Closing a tab MUST NOT call a delete API and MUST NOT drop the in-memory current thread.

### 2. Do not send `userId`; Bearer is enough

`AgentRequest` in this client gains `userText` and keeps `conversationId`. No `userId` field. Isolation is enforced by AI using auth-context. Locally, if any cache key is needed, use `session.user.id` so two logins on one browser do not share Pinia leftovers. Logout already `clearAuth()`; also `copilot.reset()`.

### 3. `userText` vs `message`

```ts
streamAgent({
  message: buildCopilotMessage({ userText, ...editor }),
  userText,
  conversationId,
  datasourceId,
  database,
})
```

History UI and the store's user bubble render `userText`. `message` stays the silent context block + user sentence. If the AI service is old and ignores `userText`, Copilot still functions (worse memory window); MSW and types assume the new field.

### 4. When to mint vs reuse `conversationId`

- Empty panel + first send: omit `conversationId` (or send none). Use the `start`/`completed` id thereafter.
- Resume from list: `GET` detail, replace `messages`, set current id, next send includes it.
- New conversation: clear messages, clear current id, next send creates a server row.
- `404 CONVERSATION_NOT_FOUND` on send or GET: treat as gone, start a new thread, toast a short message.

Do not use SQL tab ids as conversation ids (128-char limit is fine; the problem is identity and leak across users).

### 5. History UI in the Copilot panel, not the left rail

Left rail stays 数据库 | 历史 (SQL runs). Copilot header: 新对话, a compact list (title + time), delete. Opening the panel with no current thread shows empty state plus recent list. List is loaded when the panel opens and after a successful turn (title appears).

Delete: confirm, `DELETE`, if it was current then empty the panel.

### 6. Reload

Opening `/sql-editor` with a token SHALL fetch the conversation list. It SHALL NOT auto-resume the latest thread (avoids surprising SQL context on a new tab). The user picks from the list, or types to start a new one. In-flight abort still drops the partial assistant bubble locally; the server will not have stored that turn.

### 7. MSW

Handlers for:

- `GET/DELETE /ai-api/api/v1/ai/agent/conversations` and `.../{id}` scoped by the mock user implied by the test token.
- `POST .../agent/stream` that records `userText`/`conversationId` and, on follow-up with the same id, returns a reply that demonstrates prior-turn awareness (e.g. mentions the previous user sentence).

Tests that currently assert "tab isolation and no persistence" change to: tab close keeps messages; two mock users do not share list rows; reload (store remount + GET) restores a saved thread.

## Risks / Trade-offs

- [AI jar not deployed yet] → MSW + types ship in this repo; e2e stays on MSW. Live follow-up needs the sibling service.
- [Same thread, different tab/datasource] → Hint only. Model still gets this-turn `message` context and prior assistant SQL in memory.
- [List on every panel open] → One GET; 50 rows max from v1 API.
- [Old Agent without `userText`] → Harmless extra JSON field if the server ignores unknown properties; if a proxy strips it, memory quality degrades but insert/repair still work.

## Migration Plan

No stored Copilot data in this app. Deploy the frontend after or with the AI conversation APIs. Until AI is up, MSW/dev mock serves empty lists. Rollback is a frontend revert; server rows remain until the user deletes them.

## Open Questions

None. Independent conversations, no auto-resume on load, no `userId` in the body.
