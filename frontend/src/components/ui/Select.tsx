import type { SelectHTMLAttributes } from "react";

export function Select({ className = "", children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={`select ${className}`.trim()} {...props}>
      {children}
    </select>
  );
}
