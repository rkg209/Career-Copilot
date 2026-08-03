import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import {
  downloadResumePdf,
  listResumes,
  scoreResume,
  type AtsGapReport,
  type ResumeVersion,
} from "../api";
import { useFetch } from "../lib/useFetch";
import { GapReport } from "../components/GapReport";
import { AppLayout } from "../components/AppLayout";
import { Alert, Badge, Button, Card, EmptyState, PageHeader, SkeletonList } from "../components/ui";

export function ResumesPage() {
  const token = localStorage.getItem("token");
  const [actionError, setActionError] = useState<string | null>(null);
  const [report, setReport] = useState<AtsGapReport | null>(null);
  const [scoringId, setScoringId] = useState<number | null>(null);
  const {
    data: page,
    error: loadError,
    loading,
  } = useFetch(() => listResumes(token!), [token]);
  const resumes = page?.content ?? [];
  const error = actionError ?? loadError;

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const score = (r: ResumeVersion) => {
    setActionError(null);
    setScoringId(r.resumeVersionId);
    scoreResume(r.resumeVersionId, r.jobPostingId, token)
      .then(setReport)
      .catch(() => setActionError("Scoring failed — try again in a moment."))
      .finally(() => setScoringId(null));
  };

  return (
    <AppLayout>
      <PageHeader title="My resumes" subtitle="Every tailored resume, ready to download or score." />

      {error && <Alert variant="error" className="mb-4">{error}</Alert>}

      {loading ? (
        <SkeletonList rows={4} rowClassName="h-14" />
      ) : resumes.length === 0 && !error ? (
        <EmptyState
          title="No tailored resumes yet"
          description="Open a job and click “Tailor resume” to generate your first one."
          action={
            <Link to="/jobs">
              <Button variant="primary">Browse jobs</Button>
            </Link>
          }
        />
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b text-xs uppercase tracking-wide text-muted">
                <th className="px-4 py-3 font-medium">Created</th>
                <th className="px-4 py-3 font-medium">Template</th>
                <th className="px-4 py-3 font-medium">Job</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {resumes.map((r) => (
                <tr key={r.resumeVersionId} className="border-b last:border-0">
                  <td className="px-4 py-3 text-muted">{new Date(r.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3">{r.templateId}</td>
                  <td className="px-4 py-3">
                    <Link to={`/jobs/${r.jobPostingId}`} className="text-accent hover:underline">
                      #{r.jobPostingId}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={r.renderStatus === "RENDERED" ? "success" : r.renderStatus === "FAILED" ? "danger" : "warning"}>
                      {r.renderStatus}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        onClick={() =>
                          downloadResumePdf(r.resumeVersionId, token).catch(() =>
                            setActionError(
                              "Download failed — the PDF may still be rendering; try again.",
                            ),
                          )
                        }
                      >
                        Download PDF
                      </Button>
                      <Button size="sm" variant="primary" disabled={scoringId === r.resumeVersionId} onClick={() => score(r)}>
                        {scoringId === r.resumeVersionId ? "Scoring…" : "ATS score"}
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {report && (
        <div className="mt-6">
          <GapReport report={report} />
        </div>
      )}
    </AppLayout>
  );
}
