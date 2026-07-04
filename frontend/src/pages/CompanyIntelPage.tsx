import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { browseCompanyIntel, type CompanyIntelSummary, type Page } from "../api";

const PAGE_SIZE = 20;

export function CompanyIntelPage() {
  const [data, setData] = useState<Page<CompanyIntelSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    browseCompanyIntel(page, PAGE_SIZE)
      .then(setData)
      .catch(() => setError("Could not load company intel"));
  }, [page]);

  return (
    <div>
      <h1>Company interview intel</h1>
      <p>
        <Link to="/">Home</Link>
      </p>
      <p>
        Anonymized, community-contributed interview questions and process notes. A company
        appears here only once at least a few contributors have shared their experience.
      </p>

      {error && <p role="alert">{error}</p>}
      {!data && !error && <p>Loading…</p>}
      {data && data.content.length === 0 && <p>No published company intel yet.</p>}

      <ul>
        {data?.content.map((summary) => (
          <li key={summary.company}>
            <Link to={`/intel/${encodeURIComponent(summary.company)}`}>
              <strong>{summary.company}</strong>
            </Link>{" "}
            — {summary.entryCount} contribution{summary.entryCount === 1 ? "" : "s"}
            {summary.avgDifficulty != null ? ` · avg difficulty ${summary.avgDifficulty}/5` : ""}
            {" · "}
            {summary.roundTypes.map((rt) => rt.replaceAll("_", " ")).join(", ")}
          </li>
        ))}
      </ul>

      {data && data.page.totalPages > 1 && (
        <div>
          <button disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span>
            {" "}
            Page {data.page.number + 1} of {data.page.totalPages}{" "}
          </span>
          <button
            disabled={page >= data.page.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
