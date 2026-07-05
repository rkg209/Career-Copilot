import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { listApplications, type Application, type ApplicationStatus } from "../api";
import { AppLayout } from "../components/AppLayout";
import { Alert, Button, EmptyState, PageHeader, StatusBadge } from "../components/ui";

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
    <AppLayout>
      <PageHeader title="My applications" subtitle="Track every application from saved to offer." />

      {error && <Alert variant="error" className="mb-4">{error}</Alert>}

      {applications.length === 0 && !error ? (
        <EmptyState
          title="No applications tracked yet"
          description="Open a job and click “Track this application” to start your board."
          action={
            <Link to="/jobs">
              <Button variant="primary">Browse jobs</Button>
            </Link>
          }
        />
      ) : (
        <div className="flex gap-4 overflow-x-auto pb-2">
          {STATUSES.map((status) => {
            const inColumn = applications.filter((a) => a.status === status);
            if (inColumn.length === 0) return null;
            return (
              <div key={status} className="w-64 shrink-0">
                <div className="mb-3 flex items-center gap-2">
                  <StatusBadge status={status} />
                  <span className="text-xs text-muted">{inColumn.length}</span>
                </div>
                <div className="flex flex-col gap-2">
                  {inColumn.map((a) => (
                    <Link
                      key={a.id}
                      to={`/applications/${a.id}`}
                      className="card p-3 transition-colors duration-150 hover:border-accent"
                    >
                      <p className="text-sm font-medium">{a.jobTitleSnapshot}</p>
                      <p className="text-xs text-muted">{a.companySnapshot}</p>
                    </Link>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </AppLayout>
  );
}
