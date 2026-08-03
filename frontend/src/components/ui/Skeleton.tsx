interface SkeletonProps {
  className?: string;
}

/** A single pulsing placeholder block. Compose with utility classes for width/height. */
export function Skeleton({ className = "" }: SkeletonProps) {
  return (
    <div
      className={`animate-pulse rounded-md bg-line/60 ${className}`.trim()}
      aria-hidden="true"
    />
  );
}

interface SkeletonListProps {
  rows?: number;
  rowClassName?: string;
}

/** A stack of skeleton rows for list/card-grid loading states. */
export function SkeletonList({ rows = 4, rowClassName = "h-20" }: SkeletonListProps) {
  return (
    <div className="flex flex-col gap-3" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className={`w-full ${rowClassName}`} />
      ))}
    </div>
  );
}
