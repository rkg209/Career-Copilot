import type { AtsGapReport } from "../api";

function pct(fraction: number): string {
  return `${Math.round(fraction * 100)}%`;
}

/** Renders one ATS gap report: score gauge, coverage breakdown, gaps, and ordered fixes. */
export function GapReport({ report }: { report: AtsGapReport }) {
  return (
    <div style={{ border: "1px solid #ccc", borderRadius: 8, padding: 16, marginTop: 12 }}>
      <h2 style={{ marginTop: 0 }}>
        ATS score: {report.score.toFixed(1)} / 100
      </h2>
      <meter min={0} max={100} low={40} high={70} optimum={100} value={report.score}
        style={{ width: "100%", height: 24 }}>
        {report.score.toFixed(1)}
      </meter>
      <p>
        Required keywords covered: <strong>{pct(report.breakdown.requiredCoverage)}</strong> ·
        Preferred: <strong>{pct(report.breakdown.preferredCoverage)}</strong> ·
        Overall keyword density: <strong>{pct(report.breakdown.keywordDensity)}</strong>
      </p>
      {report.missingRequired.length > 0 && (
        <p>
          <strong>Missing required:</strong> {report.missingRequired.join(", ")}
        </p>
      )}
      {report.missingPreferred.length > 0 && (
        <p>
          <strong>Missing preferred:</strong> {report.missingPreferred.join(", ")}
        </p>
      )}
      {report.nearMisses.length > 0 && (
        <>
          <h3>Near misses</h3>
          <ul>
            {report.nearMisses.map((n) => (
              <li key={n.skill}>
                <strong>{n.skill}</strong> (similarity {n.similarity.toFixed(2)}): your resume
                says “{n.suggestedPhrase}” — name it explicitly.
              </li>
            ))}
          </ul>
        </>
      )}
      {report.fixes.length > 0 && (
        <>
          <h3>Suggested fixes</h3>
          <ol>
            {report.fixes.map((f) => (
              <li key={f.priority}>
                <strong>{f.skill}</strong>: {f.suggestedBullet}
              </li>
            ))}
          </ol>
        </>
      )}
      {report.missingRequired.length === 0 && report.missingPreferred.length === 0 && (
        <p>No keyword gaps found — this resume covers the JD’s keywords. 🎉</p>
      )}
      <p style={{ color: "#666", fontSize: "0.85em" }}>
        Scored {new Date(report.scoredAt).toLocaleString()} · resume #{report.resumeVersionId} vs
        job #{report.jobPostingId}
      </p>
    </div>
  );
}
