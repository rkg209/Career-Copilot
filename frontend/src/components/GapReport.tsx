import type { AtsGapReport } from "../api";
import { Chip } from "./ui";

function pct(fraction: number): string {
  return `${Math.round(fraction * 100)}%`;
}

/** Color the score by band: <40 danger, <70 warning, else success. */
function scoreColor(score: number): string {
  if (score < 40) return "var(--color-danger)";
  if (score < 70) return "var(--color-warning)";
  return "var(--color-success)";
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-elevated px-3 py-2">
      <p className="text-xs text-muted">{label}</p>
      <p className="text-lg font-semibold text-fg">{value}</p>
    </div>
  );
}

/** Renders one ATS gap report: score gauge, coverage breakdown, gaps, and ordered fixes. */
export function GapReport({ report }: { report: AtsGapReport }) {
  const color = scoreColor(report.score);
  return (
    <div className="card-glow">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">ATS score</h2>
        <span className="text-2xl font-bold" style={{ color }}>
          {report.score.toFixed(1)}
          <span className="text-base font-medium text-muted"> / 100</span>
        </span>
      </div>

      <div className="mt-3 h-2.5 w-full overflow-hidden rounded-full bg-elevated">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${Math.max(0, Math.min(100, report.score))}%`, backgroundColor: color }}
        />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Stat label="Required covered" value={pct(report.breakdown.requiredCoverage)} />
        <Stat label="Preferred covered" value={pct(report.breakdown.preferredCoverage)} />
        <Stat label="Keyword density" value={pct(report.breakdown.keywordDensity)} />
      </div>

      {report.missingRequired.length > 0 && (
        <div className="mt-4">
          <p className="mb-2 text-sm font-medium">Missing required</p>
          <div className="flex flex-wrap gap-2">
            {report.missingRequired.map((k) => (
              <Chip key={k} style={{ color: "var(--color-danger)" }}>{k}</Chip>
            ))}
          </div>
        </div>
      )}

      {report.missingPreferred.length > 0 && (
        <div className="mt-4">
          <p className="mb-2 text-sm font-medium">Missing preferred</p>
          <div className="flex flex-wrap gap-2">
            {report.missingPreferred.map((k) => (
              <Chip key={k}>{k}</Chip>
            ))}
          </div>
        </div>
      )}

      {report.nearMisses.length > 0 && (
        <div className="mt-4">
          <p className="mb-2 text-sm font-medium">Near misses</p>
          <ul className="space-y-1 text-sm text-muted">
            {report.nearMisses.map((n) => (
              <li key={n.skill}>
                <span className="font-medium text-fg">{n.skill}</span> (similarity{" "}
                {n.similarity.toFixed(2)}): your resume says “{n.suggestedPhrase}” — name it explicitly.
              </li>
            ))}
          </ul>
        </div>
      )}

      {report.fixes.length > 0 && (
        <div className="mt-4">
          <p className="mb-2 text-sm font-medium">Suggested fixes</p>
          <ol className="list-decimal space-y-1 pl-5 text-sm text-muted">
            {report.fixes.map((f) => (
              <li key={f.priority}>
                <span className="font-medium text-fg">{f.skill}</span>: {f.suggestedBullet}
              </li>
            ))}
          </ol>
        </div>
      )}

      {report.missingRequired.length === 0 && report.missingPreferred.length === 0 && (
        <p className="mt-4 text-sm" style={{ color: "var(--color-success)" }}>
          No keyword gaps found — this resume covers the JD’s keywords. 🎉
        </p>
      )}

      <p className="mt-4 text-xs text-muted">
        Scored {new Date(report.scoredAt).toLocaleString()} · resume #{report.resumeVersionId} vs job #
        {report.jobPostingId}
      </p>
    </div>
  );
}
