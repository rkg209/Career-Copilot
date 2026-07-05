import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getCompanyIntel, type CompanyIntelDetail } from "../api";
import { PublicLayout } from "../components/AppLayout";
import { Alert, Badge, Card, PageHeader, Spinner } from "../components/ui";

export function CompanyIntelDetailPage() {
  const { companyName } = useParams();
  const [detail, setDetail] = useState<CompanyIntelDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!companyName) return;
    getCompanyIntel(companyName)
      .then(setDetail)
      .catch(() => setError("No published intel found for this company"));
  }, [companyName]);

  return (
    <PublicLayout>
      <p className="mb-4">
        <Link to="/intel" className="text-sm text-accent hover:underline">
          ← Company interview intel
        </Link>
      </p>

      {error && <Alert variant="error">{error}</Alert>}
      {!detail && !error && <Spinner />}

      {detail && (
        <>
          <PageHeader title={detail.company} />
          <div className="flex flex-col gap-4">
            {detail.rounds.map((round) => (
              <Card key={round.roundType}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">
                    {round.roundType.replaceAll("_", " ")}
                    {round.roleCategory ? ` — ${round.roleCategory}` : ""}
                  </h2>
                  {round.difficulty != null && <Badge tone="warning">difficulty {round.difficulty}/5</Badge>}
                </div>
                <p className="mt-1 text-sm text-muted">
                  {round.entryCount} contribution{round.entryCount === 1 ? "" : "s"}
                </p>
                {round.questions.length > 0 && (
                  <ul className="mt-3 list-disc space-y-1 pl-5 text-sm">
                    {round.questions.map((question, i) => (
                      <li key={i}>{question}</li>
                    ))}
                  </ul>
                )}
                {round.processNotes && <p className="mt-3 text-sm text-muted">{round.processNotes}</p>}
              </Card>
            ))}
          </div>
        </>
      )}
    </PublicLayout>
  );
}
