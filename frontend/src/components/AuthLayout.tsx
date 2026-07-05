import type { ReactNode } from "react";
import { ThemeToggle } from "./ThemeToggle";

interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/** Centered glow-card layout for Login / Register. */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-canvas px-4">
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      {/* Ambient violet glow behind the card */}
      <div
        className="pointer-events-none absolute left-1/2 top-1/3 h-72 w-72 -translate-x-1/2 rounded-full blur-3xl"
        style={{ background: "var(--color-accent-soft)" }}
        aria-hidden="true"
      />

      <div className="relative w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2 text-lg font-semibold">
          <span className="text-accent" aria-hidden="true">◆</span>
          <span>Career Copilot</span>
        </div>
        <div className="card-glow">
          <h1 className="text-xl font-semibold">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
          <div className="mt-5">{children}</div>
        </div>
        {footer && <p className="mt-4 text-center text-sm text-muted">{footer}</p>}
      </div>
    </div>
  );
}
