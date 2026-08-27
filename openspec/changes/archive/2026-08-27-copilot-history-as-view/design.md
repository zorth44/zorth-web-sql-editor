## Context

`CopilotPanel` currently treats history as a strip above the chat:

- `showHistory = historyOpen || !messages.length`
- CSS caps the list at `max-h-36`
- Empty chat already shows the list, so the 历史 button has nothing to open

The list/get/delete APIs and `useCopilotStore` stay as they are. This change is panel view-state and layout only. Left-rail 「历史」 remains SQL execution history.

## Goals / Non-Goals

**Goals:**

- 历史 is a dedicated view that replaces the chat body (messages + composer).
- The 历史 button is a mode toggle with a selected state whenever that view is showing, including on an empty panel.
- The conversation list fills the remaining panel height and scrolls.
- Opening a thread or starting a new conversation returns to the chat view.

**Non-Goals:**

- Search, date grouping, or pagination.
- AI conversation API changes (v1 still returns at most 50 rows).
- Peek of recent threads on the empty chat.
- Moving Copilot rows into the left-rail SQL history.

## Decisions

### 1. Two views, not a collapsible strip

Panel state is `view: 'chat' | 'history'`. Default `'chat'`.

```
chat:    header + context + notices + messages + composer
history: header + context + notices + full-height list
```

Rejected: keep the list stacked above messages. That is the current design; both panes stay cramped in a 340px side panel.

Rejected: empty chat shows recents and 历史 only works after a thread is open. That is why the button feels dead.

### 2. Header buttons drive the view

- 历史 toggles `chat` ↔ `history`. Use the existing `activity-btn-active` class (and `aria-pressed`) when `view === 'history'`.
- Opening a list item sets `view = 'chat'` then emits `open-conversation`.
- 新对话 sets `view = 'chat'` then emits `new-conversation`.
- Deleting a row stays on the history view so the user can keep managing the list.

### 3. Empty chat is a composer, not a list

No messages and `view === 'chat'` shows the existing empty hint and composer. Recents are reached only through 历史. e2e that currently expects the list after 新对话 MUST click 历史 first.

### 4. Layout

`.copilot-history` becomes `flex: 1; min-height: 0; overflow-y: auto`. Drop `max-h-36` and `shrink-0`. Item chrome (title, connection, time, delete) stays.

Store, `listConversations()`, and the 50-row cap are unchanged.

## Risks / Trade-offs

- [e2e/unit assume empty chat shows the list] → Update `CopilotPanel.test.ts` and `web/e2e/copilot.spec.ts` in the same change.
- [50-row cap still hides older threads] → Accepted; search/pagination are a later change. Full-height scroll is enough for the current API.
- [Hiding the composer on the history view] → Intentional. 新对话 or opening a row returns the composer.

## Migration Plan

Frontend-only. No API or data migration. Rollback is a panel revert.

## Open Questions

None. Empty chat has no peek list; 历史 is always the list view.
