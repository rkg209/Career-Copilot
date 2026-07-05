import type { ReactNode } from "react";

interface FieldProps {
  label?: ReactNode;
  htmlFor?: string;
  hint?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** Label + control grouping with optional hint text below. */
export function Field({ label, htmlFor, hint, children, className = "" }: FieldProps) {
  return (
    <div className={className}>
      {label && (
        <label className="label" htmlFor={htmlFor}>
          {label}
        </label>
      )}
      {children}
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}
