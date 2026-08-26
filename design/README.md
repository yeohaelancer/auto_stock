# Handoff: 모의투자(Paper Trading) 자동매매 대시보드

## Overview
A status dashboard for a simulated (paper-trading) automated stock trading system. Shows current position, market/feature data collection pipeline health, daily account snapshots, and order/risk-event history. Purely a read-only monitoring screen plus one control action (emergency stop).

## About the Design Files
The file in this bundle (`Trading Dashboard.dc.html`) is a **design reference built in HTML** — a high-fidelity visual prototype, not production code to copy verbatim. Recreate this UI inside the target codebase's existing stack (React/Vue/etc., whatever the project already uses) using its own component patterns, state management, and data-fetching conventions. If no frontend stack exists yet, React is a reasonable default.

## Fidelity
**High-fidelity.** Colors, typography, spacing, radii, and icon choices below are final. Recreate pixel-close using the target codebase's styling system (CSS variables, Tailwind config, styled-components theme, etc.) — translate the tokens below into whatever token mechanism the codebase already uses.

## Screens / Views
Single screen, one scrollable page, max content width 1080px, centered, generous side padding (scales 16–35px with viewport). Responsive: 3-column stat/metric grids collapse to 1 column ≤860px; nav padding and font sizes shrink ≤520px.

### 1. Top nav bar
- Full-width bar, light surface background, rounded bottom corners (28px radius), 1px border.
- Left: 40px circular icon badge (line-chart icon) + "모의투자 대시보드" wordmark (display font, 22px) + a small pill tag "자동매매 · 모의".
- Right: "긴급정지" (Emergency Stop) button — solid dark-navy-blue pill, white text, warning-triangle icon, bold weight. This is the one interactive control on the page; should trigger a confirmation dialog before actually halting trading, then reflect a stopped state.
- Wraps to two lines on narrow viewports.

### 2. "지금 상태" (Current status) — 3-card stat row
Three equal-width cards, each: white/light-blue surface, 1px border, soft shadow, flex row, 52px circular icon badge + label/value.
1. **보유 포지션** (Position) — neutral icon badge (layers icon), value = position count or "보유 종목 없음" when empty.
2. **일일 손실 한도 대비** (Daily loss-limit usage) — icon badge (shield), big "%" value, thin progress bar underneath (0–100%, fill color from secondary accent ramp).
3. **평가금액 (오늘)** (Today's valuation) — neutral icon badge (bar-chart), formatted currency number.

### 3. "시세/피처 수집 현황" (Market/feature pipeline status) — single wide card
- Card eyebrow label + heading "데이터 파이프라인".
- 3-column metric row, each with a 44px icon badge + label + big number + small caption:
  - 매매 유니버스: "N / M" (active/total tickers)
  - 시세 데이터 (trading_price_history): count + ticker count + last-collected timestamp
  - AI 피처 (trading_feature_daily): count + ticker count + last-computed date
- Below: an info banner (tinted secondary-accent background, info icon) explaining when data will first populate — only shown while the pipeline hasn't run yet; should be replaced/hidden once real data exists.

### 4. "계좌 스냅샷 추이" (Account snapshot history) — table card
- Card eyebrow + heading "일별 자산 현황".
- Standard data table: 일자 / 평가금액 / 현금 / 당일손익 / 손익률 columns, one row per day, most recent first. Currently shows a single seed row.

### 5. Orders / Risk log — tabbed card
- One card containing a pill-shaped tab bar with two tabs: "주문/체결 이력" (Orders) and "리스크 이벤트 로그" (Risk events).
- Active tab: solid dark-navy fill, white text. Inactive: transparent, muted text.
- Below the tabs, only the active tab's content renders:
  - Orders empty state: inbox icon in a circular badge + "아직 주문 이력이 없습니다". When populated, replace with an order table (columns TBD by backend: time, ticker, side, qty, price, status).
  - Risk log empty state: shield-check icon (secondary-accent tint) + "아직 리스크 이벤트가 없습니다 — 모든 안전 장치가 정상 대기 중입니다". When populated, replace with a log list/table (timestamp, severity, message).

## Interactions & Behavior
- Tab switch is local UI state (no navigation), instant swap, no animation required.
- Emergency-stop button should be wired to the real stop-trading action; treat it as destructive/critical — a confirm step before firing is recommended even though the mock has none.
- All numeric "0" values are seed/empty state — component should branch on real vs. empty data (see empty-state copy above) rather than always rendering the zero-state copy.
- No client-side routing; this is one screen.

## State Management
- `activeTab: 'orders' | 'risk'` — local UI state.
- Data needed from backend: position summary, daily-loss-limit %, today's valuation, universe active/total counts, price-history collection stats + last-collected time, feature collection stats + last-computed date, account snapshot rows, order/execution rows, risk event rows.
- Each data section should independently support a loading and an empty state (the empty-state copy shown in the mock is the intended empty copy).

## Design Tokens
Color roles (translate into the target codebase's own token system — these are the values used in the mock, on a **white background / blue accent** direction per latest revision):
- Background: `#ffffff`
- Surface (cards/nav): `#f3f6fb`
- Text: `#142033`
- Divider: `#142033` at ~14% opacity
- Primary accent (blue) ramp 100→900: `#eaf1ff, #cfe0ff, #9fc2ff, #6b9dfa, #3d78f0 (base), #2f5fcf, #2549a8, #1c3980, #142a5c`
- Secondary accent (indigo-blue) ramp 100→900: `#eceffb, #d3daf3, #aab8e6, #7d8fd2, #5567b8 (base), #445299, #35407a, #262e59, #181d3a`
- Neutral ramp 100→900: `#f4f6fa, #e7ebf2, #d3dae6, #b0bccd, #8b99b0, #6b7a94, #505d75, #38435a, #212a3d`
- Shadows: soft, ink-tinted at 14/16/22% opacity for sm/md/lg elevation.

Typography:
- Display/heading font: Caprasimo (decorative display serif) — used for the wordmark and card headings (20–26px).
- Body font: Figtree — used for all labels, numbers, table text.
- Base spacing scale (×1.1 density): 4.4 / 8.8 / 13.2 / 17.6 / 26.4 / 35.2 px steps.
- Radii: 8px small, 16px medium (cards use ~28-32px via a 1.15× multiplier), pill (999px) for buttons/tags/tab bar.

## Assets
No raster images or custom SVG icons beyond inline Lucide-style line icons (stroke-width 2.75, rounded caps): layers, shield, bar-chart, clock, trending-up, sparkles/refresh, info-circle, inbox, shield-check, alert-triangle. Use the target codebase's existing icon library (e.g. `lucide-react`) with matching icon names/stroke width if available.

## Files
- `Trading Dashboard.dc.html` — the full design reference (single self-contained HTML file; open directly in a browser to view).
