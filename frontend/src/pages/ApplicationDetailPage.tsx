import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import {
  addInterviewLog,
  contributeInterviewLog,
  listApplications,
  listInterviewLogs,
  updateApplication,
  type Application,
  type ApplicationStatus,
  type InterviewLog,
  type InterviewRoundType,
} from "../api";
import { AppLayout } from "../components/AppLayout";
import {
  Alert,
  Badge,
  Button,
  Card,
  Field,
  Input,
  PageHeader,
  Select,
  Spinner,
  StatusBadge,
} from "../components/ui";

const NEXT_STATUS: Partial<Record<ApplicationStatus, ApplicationStatus>> = {
  SAVED: "APPLIED",
  APPLIED: "PHONE_SCREEN",
  PHONE_SCREEN: "TECHNICAL_INTERVIEW",
  TECHNICAL_INTERVIEW: "FINAL_ROUND",
  FINAL_ROUND: "OFFER",
};

const ROUND_TYPES: InterviewRoundType[] = [
  "PHONE_SCREEN",
  "TECHNICAL",
  "SYSTEM_DESIGN",
  "BEHAVIOURAL",
  "TAKE_HOME",
  "PANEL",
  "HR",
  "OTHER",
];

export function ApplicationDetailPage() {
  const { id } = useParams();
  const token = localStorage.getItem("token");
  const applicationId = Number(id);

  const [application, setApplication] = useState<Application | null>(null);
  const [logs, setLogs] = useState<InterviewLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [roundType, setRoundType] = useState<InterviewRoundType>("PHONE_SCREEN");
  const [notes, setNotes] = useState("");
  const [contributeMessage, setContributeMessage] = useState<string | null>(null);

  const loadApplication = () => {
    if (!token) return;
    listApplications(token, 0, 100)
      .then((page) => {
        const found = page.content.find((a) => a.id === applicationId) ?? null;
        setApplication(found);
        if (!found) setError("Application not found");
      })
      .catch(() => setError("Failed to load application"));
  };

  const loadLogs = () => {
    if (!token) return;
    listInterviewLogs(applicationId, token)
      .then(setLogs)
      .catch(() => setError("Failed to load interview logs"));
  };

  useEffect(loadApplication, [token, applicationId]);
  useEffect(loadLogs, [token, applicationId]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const advance = () => {
    if (!application || !token) return;
    const next = NEXT_STATUS[application.status];
    if (!next) return;
    updateApplication(application.id, { status: next }, token)
      .then(setApplication)
      .catch(() => setError("Status update failed"));
  };

  const reject = (status: "REJECTED" | "WITHDRAWN") => {
    if (!application || !token) return;
    updateApplication(application.id, { status }, token)
      .then(setApplication)
      .catch(() => setError("Status update failed"));
  };

  const submitLog = (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;
    addInterviewLog(applicationId, { roundType, notes: notes || undefined }, token)
      .then(() => {
        setNotes("");
        loadLogs();
      })
      .catch(() => setError("Failed to add interview log"));
  };

  const contribute = (logId: number) => {
    if (!token) return;
    contributeInterviewLog(applicationId, logId, token)
      .then((response) => {
        setContributeMessage(
          response.status === "PUBLISHED"
            ? `Thanks! ${response.company} has reached ${response.publishedCountForCompany} contributions and its intel is now public.`
            : `Contribution staged (${response.publishedCountForCompany}/${response.threshold} needed for ${response.company} to publish).`,
        );
        loadLogs();
      })
      .catch(() => setError("Contribute failed"));
  };

  if (error && !application) {
    return (
      <AppLayout>
        <Alert variant="error">{error}</Alert>
        <p className="mt-4">
          <Link to="/applications" className="text-accent hover:underline">
            ← Back to applications
          </Link>
        </p>
      </AppLayout>
    );
  }

  if (!application) {
    return (
      <AppLayout>
        <Spinner />
      </AppLayout>
    );
  }

  const next = NEXT_STATUS[application.status];
  const isTerminal = ["OFFER", "REJECTED", "WITHDRAWN"].includes(application.status);

  return (
    <AppLayout>
      <p className="mb-4">
        <Link to="/applications" className="text-sm text-accent hover:underline">
          ← Applications
        </Link>
      </p>

      <PageHeader
        title={`${application.jobTitleSnapshot} — ${application.companySnapshot}`}
        subtitle={<StatusBadge status={application.status} />}
        actions={
          !isTerminal ? (
            <>
              {next && (
                <Button variant="primary" onClick={advance}>
                  Advance to {next.replaceAll("_", " ")}
                </Button>
              )}
              <Button onClick={() => reject("REJECTED")}>Mark rejected</Button>
              <Button onClick={() => reject("WITHDRAWN")}>Withdraw</Button>
            </>
          ) : undefined
        }
      />

      {error && <Alert variant="error" className="mb-4">{error}</Alert>}
      {application.notes && (
        <Card className="mb-6">
          <p className="text-sm text-muted">{application.notes}</p>
        </Card>
      )}

      <h2 className="mb-3 text-lg font-semibold">Interview logs</h2>
      {contributeMessage && <Alert variant="success" className="mb-4">{contributeMessage}</Alert>}

      {logs.length === 0 ? (
        <p className="mb-6 text-sm text-muted">No interview logs yet.</p>
      ) : (
        <div className="mb-6 flex flex-col gap-3">
          {logs.map((log) => (
            <Card key={log.id}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-medium">
                    Round {log.roundNumber} · {log.roundType.replaceAll("_", " ")}
                  </p>
                  {log.notes && <p className="mt-1 text-sm text-muted">{log.notes}</p>}
                </div>
                {log.contributedAt ? (
                  <Badge tone="neutral">{log.contributionStatus?.toLowerCase() ?? "contributed"}</Badge>
                ) : (
                  <Button size="sm" onClick={() => contribute(log.id)}>
                    Contribute anonymously
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Card>
        <h3 className="mb-4 text-base font-semibold">Add interview log</h3>
        <form onSubmit={submitLog} className="flex flex-col gap-4 sm:flex-row sm:items-end">
          <Field label="Round type" className="sm:w-56">
            <Select value={roundType} onChange={(e) => setRoundType(e.target.value as InterviewRoundType)}>
              {ROUND_TYPES.map((rt) => (
                <option key={rt} value={rt}>
                  {rt.replaceAll("_", " ")}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Notes" className="flex-1">
            <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          <Button type="submit" variant="primary">
            Add log
          </Button>
        </form>
      </Card>
    </AppLayout>
  );
}
