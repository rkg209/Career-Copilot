import * as Sentry from "@sentry/react";

/** Inert unless VITE_SENTRY_DSN is set — call once from main.tsx before render. */
export function initSentry(): void {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn) return;
  Sentry.init({ dsn, tracesSampleRate: 0.1 });
}

export { Sentry };
