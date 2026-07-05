interface SpinnerProps {
  label?: string;
  className?: string;
}

export function Spinner({ label = "Loading…", className = "" }: SpinnerProps) {
  return (
    <div className={`flex items-center gap-3 text-muted ${className}`.trim()} role="status">
      <span
        className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        aria-hidden="true"
      />
      <span>{label}</span>
    </div>
  );
}
