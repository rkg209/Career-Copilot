import type { ReactNode } from "react";

type Variant = "error" | "success" | "info";

const TOKEN: Record<Variant, string> = {
  error: "var(--color-danger)",
  success: "var(--color-success)",
  info: "var(--color-accent)",
};

interface AlertProps {
  variant?: Variant;
  children: ReactNode;
  className?: string;
}

export function Alert({ variant = "info", children, className = "" }: AlertProps) {
  const color = TOKEN[variant];
  return (
    <div
      role={variant === "error" ? "alert" : "status"}
      className={`rounded-lg border px-4 py-3 text-sm ${className}`.trim()}
      style={{
        color,
        borderColor: `color-mix(in srgb, ${color} 40%, transparent)`,
        backgroundColor: `color-mix(in srgb, ${color} 10%, transparent)`,
      }}
    >
      {children}
    </div>
  );
}
