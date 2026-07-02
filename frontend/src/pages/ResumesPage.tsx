import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import {
  downloadResumePdf,
  listResumes,
  scoreResume,
  type AtsGapReport,
  type ResumeVersion,
} from "../api";
import { GapReport } from "../components/GapReport";

export function ResumesPage() {
  const token = localStorage.getItem("token");
  const [resumes, setResumes] = useState<ResumeVersion[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<AtsGapReport | null>(null);
  const [scoringId, setScoringId] = useState<number | null>(null);

  useEffect(() => {
    if (!token) return;
    listResumes(token)
      .then((page) => setResumes(page.content))
      .catch(() => setError("Failed to load resumes"));
  }, [token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const score = (r: ResumeVersion) => {
    setError(null);
    setScoringId(r.resumeVersionId);
    scoreResume(r.resumeVersionId, r.jobPostingId, token)
      .then(setReport)
      .catch(() => setError("Scoring failed — try again in a moment."))
      .finally(() => setScoringId(null));
  };

  return (
    <div>
      <p>
        <Link to="/jobs">← Jobs</Link> · <Link to="/profile">Profile</Link>
      </p>
      <h1>My resumes</h1>
      {error && <p role="alert">{error}</p>}
      {resumes.length === 0 && !error && (
        <p>
          No tailored resumes yet. Open a job and click “Tailor resume for this job”.
        </p>
      )}
      {resumes.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Created</th>
              <th>Template</th>
              <th>Job</th>
              <th>Status</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {resumes.map((r) => (
              <tr key={r.resumeVersionId}>
                <td>{new Date(r.createdAt).toLocaleString()}</td>
                <td>{r.templateId}</td>
                <td>
                  <Link to={`/jobs/${r.jobPostingId}`}>#{r.jobPostingId}</Link>
                </td>
                <td>{r.renderStatus}</td>
                <td>
                  <button
                    onClick={() =>
                      downloadResumePdf(r.resumeVersionId, token).catch(() =>
                        setError("Download failed — the PDF may still be rendering; try again."),
                      )
                    }
                  >
                    Download PDF
                  </button>
                </td>
                <td>
                  <button
                    disabled={scoringId === r.resumeVersionId}
                    onClick={() => score(r)}
                  >
                    {scoringId === r.resumeVersionId ? "Scoring…" : "ATS score vs job"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {report && <GapReport report={report} />}
    </div>
  );
}
