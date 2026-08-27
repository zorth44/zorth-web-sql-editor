## Why

Copilot 把历史名单塞在聊天区顶上一条约 144px 的缝里，空面板时名单已经展开，点「历史」几乎没有反馈。这个交互既不像成熟侧栏对话产品，也撑不住以后几十上百条记录。

## What Changes

- Treat Copilot「历史」as a full-panel view switch, not a collapsible strip above the chat.
- The 历史 button always maps to that view: selected when the list is showing, unselected when chatting. Empty panel and in-progress chat behave the same.
- The conversation list uses the remaining panel height and scrolls. Opening a conversation or starting a new one returns to the chat view.
- Empty chat is a composer ready to type, not a peek of recent threads. Recents live only in the history view.
- **Not in this change:** title search, date grouping, conversation pagination, AI service API changes, or moving Copilot rows into the left-rail SQL 执行历史.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `frontend-sql-copilot`: Conversation history is a dedicated panel view toggled by 历史, not a `max-h-36` list that auto-shows when the chat is empty.

## Impact

- `web/src/components/copilot/CopilotPanel.vue`: history vs chat view state; header button selected state; list layout.
- `web/src/styles.css`: drop the 144px cap; history list fills the panel.
- `web/src/components/copilot/CopilotPanel.test.ts` and `web/e2e/copilot.spec.ts`: empty chat no longer shows the list; 历史 always reveals it.
- No store, API, or Java changes. List still comes from the existing `GET .../conversations` (v1 cap 50).
