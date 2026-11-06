---
name: 버스 불편 신고 관리자 (Admin)
description: 연한 청회색 바탕에 흰 면과 1px 선, 파랑 하나로 일하는 사무실 PC용 관리자 화면.
colors:
  bg: "#f2f5fa"
  surface: "#ffffff"
  surface-2: "#f8f9fc"
  hover: "#f6f9ff"
  line: "#e1e6f0"
  line-strong: "#cbd3e1"
  ink: "#1b2433"
  ink-2: "#4f5b6e"
  ink-3: "#5f6b80"
  primary: "#243b8f"
  primary-dark: "#19296b"
  primary-weak: "#e9ecf8"
  primary-weak-hover: "#d8dcf0"
  received: "#5b3ea8"
  received-bg: "#efeafa"
  processing: "#0b6aa3"
  processing-bg: "#e3f1fa"
  done: "#25683f"
  done-bg: "#e2f3e8"
  bad: "#b3261e"
  bad-bg: "#fdeceb"
  bad-bg-hover: "#f9d9d6"
  warn: "#8a5200"
  warn-bg: "#fff2d9"
  bar-muted: "#7f8da6"
  grade-high: "#E53935"
  grade-mid: "#F9A825"
  grade-low: "#43A047"
typography:
  num:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "32px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.5px"
  title:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: 1.3
  heading:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "20px"
    fontWeight: 700
    lineHeight: 1.3
  body:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  logo:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "22px"
    fontWeight: 700
    lineHeight: 1.5
  body-strong:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "16px"
    fontWeight: 700
    lineHeight: 1.5
  label:
    fontFamily: "Pretendard Variable, Pretendard, system-ui, Malgun Gothic, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: 1.5
rounded:
  xs: "6px"
  control: "8px"
  card: "10px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  page-x: "32px"
  row: "48px"
  control: "40px"
  header: "64px"
  tabs: "56px"
  gutter: "25px"
components:
  button-default:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    height: "{spacing.control}"
    padding: "0 14px"
  button-default-hover:
    backgroundColor: "{colors.bg}"
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    rounded: "{rounded.control}"
    height: "{spacing.control}"
  button-primary-hover:
    backgroundColor: "{colors.primary-dark}"
  button-danger:
    backgroundColor: "{colors.bad-bg}"
    textColor: "{colors.bad}"
    rounded: "{rounded.control}"
    height: "{spacing.control}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    height: "{spacing.control}"
    padding: "0 12px"
  panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
    padding: "20px 24px"
  table-head:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink-2}"
    typography: "{typography.body-strong}"
    height: "44px"
  table-row:
    backgroundColor: "{colors.surface}"
    height: "{spacing.row}"
  table-row-hover:
    backgroundColor: "{colors.hover}"
  route-chip:
    backgroundColor: "{colors.primary-weak}"
    textColor: "{colors.primary}"
    rounded: "{rounded.pill}"
    padding: "2px 10px"
  status-received:
    backgroundColor: "{colors.received-bg}"
    textColor: "{colors.received}"
    rounded: "{rounded.pill}"
  status-processing:
    backgroundColor: "{colors.processing-bg}"
    textColor: "{colors.processing}"
    rounded: "{rounded.pill}"
  status-done:
    backgroundColor: "{colors.done-bg}"
    textColor: "{colors.done}"
    rounded: "{rounded.pill}"
  tab-active:
    backgroundColor: "{colors.primary-weak}"
    textColor: "{colors.primary}"
    height: "{spacing.tabs}"
---

# Design System: 버스 불편 신고 관리자 (Admin)

Scope: the four admin tabs (대시보드, 신고 관리 list and detail, 노선 관리, 버스 혼잡도). The user-facing pages (user_design.css, black/white pill style) are a different system and are not described here. Desktop browser only; the `@media` rules in the admin CSS are harmless leftovers, not a supported mobile layout.

## Overview

**Creative North Star: "The Quiet Ledger"**

A calm office-standard sheet with less on it: one surface color, one line weight, five text steps (32 / 28 / 20 / 16 / 14). The page is a pale blue-gray ground with white surfaces separated by 1px lines, never by shadow or tint. One navy carries navigation and action. Status has its own colors so navy never has to mean "state". It replaced a wireframe-stage look; the summary comes first and detail is one click away.

The admin checks the same places every day, so nothing moves, glows, or decorates. Density is moderate: 48px table rows, 40px controls, 16 to 24px gaps.

**Key Characteristics:**
- Ground `bg`, surfaces white, separation by 1px `line`, no resting shadows.
- One accent (navy) for links, primary actions, selected state, and route identity.
- Status colors are separate from the accent and mean the same thing on every page.
- Five text steps in order, 32 (summary figure) > 28 (page title) > 20 (block heading) > 16 (body) > 14 (label), plus the 22px logo wordmark, tabular figures everywhere.
- Pretendard (CDN) only; Korean-first.

## Colors

Cool blue-gray neutrals, one navy, and a small fixed set of meaning colors.

### Primary
- **Ledger Navy** (`primary`): links, primary buttons, the selected tab (navy bold text, 4px underline, `primary-weak` fill), route-number pills (filled), the logo mark, selected chips and toggles, bar fill for route counts, the AI action buttons, the current page number. It is a deep navy, about 10:1 on white. `primary-dark` is its hover; `primary-weak` / `primary-weak-hover` are its tinted selected and info backgrounds.

### Secondary (status, meaning fixed)
- **Received Violet** (`received` on `received-bg`): 접수.
- **Processing Sky** (`processing` on `processing-bg`): 처리중.
- **Done Green** (`done` on `done-bg`): 완료, success messages, and "quieter than usual" in the forecast alert.
- **Alarm Red** (`bad` on `bad-bg`, hover `bad-bg-hover`): urgent, delayed, open-report badges, destructive buttons, errors.
- **Caution Amber** (`warn` on `warn-bg`): notices and errors from the AI/forecast servers; dark text on pale amber.

### Neutral
- **Ground** (`bg`) page background, also the hover fill of default buttons and the track of bars. **Paper** (`surface`) every card, table, header, tab strip. **Table Head** (`surface-2`) header cells and detail-table label cells. **Hover Wash** (`hover`) table-row and summary-item hover.
- **Hairline** (`line`) all dividers and card borders; **Control Edge** (`line-strong`) input, button, and chip borders.
- **Ink** (`ink`) text; `ink-2` secondary text, table headers, stat labels and period notes (lookup time, period, the AI privacy sentence); `ink-3` hints, placeholders, empty states, disabled text. Secondary text is held at 4.5:1 or better on white.
- **Muted Bar** (`bar-muted`): the bar color for the second (type) series so it reads as subordinate to the navy route series.

### Congestion grade (map and chart)
`grade-high` (red), `grade-mid` (amber), `grade-low` (green) color map lines, list dots, and legend. The same three hex values are duplicated in `dashboard_map.js` (`GRADE_COLOR`, `ALERT_UP_COLOR`, `ALERT_DOWN_COLOR`); the CSS tokens and the script are two copies and must be edited together; the same goes for the hard-coded navy copies (favicon SVG, user-page logo, the dashboard_map.js ridership bar), which follow `primary` by hand. The route lines must stay distinct from the Kakao road colors (orange, yellow).

### Named Rules
**The One Navy Rule.** Navy means "go here / this is chosen / this is a route". It never means a status.
**The Same Meaning, Same Color Rule.** Quiet/free is green, crowded/urgent is red, a route is a filled navy pill, on every page. A new status gets a new color, not a reused one.

## Typography

**Font:** Pretendard Variable (CDN, dynamic subset) with Pretendard, system-ui, Malgun Gothic, sans-serif. Tabular numerals on the whole body.

**Character:** neutral and legible; hierarchy comes from weight and the gray step, not from extra sizes.

### Hierarchy
- **Num** (700, 32px, 1.2, -0.5px): the summary figures only (dashboard counts, side-panel counts). The "past period" figures drop to the 20px heading size in secondary ink.
- **Title** (700, 28px, 1.3): the page title.
- **Heading** (700, 20px, 1.3): every h3 on every admin screen (dashboard blocks, stat groups, AI box, attachments, process form, route add form, congestion panels). One size, set once in the shared rules; pages do not override it.
- **Body** (400, 16px, 1.5): everything else, including tab labels, buttons (weight 500).
- **Logo wordmark** (700, 22px): the service name in the header, beside the bus mark. A named exception role, same size as the user-facing wordmark; it is not a body size.
- **Body strong** (700, 16px, 1.5): table headers and detail-table label cells. Same size as cell text; separated by weight, `ink-2` color and the `surface-2` background.
- **Label** (600, 14px): status pills, captions, legend. Period notes are 14px regular in `ink-2`; a stat label (the caption under or beside a summary figure) is body size, 16px in `ink-2`.

### Named Rules
**The Five Steps Rule.** Text is 32, 28, 20, 16, 14, each with one role; the 22px logo wordmark is the one named exception. A new size needs a new role, not a new value, and a heading never gets a page-local size.

## Layout

Fixed 64px white header, then a 56px white tab strip on the same band (1px bottom line). Header, tab strip and content share one 25px side gutter (`gutter`), which is also the page rhythm unit; the strip's side padding is the gutter minus the tab's own 18px, so the first tab label starts on the gutter line, the same left line as the content. Content is centered, max 1440px for every admin screen (the congestion page included), padded gutter on top and sides and 48px at the bottom. The gutter also sets the page-title bottom margin and the gap between page sections (dashboard blocks, the two-column stat gap, the AI box top margin, search/add bar bottom margin, attachment and process-form top margin, paging top margin). The congestion page keeps the same frame, uses the gutter for its bottom padding too, and puts its work area in one 1px-bordered, 10px-radius white card whose height is the viewport minus header, tabs and two gutters (min 560px). The user-facing header uses the same 25px side padding so its logo starts at the same x as the admin header (a shared value, the user system is otherwise separate). The header and tab strip stay full width. Inside the card are three columns: 300px route list, map over forecast at 6:4, 340px side panel, divided by hairlines.

Spacing rhythm is 4 / 8 / 12 / 16 / 24 px inside components and the 25px gutter between sections. Card inner side padding is 24px everywhere. Tables are 100% width with 44px header and 48px rows, 16px cell padding. Column alignment follows one rule (see Tables). Dashboard: a one-line "to handle now" strip (items divided by hairlines), a secondary past-period line, then two equal columns of bar rows (40px each) showing the top 5 with a native details disclosure "더 보기" for the rest. Content on the dashboard will change (route reports plus ridership forecast); the block pattern (summary strip, bar list, disclosure) is the system, the specific figures are not.

The `@media (max-width: 720px / 900px)` rules stack columns and scroll tables; the product is desktop-only, so treat them as unmaintained leftovers.

### Named Rules
**The Two Widths Rule.** Wide screens (dashboard, report list, bus congestion map) use the 1440px frame. Simple list-and-form screens (route management, report processing) wrap their content in a centered 1040px column; tables, attachments and forms inside fill that column and carry no width cap of their own. A new screen picks one of the two widths.

## Elevation & Depth

Flat. Depth is a 1px `line` border on white against the `bg` ground. Hover is a tonal wash (`hover`, `bg`, or `primary-weak`), never a lift. Selection is a `primary-weak` fill with a `primary` border.

### Shadow Vocabulary
- **Floating tooltip** (`box-shadow: 0 4px 12px rgba(27, 36, 51, 0.16)`): the map hover tooltip only, because it floats over a busy map and must separate from it.

### Named Rules
**The Line Not Shadow Rule.** Anything in the page flow separates with a line. A shadow is allowed only for something that floats over the map.

## Shapes

One card radius (`card`, 10px) for cards, tables, strips, answer boxes, tooltips' parent surfaces. Controls (inputs, buttons, route buttons, paging, toggles) use `control`, 8px. Small date buttons use `xs`, 6px. Pills (`pill`) are for route numbers, status badges, chips, open-report badges, and bar tracks. Dots and the stop markers are circles. Borders are always 1px, except the 4px tab underline.

## Components

### Buttons
- **Shape:** 40px high, 8px radius, 0 14px padding, weight 500, no wrap.
- **Default:** white, `line-strong` border, `ink` text; hover fills `bg`. Disabled: `ink-3` text on `bg`.
- **Primary:** `primary` fill and border, white text; hover `primary-dark`.
- **Danger:** `bad` text on `bad-bg`; hover `bad-bg-hover`.
- **AI button:** outlined navy (`primary` border and text), hover `primary-weak`; the solid variant is a primary button.
- **Focus:** 2px `primary` outline with 2px offset on links, buttons, and fields.

### Inputs / Fields
- 40px, white, `line-strong` border, 8px radius, 12px padding, inherited font; placeholder `ink-3`. Focus turns the border `primary` (plus the outline). Textarea is auto height, 10px 12px padding, line-height 1.6.

### Cards / Containers
- **Panel:** white, 1px `line`, 10px radius, 20px 24px padding. Form bars (search, add) use the same surface with 16px 24px padding (the filter card) or 20px 24px (the route add card); the process form is 20px 24px and summary strip items 18px 24px. Answers and notices use filled backgrounds (`primary-weak` for AI answer and info, `warn-bg` for errors) with no border.

### Tables
- White, 1px `line` border, 10px radius, clipped. Header 44px, `surface-2`, 16px 700 `ink-2` (same size as cell text, distinguished by weight, color and background; the report-detail label cells follow it). Rows 48px (64px in tables whose cells hold a 40px control, so the control has 12px of air above and below; route management), hairline between, last row borderless. Row hover `hover`. Links in cells are navy and stay underlined with a faint `line-strong` underline (3px offset) that darkens on hover. Alignment: in list tables (`list-table`: report list, route management) short fixed-length values and their headers are centered, long free text (report content, route name) is marked left and stays left. The report-detail label/value table stays left-aligned. Actions sit next to the thing they act on: in route management the name field, the rename button and the delete button share one row in the name column (field takes the remaining width), with no separate action column at the far edge. Empty state: 48px padding, centered, `ink-3`.

### List toolbar
- **The List Toolbar Rule.** Above a list, left is the total count and right are the tools that filter that list; a form's submit button sits at the end of the last input row, and the primary (조회/검색) and reset buttons share one width (min 88px) so they read as a pair.
- **Count line (`list-head`):** a flex row, space-between, 12px bottom margin. The count ("전체 N건") is 16px, weight 500, `ink-2`.
- **Filter card (report list):** one bordered 10px-radius card with two deliberate rows. Row 1: status, type, route number (120px), and a keyword field that flexes to fill the rest. Row 2: date range, sort, count (the date inputs and the two selects stretch to share the free width, with maximums (dates 240px, selects 160px), so there is no gap before the buttons), then 조회 (primary) and 초기화 pushed to the right end.
- **Route management:** no separate search card. The count sits left of the table and the search (280px field, 검색 primary, 초기화) sits right. The 노선 등록 card is the only card above the table: route-number field 200px, name field flexes to fill the width. In the table, the name field and its two buttons sit in one row with a 12px gap.

### Navigation
- Tab strip: 56px, tabs 16px with 18px side padding and a 4px bottom border. Inactive tabs are `ink`, weight 500; hover adds a `surface-2` fill and a `line-strong` underline; the active tab is `primary`, bold, with a 4px `primary` underline and a `primary-weak` fill, so the current page reads at a glance. Active tab carries `aria-current="page"`. Header links are `ink-2`, hover navy.

### Route chip and status badge
- **Route chip:** filled `primary` pill, white bold text, hover `primary-dark`, min-width 48px (inside table cells the white text is forced so the link color does not hide it). Every route reference, in every page, uses it.
- **Status badge:** 14px 600 pill, colors per status above.

### Logo mark
- A 36px inline SVG bus in a filled circle (bus body white, details in the circle color) sits left of the service name in the header, 10px gap, 22px bold name. The circle uses `currentColor` set to `primary`, so it follows the accent. The favicon (`/img/logo_bus.svg`) is the same mark with a fixed `#243b8f`, a second copy of the navy. The user-facing header carries the same mark (36px, navy hard-coded in its own stylesheet); that page set is otherwise a separate system and is not documented here.

### Summary strip and bar rows (dashboard)
- The strip is one bordered, 10px-radius row; items split by 1px dividers, label left (16px 500 `ink`, no size override of its own), 32px figure right in the status color. Bar rows are 40px: label, 8px pill track on `bg`, fill (`primary` for routes, `bar-muted` for types), right-aligned count. Overflow sits in a native `details` with a navy "더 보기" summary.

### Map side components (congestion)
- Route list buttons: white, 1px `line`, 8px radius, selected = `primary-weak` + `primary` border + bold; missing data = dashed. Open-report badge: red pill top-right. Direction toggle: two joined 8px-end buttons. Chips: 26px pill, on = navy tint. Stop markers: white fill with a grade-colored border, set by script.
- The forecast chart's navy is hard-coded in `dashboard_map.js` (`#243b8f`), a third copy of `primary`.

## Do's and Don'ts

### Do:
- **Do** take every color, size, radius, and height from `admin_tokens.css`; page CSS may add layout, not new values.
- **Do** keep the default button rule low-specificity (`:where`) so page CSS can override it, as the build does.
- **Do** show a route as the filled navy pill and a status as its status pill everywhere.
- **Do** separate in-flow surfaces with a 1px `line` and use tonal washes for hover.
- **Do** follow the list toolbar rule: count left, filters right, submit at the end of the last input row.
- **Do** align by content: short fixed-length values centered, long free text left, and a header follows its column.
- **Do** put row actions beside the item they change, not in a far-edge column.
- **Do** pick one of the two content widths (1440 wide, 1040 narrow) for a new screen.
- **Do** space page sections with the gutter and give cards 24px inner side padding.
- **Do** put summaries first and detail behind one click (link or `details`).
- **Do** edit `grade-*` tokens and `dashboard_map.js` colors together.
- **Do** keep text at 4.5:1 or better on its surface; use `ink-3` as the floor.

### Don't:
- **Don't** use navy to express a status, or a status color to express navigation or selection.
- **Don't** add tinted stat cards in several hues, colored side stripes on rows, or two-line list rows.
- **Don't** add resting shadows to cards or tables.
- **Don't** introduce a sixth text size or a second card radius for cards.
- **Don't** reach into `common.css` or `user_design.css`; override only under `body.admin`.

### Not canonized
Present in the build but not system rules: the `0 1px 3px` shadow on `.stop-icon` map markers (a map-marker exception, not a card shadow); hard-coded `#fff`, `#222`, `#888` on map markers and labels; and the 6px-radius 28px date buttons and 26px chips (fixed pixel sizes, not scaled with the type tokens), which are map-panel one-offs.
