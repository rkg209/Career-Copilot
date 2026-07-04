import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { listApplications, type Application, type ApplicationStatus } from "../api";

const STATUSES: ApplicationStatus[] = [
  "SAVED",
  "APPLIED",
  "PHONE_SCREEN",
  "TECHNICAL_INTERVIEW",
  "FINAL_ROUND",
  "OFFER",
  "REJECTED",
  "WITHDRAWN",
];

export function ApplicationsPage() {
  const token = localStorage.getItem("token");
  const [applications, setApplications] = useState<Application[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    listApplications(token)
      .then((page) => setApplications(page.content))
      .catch(() => setError("Failed to load applications"));
  }, [token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div>
      <p>
        <Link to="/jobs">← Jobs</Link> · <Link to="/resumes">Resumes</Link>
      </p>
      <h1>My applications</h1>
      {error && <p role="alert">{error}</p>}
      {applications.length === 0 && !error && (
        <p>No applications tracked yet. Open a job and click “Track this application”.</p>
      )}
      <div style={{ display: "flex", gap: "1rem", overflowX: "auto" }}>
        {STATUSES.map((status) => {
          const inColumn = applications.filter((a) => a.status === status);
          if (inColumn.length === 0) return null;
          return (
            <div key={status} style={{ minWidth: "220px" }}>
              <h3>{status.replaceAll("_", " ")}</h3>
              <ul style={{ listStyle: "none", padding: 0 }}>
                {inColumn.map((a) => (
                  <li key={a.id} style={{ marginBottom: "0.5rem" }}>
                    <Link to={`/applications/${a.id}`}>
                      {a.jobTitleSnapshot} — {a.companySnapshot}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </div>
    </div>
  );
}
