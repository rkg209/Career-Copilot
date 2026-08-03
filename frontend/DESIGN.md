# Career Copilot — Frontend Design System

> **Theme:** Modern SaaS, dark-first (Linear / Vercel style). Violet accent, light + dark with a
> persisted toggle. Built with **Tailwind CSS v4** (CSS-first config in `src/index.css`).
> This is the single source of truth for UI decisions — reuse these tokens and primitives; do not
> hardcode colors in components.

## Stack

- **Tailwind CSS v4** via `@tailwindcss/vite` (no `tailwind.config.js` — config lives in
  `src/index.css` under `@theme`).
- **Font:** Inter (`@fontsource-variable/inter`, bundled locally — no CDN), fallback `system-ui`.
- **Dark mode:** class-based. `.dark` on `<html>` selects the dark palette.
  `@custom-variant dark (&:where(.dark, .dark *))` enables `dark:` utilities if ever needed.
- **Theme state:** `src/theme.ts` reads/writes `localStorage["theme"]` (`"dark"` | `"light"`,
  **default dark**) and toggles the `.dark` class. Applied in `main.tsx` before render (no flash).
  UI control: `src/components/ThemeToggle.tsx` in the nav.

## Color tokens

Semantic CSS variables defined in `:root` (light) and `.dark` (dark), surfaced to Tailwind as
`--color-*` in `@theme`. Utilities: `bg-canvas`, `bg-surface`, `bg-elevated`, `border-line`,
`text-fg`, `text-muted`, `text-accent`, `bg-accent`, `text-success`, `text-warning`, `text-danger`.

| Token (utility root) | Dark (default) | Light | Use |
|---|---|---|---|
| `canvas` | `#0b0b0f` | `#f8fafc` | Page background |
| `surface` | `#16171d` | `#ffffff` | Cards, inputs, nav |
| `elevated` | `#1e1f27` | `#f1f5f9` | Raised / hover fills, chips |
| `line` | `rgba(255,255,255,.08)` | `#e5e7eb` | Borders (default border color) |
| `fg` | `#f3f4f6` | `#0f1117` | Primary text, headings |
| `muted` | `#9ca3af` | `#6b7280` | Secondary text |
| `accent` | `#a855f7` | `#7c3aed` | Primary buttons, links, focus |
| `accent-hover` | `#9333ea` | `#6d28d9` | Accent hover |
| `accent-soft` | `rgba(168,85,247,.14)` | `rgba(124,58,237,.10)` | Washes, glow, focus ring |
| `success` | `#34d399` | `#059669` | Positive states |
| `warning` | `#fbbf24` | `#d97706` | Caution states |
| `danger` | `#fb7185` | `#e11d48` | Errors, destructive |

## Shape, elevation, motion

- **Radii:** cards `rounded-xl` (16px), buttons/inputs `rounded-lg` (10px), chips/badges `rounded-full`.
- **Elevation:** flat by default (border-only). Hero/primary surfaces use `.card-glow` — a soft
  violet glow (`0 0 40px -12px accent-soft`). Avoid heavy drop shadows.
- **Motion:** 150ms color transitions on hover/focus. Focus = 2px accent outline + `accent-soft` ring.

## Reusable classes (`@layer components` in `index.css`)

- **Buttons:** `.btn-primary`, `.btn-secondary`, `.btn-ghost`, `.btn-danger`; size modifier `.btn-sm`.
- **Forms:** `.input`, `.textarea`, `.select`, `.label`.
- **Surfaces:** `.card`, `.card-glow`.
- **Tags:** `.chip` (neutral), `.badge` (accent).

## React primitives (`src/components/ui/`)

Thin, typed wrappers over the classes above — prefer these in pages over raw HTML:
`Button`, `Input`, `Textarea`, `Select`, `Field` (label + control + error), `Card`, `Chip`,
`Badge`, `StatusBadge` (maps the 8 `ApplicationStatus` values to success/accent/danger/muted),
`PageHeader`, `Spinner`, `EmptyState`, `Alert` (error / success / info), `Skeleton` / `SkeletonList`
(pulsing placeholder blocks for a page's initial load, before there's data to show — prefer these
over `Spinner` when the eventual layout is known, since it reduces layout shift on load).

Fetching pages should use the `useFetch` hook (`src/lib/useFetch.ts`) instead of hand-rolled
`useEffect` + three `useState`s: it returns `{data, error, loading, reload}`, and centrally handles
a 401 by clearing the token and redirecting to `/login` (via the `ApiError` thrown by `src/api.ts`).

## Layouts

- **`AppLayout`** — authenticated shell: sticky top nav (brand `◆ Career Copilot`, links Jobs /
  Applications / Resumes / Company Intel / Profile, active-route highlight, `ThemeToggle`, Log out),
  centered `max-w-6xl` content container.
- **`AuthLayout`** — centered glow card for Login / Register (brand only, no nav).
- **Public pages** (Company Intel) use a minimal header: brand + `ThemeToggle`, no auth nav.

## Rules

1. No hardcoded hex colors in components — use tokens / primitives.
2. Every error message renders through `<Alert variant="error">`, not a bare `<p>`.
3. Both themes must remain legible — verify light **and** dark on any new surface.
4. Keep primitives presentational; page logic and `src/api.ts` stay untouched by styling work.
