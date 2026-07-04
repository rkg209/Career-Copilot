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
      <div>
        <p role="alert">{error}</p>
        <Link to="/applications">Back to applications</Link>
      </div>
    );
  }

  if (!application) {
    return <p>Loading…</p>;
  }

  const next = NEXT_STATUS[application.status];
  const isTerminal = ["OFFER", "REJECTED", "WITHDRAWN"].includes(application.status);

  return (
    <div>
      <p>
        <Link to="/applications">← Applications</Link>
      </p>
      <h1>
        {application.jobTitleSnapshot} — {application.companySnapshot}
      </h1>
      <p>Status: {application.status.replaceAll("_", " ")}</p>
      {error && <p role="alert">{error}</p>}
      {!isTerminal && (
        <p>
          {next && <button onClick={advance}>Advance to {next.replaceAll("_", " ")}</button>}{" "}
          <button onClick={() => reject("REJECTED")}>Mark rejected</button>{" "}
          <button onClick={() => reject("WITHDRAWN")}>Withdraw</button>
        </p>
      )}
      {application.notes && <p>Notes: {application.notes}</p>}

      <h2>Interview logs</h2>
      {contributeMessage && <p role="status">{contributeMessage}</p>}
      {logs.length === 0 && <p>No interview logs yet.</p>}
      <ul>
        {logs.map((log) => (
          <li key={log.id}>
            Round {log.roundNumber} — {log.roundType.replaceAll("_", " ")}
            {log.notes ? `: ${log.notes}` : ""}{" "}
            {log.contributedAt ? (
              <em>({log.contributionStatus?.toLowerCase() ?? "contributed"})</em>
            ) : (
              <button onClick={() => contribute(log.id)}>Contribute anonymously</button>
            )}
          </li>
        ))}
      </ul>

      <h3>Add interview log</h3>
      <form onSubmit={submitLog}>
        <label>
          Round type{" "}
          <select
            value={roundType}
            onChange={(e) => setRoundType(e.target.value as InterviewRoundType)}
          >
            {ROUND_TYPES.map((rt) => (
              <option key={rt} value={rt}>
                {rt.replaceAll("_", " ")}
              </option>
            ))}
          </select>
        </label>{" "}
        <label>
          Notes{" "}
          <input value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>{" "}
        <button type="submit">Add log</button>
      </form>
    </div>
  );
}
