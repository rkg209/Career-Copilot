import { useEffect, useState } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import {
  generateCoverLetter,
  getJob,
  listTemplates,
  tailorResume,
  downloadResumePdf,
  downloadCoverLetterPdf,
  type CoverLetterVersion,
  type JobPosting,
  type ResumeVersion,
  type TemplateInfo,
} from "../api";

export function TailorPage() {
  const { id } = useParams();
  const token = localStorage.getItem("token");
  const [job, setJob] = useState<JobPosting | null>(null);
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [templateId, setTemplateId] = useState("classic-ats");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resume, setResume] = useState<ResumeVersion | null>(null);
  const [coverLetter, setCoverLetter] = useState<CoverLetterVersion | null>(null);

  useEffect(() => {
    if (!id || !token) return;
    getJob(id)
      .then(setJob)
      .catch(() => setError("Job not found"));
    listTemplates(token)
      .then((all) => setTemplates(all.filter((t) => t.kind === "RESUME")))
      .catch(() => setTemplates([]));
  }, [id, token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const jobId = Number(id);

  async function onTailor() {
    setBusy("Tailoring your resume — this can take up to a minute…");
    setError(null);
    try {
      setResume(await tailorResume(jobId, templateId, token!));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Tailoring failed");
    } finally {
      setBusy(null);
    }
  }

  async function onCoverLetter() {
    setBusy("Generating cover letter…");
    setError(null);
    try {
      setCoverLetter(
        await generateCoverLetter(jobId, token!, {
          resumeVersionId: resume?.resumeVersionId,
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Cover letter generation failed");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div>
      <p>
        <Link to={`/jobs/${id}`}>← Back to job</Link> · <Link to="/resumes">My resumes</Link>
      </p>
      <h1>Tailor resume</h1>
      {job && (
        <p>
          For: <strong>{job.title}</strong> at <strong>{job.company}</strong>
        </p>
      )}

      <h2>1. Pick a template</h2>
      <div>
        {templates.map((t) => (
          <label key={t.id} style={{ display: "block", marginBottom: "0.25rem" }}>
            <input
              type="radio"
              name="template"
              value={t.id}
              checked={templateId === t.id}
              onChange={() => setTemplateId(t.id)}
            />{" "}
            {t.displayName}
          </label>
        ))}
      </div>

      <h2>2. Generate</h2>
      <button onClick={onTailor} disabled={busy !== null}>
        Tailor resume
      </button>{" "}
      <button onClick={onCoverLetter} disabled={busy !== null}>
        Generate cover letter
      </button>
      {busy && <p>{busy}</p>}
      {error && <p role="alert">{error}</p>}

      {resume && (
        <div>
          <h2>Resume ready</h2>
          <p>
            Status: {resume.renderStatus} · model {resume.llmModel} · {resume.tokenCount} tokens
          </p>
          <button
            onClick={() =>
              downloadResumePdf(resume.resumeVersionId, token!).catch((e) =>
                setError(e instanceof Error ? e.message : "Download failed"),
              )
            }
          >
            Download PDF
          </button>
        </div>
      )}

      {coverLetter && (
        <div>
          <h2>Cover letter ready</h2>
          <p>Status: {coverLetter.renderStatus}</p>
          <button
            onClick={() =>
              downloadCoverLetterPdf(coverLetter.coverLetterId, token!).catch((e) =>
                setError(e instanceof Error ? e.message : "Download failed"),
              )
            }
          >
            Download PDF
          </button>
        </div>
      )}
    </div>
  );
}
