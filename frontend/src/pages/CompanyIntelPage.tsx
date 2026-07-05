import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { browseCompanyIntel, type CompanyIntelSummary, type Page } from "../api";
import { PublicLayout } from "../components/AppLayout";
import { Alert, Button, Chip, EmptyState, PageHeader, Spinner } from "../components/ui";

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
    <PublicLayout>
      <PageHeader
        title="Company interview intel"
        subtitle="Anonymized, community-contributed interview questions and process notes. A company appears here only once a few contributors have shared their experience."
      />

      {error && <Alert variant="error">{error}</Alert>}
      {!data && !error && <Spinner />}
      {data && data.content.length === 0 && (
        <EmptyState title="No published company intel yet" description="Check back once contributors have shared." />
      )}

      <div className="flex flex-col gap-3">
        {data?.content.map((summary) => (
          <Link
            key={summary.company}
            to={`/intel/${encodeURIComponent(summary.company)}`}
            className="card transition-colors duration-150 hover:border-accent"
          >
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-base font-semibold">{summary.company}</h2>
                <p className="text-sm text-muted">
                  {summary.entryCount} contribution{summary.entryCount === 1 ? "" : "s"}
                  {summary.avgDifficulty != null ? ` · avg difficulty ${summary.avgDifficulty}/5` : ""}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {summary.roundTypes.map((rt) => (
                  <Chip key={rt}>{rt.replaceAll("_", " ")}</Chip>
                ))}
              </div>
            </div>
          </Link>
        ))}
      </div>

      {data && data.page.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <Button disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-sm text-muted">
            Page {data.page.number + 1} of {data.page.totalPages}
          </span>
          <Button disabled={page >= data.page.totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </PublicLayout>
  );
}
