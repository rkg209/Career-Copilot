import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getJob, type JobPosting } from "../api";

export function JobDetailPage() {
  const { id } = useParams();
  const [job, setJob] = useState<JobPosting | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    getJob(id)
      .then(setJob)
      .catch(() => setError("Job not found"));
  }, [id]);

  if (error) {
    return (
      <div>
        <p role="alert">{error}</p>
        <Link to="/jobs">Back to jobs</Link>
      </div>
    );
  }

  if (!job) {
    return <p>Loading…</p>;
  }

  const salary =
    job.salaryMin || job.salaryMax
      ? `${job.salaryMin ?? "?"}–${job.salaryMax ?? "?"} ${job.salaryCurrency ?? ""}`
      : null;

  return (
    <div>
      <p>
        <Link to="/jobs">← Back to jobs</Link>
      </p>
      <h1>{job.title}</h1>
      <p>
        <strong>{job.company}</strong>
        {job.location ? ` — ${job.location}` : ""}
        {job.remote ? " · Remote" : ""}
      </p>
      <p>
        <Link to={`/jobs/${job.id}/tailor`}>
          <button>Tailor resume for this job</button>
        </Link>
      </p>
      {job.employmentType && <p>Type: {job.employmentType}</p>}
      {salary && <p>Salary: {salary}</p>}
      {job.skills.length > 0 && <p>Skills: {job.skills.join(", ")}</p>}
      {job.responsibilities.length > 0 && (
        <>
          <h2>Responsibilities</h2>
          <ul>
            {job.responsibilities.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </>
      )}
      <h2>Description</h2>
      <pre style={{ whiteSpace: "pre-wrap" }}>{job.fullText}</pre>
      {job.url && (
        <p>
          <a href={job.url} target="_blank" rel="noreferrer">
            Original posting
          </a>
        </p>
      )}
    </div>
  );
}
