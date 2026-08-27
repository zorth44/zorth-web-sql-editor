## 1. Panel view state

- [x] 1.1 Replace `showHistory = historyOpen || !messages.length` with `view: 'chat' | 'history'`; 历史 toggles the view and uses `activity-btn-active` plus `aria-pressed` while on history
- [x] 1.2 History view renders the list (or empty copy) in place of messages and composer; opening a row or 新对话 returns to chat; delete stays on history
- [x] 1.3 Drop `.copilot-history` `max-h-36` / `shrink-0`; list fills remaining panel height and scrolls

## 2. Tests

- [x] 2.1 Update `CopilotPanel.test.ts`: empty chat hides the list until 历史; toggle selected state; open/new leave history; delete stays on history
- [x] 2.2 Update `web/e2e/copilot.spec.ts` so resume/delete go through 历史 instead of assuming the list on empty chat
- [x] 2.3 Run Copilot unit tests and the Copilot e2e spec
