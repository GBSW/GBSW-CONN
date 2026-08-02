---
version: 1
slug: "frontend-app-proposals-page-tsx"
primary_target: "frontend/app/proposals/page.tsx"
related_targets: ["frontend/app/page.tsx","frontend/app/admin/page.tsx","frontend/app/moderation/page.tsx"]
---

# Proposal platform surface brief

## Direction contract

- **THESIS:** A calm civic workspace where student proposals read as accountable public records, not social-media posts.
- **OWN-WORLD:** Warm neutral system surfaces, compact institutional typography, quiet one-pixel separators, system light/dark mode, and status conveyed by A stryx semantic components.
- **STORY:** See what needs attention, understand how support becomes a formal agenda, contribute safely, then follow the school's response without losing the record.
- **FIRST VIEWPORT:** A text wordmark and short TopNav lead directly into a clear page title, proposal action, small filters, and the first dense proposal rows. The feed is the focal object.
- **FORM:** Full-page AppShell. Public and student areas use TopNav; staff tools use SideNav. Lists are rows, tables, or metadata—not card grids. Cards are reserved for genuinely distinct choices or security-sensitive one-time information.

## Approved reference

`.impeccable/mocks/proposal-feed-topnav-rows.png`

## Design grammar

- A stryx Neutral provides the color, spacing, radius, type, elevation, focus, and system dark-mode contract.
- Borders are quiet single-token separators; elevation is rare and only marks a truly raised surface.
- Headings have an obvious but restrained scale. Body copy stays within a readable measure.
- Dense rows lead with proposal title and supporting copy, then expose status, support count, and progress in stable scan columns.
- No school logo or official brand colors are implied. The text wordmark remains provisional.

## Visible ingredient inventory

| Ingredient | Commitment | Medium |
| --- | --- | --- |
| Product identity | Text wordmark at the navigation start | Semantic text in TopNavHeading |
| Primary navigation | Home, proposal feed, write proposal, role-specific tools | A stryx TopNav / SideNav |
| Page hierarchy | One clear title, short explanation, contextual actions | Heading, Text, Button, Toolbar |
| Proposal feed | Compact filters and dense separated rows | TextInput/Selector, List/ListItem, ProgressBar, StatusDot/Token |
| Primary action | “공개 제안 작성” remains prominent but restrained | A stryx primary Button link |
| Staff records | Scannable account/case/assignment records | A stryx Table/List and Panel layouts |
| Feedback states | Loading, empty, error, success, disabled | Spinner/EmptyState/Banner/Button states |
| Imagery | None required by the approved world | Accepted omission; product is record-led |

## Non-literal adaptations

- The mock's exact English labels are replaced with real Korean product copy.
- Administrative and moderation surfaces inherit the same grammar but use SideNav and denser record layouts.
- Mobile uses the AppShell navigation drawer and collapses row metadata below titles while preserving reading order.
