import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getCompanyIntel, type CompanyIntelDetail } from "../api";

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
    <div>
      <p>
        <Link to="/intel">← Company interview intel</Link>
      </p>

      {error && <p role="alert">{error}</p>}
      {!detail && !error && <p>Loading…</p>}

      {detail && (
        <>
          <h1>{detail.company}</h1>
          {detail.rounds.map((round) => (
            <section key={round.roundType}>
              <h2>
                {round.roundType.replaceAll("_", " ")}
                {round.roleCategory ? ` — ${round.roleCategory}` : ""}
              </h2>
              <p>
                {round.entryCount} contribution{round.entryCount === 1 ? "" : "s"}
                {round.difficulty != null ? ` · avg difficulty ${round.difficulty}/5` : ""}
              </p>
              {round.questions.length > 0 && (
                <ul>
                  {round.questions.map((question, i) => (
                    <li key={i}>{question}</li>
                  ))}
                </ul>
              )}
              {round.processNotes && <p>{round.processNotes}</p>}
            </section>
          ))}
        </>
      )}
    </div>
  );
}
