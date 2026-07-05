import type { HTMLAttributes } from "react";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  glow?: boolean;
}

export function Card({ glow = false, className = "", ...props }: CardProps) {
  return <div className={`${glow ? "card-glow" : "card"} ${className}`.trim()} {...props} />;
}
