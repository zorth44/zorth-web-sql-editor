## 1. Selection model

- [x] 1.1 Add a result-grid selection module for rectangle state, visual-column hit testing from pointer coordinates, row-number/header range expansion, and TSV serialization via `displayCell`
- [x] 1.2 Cover the module with unit tests for single cells, rectangles, pinned visual order, filtered/sorted rows, null/empty/binary cells, and header-less TSV

## 2. Grid interaction

- [x] 2.1 Replace single-cell selection in `ResultGrid.vue` with hover tracking, pointer-captured drag selection, Shift-click/Shift-arrow extension, row-number row select, and header-body column select
- [x] 2.2 Keep column resize, context menu, and type-glyph/sort-icon click from starting a drag; cycle sort only from the type glyph or sort icon
- [x] 2.3 Copy the current selection with Ctrl/Cmd+C and a context-menu copy-selection action; keep footer whole-result copy, CSV export, value panel, filter, pin, and context-menu sort
- [x] 2.4 Add hover and selected-range styles that are weaker than the focused-cell inset chrome, and disable native text selection on the grid

## 3. Verification

- [x] 3.1 Extend `ResultGrid` component tests for hover class, drag rectangle, Shift extend, row-number and header selection, sort-from-glyph, and TSV clipboard copy
- [x] 3.2 Confirm table-object Data pane still mounts the same `ResultGrid` with no extra selection implementation
- [x] 3.3 Run frontend unit/component tests, typecheck, and lint for the result-grid changes
