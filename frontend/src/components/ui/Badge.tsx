import type { HTMLAttributes } from "react";

export type Tone = "accent" | "success" | "warning" | "danger" | "neutral";

const TOKEN: Record<Tone, string> = {
  accent: "var(--color-accent)",
  success: "var(--color-success)",
  warning: "var(--color-warning)",
  danger: "var(--color-danger)",
  neutral: "var(--color-muted)",
};

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

/** Small status pill; color driven by tone, background is a soft wash of it. */
export function Badge({ tone = "accent", className = "", style, ...props }: BadgeProps) {
  const color = TOKEN[tone];
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${className}`.trim()}
      style={{ color, backgroundColor: `color-mix(in srgb, ${color} 14%, transparent)`, ...style }}
      {...props}
    />
  );
}
