---
name: GBSW 제안
description: 학생 제안과 학교의 공식 대응을 차분한 공공 기록처럼 다루는 A stryx Neutral 웹 시스템
colors:
  accent: "light-dark(#262626, #ebebeb)"
  on-accent: "light-dark(#ffffff, #171717)"
  neutral: "light-dark(#0000000F, #FFFFFF1A)"
  body: "light-dark(#f1f1f1, #1b1b1b)"
  surface: "light-dark(#ffffff, #262626)"
  card: "light-dark(#ffffff, #1b1b1b)"
  muted: "light-dark(#f1f1f1, #1b1b1b)"
  text-primary: "light-dark(#171717, #fafafa)"
  text-secondary: "light-dark(#737373, #a3a3a3)"
  border: "light-dark(#00000014, #FFFFFF1A)"
  border-emphasized: "light-dark(#d4d4d4, #525252)"
  success: "light-dark(#007004, #9fe59b)"
  success-muted: "light-dark(#c5e5c0, #84c9803D)"
  warning: "light-dark(#745b00, #fdcf4f)"
  warning-muted: "light-dark(#f8da9d, #deb4333D)"
  error: "light-dark(#a50c25, #ffc6c1)"
  error-muted: "light-dark(#facecb, #ff9e973D)"
  on-error: "light-dark(#ffffff, #171717)"
typography:
  display-1:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "2.625rem"
    fontWeight: 400
    lineHeight: 1.2381
  display-2:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "2.1875rem"
    fontWeight: 400
    lineHeight: 1.2571
  heading-1:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.3333
  heading-2:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.4
  heading-3:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 700
    lineHeight: 1.4118
  body:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.4286
  label:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 500
    lineHeight: 1.4286
  supporting:
    fontFamily: "Figtree, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.6667
rounded:
  inner: "0.375rem"
  element: "0.625rem"
  container: "0.75rem"
  page: "1.75rem"
  full: "9999px"
spacing:
  step-0-5: "2px"
  step-1: "4px"
  step-1-5: "6px"
  step-2: "8px"
  step-3: "12px"
  step-4: "16px"
  step-5: "20px"
  step-6: "24px"
  step-8: "32px"
  step-10: "40px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.label}"
    rounded: "{rounded.element}"
    padding: "{spacing.step-2} {spacing.step-3}"
    height: "32px"
  button-secondary:
    backgroundColor: "{colors.neutral}"
    textColor: "{colors.text-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.element}"
    padding: "{spacing.step-2} {spacing.step-3}"
    height: "32px"
  button-destructive:
    backgroundColor: "{colors.error}"
    textColor: "{colors.on-error}"
    typography: "{typography.label}"
    rounded: "{rounded.element}"
    padding: "{spacing.step-2} {spacing.step-3}"
    height: "32px"
  input-default:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    typography: "{typography.body}"
    rounded: "{rounded.element}"
    padding: "{spacing.step-1} {spacing.step-2}"
    height: "32px"
  card-default:
    backgroundColor: "{colors.card}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.container}"
    padding: "{spacing.step-4}"
---

# Design System: GBSW 제안

## Overview

**Creative North Star: “The Calm Civic Record”**

The shipped interface is a calm civic workspace where student proposals read as accountable public records, not social posts. It is restrained, neutral, direct, and task-led: hierarchy comes from type, spacing, one-pixel separators, and semantic states rather than decoration.

A stryx Neutral is the visual authority. Public pages make the proposal feed and process easy to understand; staff pages increase information density without becoming a different product. The finish-reviewed desktop, mobile, light, and dark surfaces are the reference behavior.

**Key Characteristics:**

- Neutral institutional surfaces with a provisional text wordmark.
- Dense row-based records, stable metadata, and visible progress.
- Direct Korean copy with explicit privacy, permission, and consequence language.
- System light/dark mode, strong focus states, and reduced-motion support.
- Minimal imagery and rare elevation; function carries the character.

**The Public Record Rule.** Every screen should make the record, its state, and the next permitted action easier to scan. Do not borrow engagement patterns from social feeds.

## Colors

The palette is a grayscale spine with color reserved for meaning. The frontmatter values are the normative A stryx Neutral tokens and switch automatically between light and dark.

- **Accent:** Primary actions, selected navigation, focus, gathering-support progress, and active states.
- **Neutral surfaces:** Body is the canvas; surface is the working layer; muted separates explanatory or supporting regions; card is reserved for bounded objects.
- **Text:** Primary carries titles and records. Secondary carries descriptions, dates, roles, and explanatory metadata. Disabled styling must come from component state, not a hand-picked gray.
- **Semantic color:** Success means completed, approved, formalized, or saved; warning means caution, hold, or expiring attention; error means failed, rejected, invalid, or destructive. Use the matching muted token for banners and tinted containers.
- **Borders:** Use the quiet border for dividers and the emphasized border for fields. Do not introduce darker separators to manufacture hierarchy.

**The Semantic-Only Color Rule.** Product status may use semantic color, but it must always be paired with visible Korean text, an accessible label, or both. Never use color as branding or decoration.

**The Theme Authority Rule.** Change brand or accent color through an A stryx theme package. Never override `--color-*` in `:root` or add raw hex values to application components.

## Typography

Figtree is the shipped heading and body family, falling back to the system sans stack. Korean glyphs therefore resolve through the platform fallback; do not add a remote font dependency without a deliberate typography and performance review. Code and one-time values use the A stryx system-monospace stack.

### Hierarchy

- **Display 1:** Home-page thesis only. It is intentionally lighter and more spacious than operational headings.
- **Display 2:** Proposal feed, proposal detail, admin, and moderation page titles.
- **Heading 1:** Standard form and authentication page title when display scale would be excessive.
- **Heading 2:** Major sections and record titles; use the semantic heading level that matches the document outline.
- **Heading 3:** Row titles and nested operational sections; its bold weight provides compact hierarchy.
- **Body:** Default records and form copy. Use large text only for primary long-form proposal content or important introductory copy.
- **Supporting:** Dates, role labels, counts, helper text, and secondary status detail. Keep important consequences out of supporting text.

Keep explanatory copy within the shipped `72ch` reading measure. Preserve user-authored line breaks with `pre-wrap` and allow long identifiers/content to wrap with `wrap-anywhere`. Truncation is for previews only: feed excerpts use two lines and report previews use four; detail views show the record in full.

**The Restrained Hierarchy Rule.** Use one dominant page title, then semantic headings. Do not create hierarchy with all caps, arbitrary font sizes, or more than the existing display/heading/body roles.

## Layout

The spatial system follows the A stryx 4px-based scale. Most local gaps are 8–24px; major page sections use 32–40px. Use `Stack`, `Grid`, `Section`, `Layout`, and component spacing props before adding CSS.

### Widths and shells

- The shared page frame is fluid up to `72rem`; main content stays centered.
- Explanatory text uses a `72ch` reading measure.
- Proposal and moderation details narrow to `56rem`; proposal composition narrows to `48rem`; authentication narrows to `30rem` and centers within an `85dvh` minimum-height frame.
- Public/student surfaces use `AppShell` + `TopNav`, `variant="section"`, and whole-page scrolling. Staff surfaces add `SideNav` with a 240px default width, resizable from 200–360px.
- Authentication uses a quiet `wash` shell and one low-elevation card. It is a focused exception, not a template for ordinary forms.

### Rows, sections, and cards

- Proposal feeds, accounts, assignments, moderation cases, votes, histories, and official responses are `List`/`ListItem` rows with quiet dividers. Use balanced, spacious, or compact density according to record complexity.
- Keep row scan order stable: title/content first, then status and supporting metadata, then count/progress or the contextual action.
- Use `Section` for page regions, form groups, supporting explanations, and top/bottom dividers.
- Use `Card` only for genuinely distinct choices, authentication, or security-sensitive one-time information. The shipped home path choices, activation/auth card, one-time code, and identity reveal are valid examples. Never wrap every feed row in a card or create a bento dashboard.

### Responsive behavior

The application breakpoint is A stryx `md` (`max-width: 768px`). Public and staff shells both convert navigation into the AppShell drawer at this point.

- Public desktop keeps primary links at the start and the compose/auth actions at the end. Mobile keeps the wordmark and menu trigger visible, moving links, compose, and auth actions into the drawer.
- Staff desktop keeps TopNav plus SideNav. Mobile moves the staff navigation into the drawer; do not create a second bespoke mobile menu.
- Feed filter stacks use wrapping layout. On mobile, fields become naturally full or compact-width rows without horizontal scrolling.
- Proposal row support metadata moves from the desktop end column beneath the title/excerpt and becomes full width. Preserve status, count, threshold, and progress in reading order; never hide them to save space.
- Horizontal action groups wrap. Bounded cards and selectors use `min(100%, …)` sizing instead of fixed overflow-prone widths.

## Elevation & Depth

This system is flat by default. Hierarchy comes from the body/surface/muted tonal ladder and one-pixel dividers. Default cards and buttons have no resting elevation.

Use A stryx low elevation for the authentication card. Medium elevation is reserved for one-time sensitive results such as issued account codes and revealed identity information. Higher elevation belongs only to library-managed overlays such as dialogs and popovers, not ordinary page content.

The shared page reveal uses the A stryx medium duration (300ms) with a small 12px rise and brief blur. It runs once per page surface. `prefers-reduced-motion: reduce` disables that reveal and smooth scrolling; A stryx component transitions also collapse. Do not add independent entrance animation to rows, forms, banners, or progress bars.

**The Flat-by-Default Rule.** If a divider or muted surface expresses the relationship, do not add a shadow.

## Shapes

Element controls use the A stryx element radius, containers use the container radius, and fully rounded geometry is limited to dots, pills, and native library affordances. Borders remain one token wide. Do not mix in sharp custom rectangles or oversized marketing-style rounding.

Inputs, selectors, and buttons share the same gently rounded element silhouette. Sections can remain visually square and edge-to-edge. List rows do not become floating rounded tiles; their interactive hover/selected treatment is handled by A stryx.

## Components

### A stryx foundation

- Runtime dependencies are `@astryxdesign/core` and `@astryxdesign/theme-neutral` v0.2.0.
- Import `reset.css`, `astryx.css`, and the Neutral `theme.css` once in `frontend/app/layout.tsx`.
- Wrap the application in `Theme(theme={neutralTheme}, mode="system")`, then `InternationalizationProvider(locale="ko", dir="ltr")`, then the Next.js `LinkProvider`. Do not mount competing theme or link providers inside routes.
- Build layout from A stryx components; application CSS is limited to durable frame/measure/wrapping helpers and the single page reveal.

### Navigation and actions

Use `PublicAppShell`, `StaffAppShell`, or `AuthPageShell` instead of recreating navigation. The provisional wordmark is “GBSW 제안”. Primary buttons mark the single main action; secondary buttons are neutral alternatives; ghost buttons handle navigation and low-emphasis actions; destructive buttons are only for irreversible or sensitive actions. Loading actions use `isLoading`, and invalid/pending actions use `isDisabled`.

### Forms

Use `FormLayout` with explicit `TextInput`, `TextArea`, `Selector`, `RadioList`, `CheckboxInput`, or `DateTimeInput` labels. Pair required/optional state with descriptions where consequences are not obvious. Keep form actions close to the form, right-aligned when completing a full-page record and full-width for authentication. Never use placeholder text as the only label.

Focus and validation styling belong to the components: emphasized field border at rest, accent border/inset ring on focus, and semantic border/message states for success, warning, and error. Never remove focus-visible outlines. Explain disabled controls with `disabledMessage` when the reason is not already visible.

### Status, progress, and feedback

- Use `StatusDot` plus visible status text for compact record states; use `Token` for removable filters; use `Badge` only for counts or enumerated states.
- Use `ProgressBar` for the 50-support threshold. Show current/threshold numbers while gathering support; after formalization, show the current total and success treatment. Use tabular numbers for counts.
- Use `Spinner` with a Korean action label for initial loading, `EmptyState` for no-access/no-result/no-record states, and `Banner` for error, success, warning, or informational feedback.
- Keep feedback adjacent to the action or region it describes. Never rely on a toast for security, permission, or irreversible-action outcomes.
- One-time codes and identity results use elevated semantic cards, explicit “한 번만 표시” language, expiry/ephemerality guidance, and a deliberate clear/close action.

### Accessibility and Korean localization

Keep `<html lang="ko">`, the Korean A stryx overrides, semantic heading levels, labelled navigation, `aria-labelledby` section relationships, and AppShell keyboard/skip-link behavior. Every icon-only or hidden-label control needs an accessible Korean label. Status must remain understandable without color.

Use factual, direct Korean; avoid promotional claims and playful microcopy. Format dates through `Intl.DateTimeFormat("ko-KR")` with `Asia/Seoul`, and use 24-hour inputs for staff records. Add missing library strings to `frontend/lib/astryx-ko.ts` rather than leaking English defaults into the UI. Design for Korean line wrapping and longer future translations; do not encode meaning in fixed character counts.

## Do's and Don'ts

### Implementation checklist

- **Do** start with the correct AppShell and budget page regions before adding content.
- **Do** use `npx astryx build`, `template`, `component`, or `search` when a component or prop is uncertain; do not guess the A stryx API.
- **Do** use component props first, then existing helpers, then token-based custom styling as a last resort.
- **Do** verify desktop and mobile, system light and dark, keyboard focus, loading, empty, error, success, disabled, and reduced-motion behavior.
- **Do** preserve the public/staff information boundary: staff density may increase, but internal assignees and sensitive identity data must not leak into public patterns.
- **Do** keep mobile navigation in the AppShell drawer and preserve feed status/count/progress after reflow.
- **Do** keep all visible and accessible UI copy in Korean and test long titles, long identifiers, and user-authored line breaks.

### Prohibitions

- **Don't** add school logos, crests, official school colors, a bespoke product logo, or a new brand palette until approved assets exist. Continue using the text wordmark and Neutral theme.
- **Don't** add decorative imagery, gradients, glass effects, hero illustrations, social metrics, or bento/card grids. Imagery is intentionally deferred because this product is record-led.
- **Don't** use raw `div`/`span` elements for layout, Tailwind utilities, app-level StyleX utilities, route-specific stylesheets, raw hex colors, or arbitrary pixel spacing.
- **Don't** override A stryx token variables in `:root`; theme changes belong in a theme package.
- **Don't** use cards for proposal rows, account rows, cases, votes, or history. Use semantic lists/tables and dividers.
- **Don't** show status by color alone, remove labels or focus rings, hide feedback during loading, or make destructive/sensitive actions look routine.
- **Don't** invent brand assets, testimonials, school data, or protected-channel functionality to fill visual space.
