import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  createApplication,
  getJob,
  listResumes,
  scoreResume,
  type AtsGapReport,
  type JobPosting,
  type ResumeVersion,
} from "../api";
import { GapReport } from "../components/GapReport";
import { AppLayout } from "../components/AppLayout";
import { Alert, Button, Card, Chip, PageHeader, Spinner } from "../components/ui";

export function JobDetailPage() {
  const { id } = useParams();
  const token = localStorage.getItem("token");
  const [job, setJob] = useState<JobPosting | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [latestResume, setLatestResume] = useState<ResumeVersion | null>(null);
  const [report, setReport] = useState<AtsGapReport | null>(null);
  const [scoring, setScoring] = useState(false);
  const [scoreError, setScoreError] = useState<string | null>(null);
  const [tracked, setTracked] = useState(false);
  const [trackError, setTrackError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    getJob(id)
      .then(setJob)
      .catch(() => setError("Job not found"));
  }, [id]);

  useEffect(() => {
    if (!id || !token) return;
    listResumes(token)
      .then((page) => {
        const forJob = page.content.filter((r) => String(r.jobPostingId) === id);
        setLatestResume(forJob[0] ?? null);
      })
      .catch(() => setLatestResume(null));
  }, [id, token]);

  const score = () => {
    if (!latestResume || !token) return;
    setScoreError(null);
    setScoring(true);
    scoreResume(latestResume.resumeVersionId, latestResume.jobPostingId, token)
      .then(setReport)
      .catch(() => setScoreError("Scoring failed — try again in a moment."))
      .finally(() => setScoring(false));
  };

  const trackApplication = () => {
    if (!job || !token) return;
    setTrackError(null);
    createApplication({ jobPostingId: job.id }, token)
      .then(() => setTracked(true))
      .catch(() => setTrackError("Failed to track this application — try again."));
  };

  if (error) {
    return (
      <AppLayout>
        <Alert variant="error">{error}</Alert>
        <p className="mt-4">
          <Link to="/jobs" className="text-accent hover:underline">
            ← Back to jobs
          </Link>
        </p>
      </AppLayout>
    );
  }

  if (!job) {
    return (
      <AppLayout>
        <Spinner />
      </AppLayout>
    );
  }

  const salary =
    job.salaryMin || job.salaryMax
      ? `${job.salaryMin ?? "?"}–${job.salaryMax ?? "?"} ${job.salaryCurrency ?? ""}`
      : null;

  return (
    <AppLayout>
      <p className="mb-4">
        <Link to="/jobs" className="text-sm text-accent hover:underline">
          ← Back to jobs
        </Link>
      </p>

      <PageHeader
        title={job.title}
        subtitle={
          <>
            <span className="font-medium text-fg">{job.company}</span>
            {job.location ? ` · ${job.location}` : ""}
            {job.remote ? " · Remote" : ""}
          </>
        }
        actions={
          <>
            <Link to={`/jobs/${job.id}/tailor`}>
              <Button variant="primary">Tailor resume</Button>
            </Link>
            {latestResume && (
              <Button disabled={scoring} onClick={score}>
                {scoring ? "Scoring…" : "ATS-score latest resume"}
              </Button>
            )}
            <Button disabled={tracked} onClick={trackApplication}>
              {tracked ? "Tracked ✓" : "Track this application"}
            </Button>
          </>
        }
      />

      {trackError && <Alert variant="error" className="mb-3">{trackError}</Alert>}
      {scoreError && <Alert variant="error" className="mb-3">{scoreError}</Alert>}
      {report && <GapReport report={report} />}

      <div className="mt-4 flex flex-wrap gap-2">
        {job.employmentType && <Chip>{job.employmentType.replaceAll("_", " ")}</Chip>}
        {salary && <Chip>{salary}</Chip>}
        {job.skills.map((s) => (
          <Chip key={s}>{s}</Chip>
        ))}
      </div>

      {job.responsibilities.length > 0 && (
        <Card className="mt-6">
          <h2 className="mb-2 text-lg font-semibold">Responsibilities</h2>
          <ul className="list-disc space-y-1 pl-5 text-sm">
            {job.responsibilities.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </Card>
      )}

      <Card className="mt-6">
        <h2 className="mb-2 text-lg font-semibold">Description</h2>
        <pre className="whitespace-pre-wrap font-sans text-sm text-muted">{job.fullText}</pre>
      </Card>

      {job.url && (
        <p className="mt-4">
          <a href={job.url} target="_blank" rel="noreferrer" className="text-accent hover:underline">
            Original posting ↗
          </a>
        </p>
      )}
    </AppLayout>
  );
}
