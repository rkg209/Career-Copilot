import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { downloadResumePdf, listResumes, type ResumeVersion } from "../api";

export function ResumesPage() {
  const token = localStorage.getItem("token");
  const [resumes, setResumes] = useState<ResumeVersion[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    listResumes(token)
      .then((page) => setResumes(page.content))
      .catch(() => setError("Failed to load resumes"));
  }, [token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

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
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
