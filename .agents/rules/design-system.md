# XIAOAI Design System

> **Project**: XIAOAI — Intelligent Data Q&A Platform
> **Stack**: Vue 3 + TypeScript + Element Plus + TailwindCSS 4
> **Visual direction**: enterprise data console — calm, spacious, precise, and technically credible

XIAOAI is a professional data product, not a marketing site. The interface should feel like a modern operational console: clear hierarchy, generous whitespace, quiet surfaces, strong typography, and focused use of the blue/cyan brand palette.

## 1. Design Principles

1. **Clarity before decoration**: every visual treatment must improve scanning, orientation, or confidence.
2. **One primary action per surface**: use secondary/quiet styles for supporting actions.
3. **Use real product state**: dashboards and Hero cards should show live counts, service names, endpoints, and statuses whenever available.
4. **Density is contextual**: overview surfaces are spacious; operational tables remain compact but breathable.
5. **Dark surfaces are intentional**: the sidebar and dashboard signal card use deep navy as brand surfaces. Do not darken every panel.

## 2. Application Shell

The product uses one consistent shell for authenticated pages:

```
┌────────────────────────────────────────────────────────────┐
│ Header — 64px, brand, theme/locale/help/user actions        │
├───────────┬────────────────────────────────────────────────┤
│ Sidebar   │ Main content — the only vertical scrolling area │
│ 216/72px  │                                                │
└───────────┴────────────────────────────────────────────────┘
```

Rules:

- Keep the header and sidebar fixed in the application frame; only `.app-main` scrolls.
- Sidebar width is `216px` expanded and `72px` collapsed. Persist the collapsed state.
- Sidebar navigation uses 18px Lucide icons. Labels are hidden when collapsed; every item keeps a `title`.
- Active navigation uses a pale-blue pill with a blue icon/text treatment; do not reintroduce heavy gradient menu items.
- Do not place page content inside extra nested white frames. Pages should sit directly on the canvas.

## 3. Color Tokens

Use Element Plus semantic tokens for state, text, borders, and form controls. Use XIAOAI tokens for brand surfaces and accents. Raw hex values belong only in `frontend/src/styles/index.scss` where tokens are defined.

### 3.1 Brand tokens

| Token | Purpose |
|---|---|
| `--xiaoai-canvas` | Main application canvas |
| `--xiaoai-blue` | Primary brand blue and gradient start |
| `--xiaoai-cyan` | Secondary brand accent and gradient end |
| `--xiaoai-cyan-bright` | High-emphasis accent on dark surfaces |
| `--xiaoai-cyan-soft` | Numeric highlight on dark surfaces |
| `--xiaoai-success-soft` | Positive status on dark surfaces |
| `--xiaoai-sidebar-start` / `--xiaoai-sidebar-end` | Sidebar gradient |
| `--xiaoai-ink-strong` | Primary text on dark surfaces |
| `--xiaoai-ink` | Secondary text on dark surfaces |
| `--xiaoai-ink-muted` | Metadata text on dark surfaces |
| `--xiaoai-header-bg` / `--xiaoai-header-border` | Header surface and border |

### 3.2 Element Plus tokens

| Token | Purpose |
|---|---|
| `--ep-color-primary` | Primary action, links, active controls |
| `--ep-color-success` / `warning` / `danger` / `error` | Semantic states |
| `--ep-bg-color` | Cards, dialogs, tables, panels |
| `--ep-fill-color-light` / `lighter` | Subtle fills and hover surfaces |
| `--ep-text-color-primary` / `regular` / `secondary` | Text hierarchy |
| `--ep-border-color` / `lighter` | Borders and separators |

Rules:

- Primary actions use `--ep-color-primary`; never introduce page-specific primary colors.
- Destructive actions use `--ep-color-danger` and require confirmation.
- Status must not depend on color alone; pair color with a label or icon.
- Use translucent gradients sparingly and only for shell/hero emphasis.

## 4. Typography

Use the existing system font stack:

```css
-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif
```

Recommended scale:

| Level | Size | Weight | Usage |
|---|---:|---:|---|
| Hero title | `clamp(32px, 4.6vw, 46px)` | 780 | Home Hero only |
| Page title | 24px | 700–720 | List/detail page headings |
| Section title | 18–20px | 700 | Dashboard sections |
| Card title | 15–17px | 650–700 | Cards and panels |
| Body | 13–14px | 400–500 | Descriptions, table content |
| Metadata | 11–13px | 500–600 | Captions, labels, endpoints |
| Micro label | 10–11px | 700–800 | Eyebrows, badges, status chips |

Rules:

- Use sentence hierarchy through weight and color, not excessive uppercase text. Uppercase is reserved for small eyebrows/labels.
- Technical endpoints, IDs, and SQL fragments use monospace.
- Long text truncates in tables and cards; never overflow the shell.

## 5. Spacing, Radius, and Elevation

Spacing uses the Tailwind 4px scale. Typical values:

| Context | Spacing |
|---|---|
| Icon-to-text | 6–10px |
| Dense control groups | 8–12px |
| Form rows and actions | 12–16px |
| Card internal padding | 20–28px |
| Dashboard section gaps | 20–24px |
| Major Hero padding | 32–40px |

Shape and elevation:

- Base control radius: `9px`.
- Navigation pills and small cards: `12–14px`.
- Dashboard cards, tables, and detail panels: `14–18px`.
- Hero panels: `24–28px`.
- Shadows should be soft and low-contrast; use stronger shadows only in dark mode.

## 6. Core Component Patterns

### 6.1 Header

- Brand lockup on the left: logo, product name, tagline, and version badge.
- Actions on the right: theme, locale, help/docs, user menu.
- Use quiet ghost buttons for icon actions with `aria-label` and `title`.

### 6.2 Sidebar

- The sidebar is a light neutral navigation rail in light mode and a deep navy rail in dark mode.
- Section caption (`Workspace`) is subtle and collapses to a minimal marker.
- Collapse control sits at the bottom and has an accessible label.
- Icons should remain legible at 18px in both expanded and collapsed states.

### 6.3 Home dashboard

The home page is a data console, not a static landing page:

1. Hero panel with product proposition and primary action.
2. Dark signal card showing real MCP service and live counts.
3. Three module cards for Data Sources, Data Subjects, and MCP Services.
4. Workflow path connecting the same three modules.
5. Small capability cards for security, semantics, and integration.

Module cards are clickable, keyboard-focusable, and navigate on Enter.

### 6.4 Tables and list pages

- List pages use a flat page heading, not an elevated or gradient header card.
- Toolbar controls belong in `DataTableShell`'s `toolbar` slot so search/filter, table, and pagination read as one workbench surface.
- Table surfaces are white/neutral panels with a thin border, `14px` radius, and very low-contrast shadow. Do not box a table inside another card.
- Tables use quiet light-gray headers, horizontal row separators, compact action links, and no zebra striping.
- Table body text is `13px`; standard operational rows should remain close to `40px` high unless content genuinely requires expansion.
- Loading uses `v-loading`; empty states use `el-empty` with contextual copy.

### 6.5 Detail pages

- Start with a flat breadcrumb/title/action header directly on the canvas. Do not wrap it in a card.
- Use `SegmentedTabs` for detail-page navigation: a full-width neutral rail, equal visual weight across tabs, rectangular active state, and keyboard navigation.
- Content panels are independent quiet surfaces; avoid multiple nested frames and avoid colored gradient panel borders.
- MCP detail actions are semantically distinct: publish uses `Rocket`, test uses `Play`, enable/disable use `Power`/`PowerOff`.

### 6.6 Semantic chips and overflow

- Alias chips use `.semantic-alias-tag`: neutral gray fill, neutral border, non-monospace text.
- Linked-entity chips use `.semantic-entity-tag`: pale blue fill, blue text, monospace only for technical entity references.
- Table cells use `.semantic-chip-row` with fixed column proportions. Chips truncate instead of stretching columns or rows.
- Show a small representative set (normally three aliases or one linked entity), then collapse count overflow into a keyboard-accessible `+N` control. When there is no count overflow, keep a compact detail control so clipped text remains keyboard/touch accessible.
- Overflow/detail controls open a detail card that exposes every item and the supported edit/remove/add actions. Never hide information behind a bare count.
- Prefer the shared `AliasOverflowCell` for alias columns; use feature-specific detail cards only when the cell needs custom entity actions.

## 7. Icons

- Prefer `@lucide/vue` for product icons.
- Use Element Plus icons only where already established or where no Lucide equivalent exists.
- Never use emoji as structural icons.
- Sizes: navigation 18px; buttons, table actions, and copy actions 16px.
- Icon-only buttons require accessible names and visible focus states.

## 8. Dark Mode

- Test every page in light and dark mode.
- Dark mode uses a near-black blue canvas and brighter blue primary tokens.
- Keep the sidebar visually stable across themes; it is a brand surface.
- Avoid pure black cards and overly bright saturated backgrounds.
- Shadows become deeper but softer in dark mode.

## 9. Accessibility

- Provide a skip link to the main content.
- All interactive elements must be reachable and operable by keyboard.
- Focus rings must remain visible in both themes.
- Icon-only buttons need `aria-label` or equivalent accessible text.
- Destructive actions require `ElMessageBox.confirm`.
- Do not communicate status by color alone.

## 10. Anti-patterns

Avoid:

- Nested frames around every page.
- Dense toolbars crowded against tables.
- Large static/mock dashboard numbers when live data is available.
- Raw hex colors in component templates or scoped styles.
- Different primary colors or unrelated accent colors per page.
- Emoji, inconsistent icon families, or unclear repeated icons for different actions.
- Full-page spinners for small sections.
- Text below 11px except micro-labels.
