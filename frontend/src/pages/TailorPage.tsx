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
import { AppLayout } from "../components/AppLayout";
import { Alert, Badge, Button, Card, PageHeader, Spinner } from "../components/ui";

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
  const selectedTemplate = templates.find((t) => t.id === templateId);

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
    <AppLayout>
      <p className="mb-4">
        <Link to={`/jobs/${id}`} className="text-sm text-accent hover:underline">
          ← Back to job
        </Link>
      </p>
      <PageHeader
        title="Tailor resume"
        subtitle={
          job ? (
            <>
              For <span className="font-medium text-fg">{job.title}</span> at{" "}
              <span className="font-medium text-fg">{job.company}</span>
            </>
          ) : undefined
        }
      />

      <Card className="mb-6">
        <h2 className="mb-4 text-lg font-semibold">1 · Pick a template</h2>
        <div className="flex flex-col gap-6 lg:flex-row">
          <div className="grid flex-1 grid-cols-1 gap-3 content-start sm:grid-cols-2">
            {templates.map((t) => {
              const selected = templateId === t.id;
              return (
                <label
                  key={t.id}
                  className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm transition-colors duration-150 ${
                    selected ? "border-accent bg-accent-soft" : "hover:border-accent"
                  }`}
                >
                  <input
                    type="radio"
                    name="template"
                    value={t.id}
                    checked={selected}
                    onChange={() => setTemplateId(t.id)}
                    className="h-4 w-4 accent-[var(--color-accent)]"
                  />
                  {t.displayName}
                </label>
              );
            })}
          </div>
          {selectedTemplate?.previewImagePath && (
            <div className="lg:w-64 lg:shrink-0">
              <div className="lg:sticky lg:top-4">
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">
                  Preview
                </p>
                <img
                  src={selectedTemplate.previewImagePath}
                  alt={`${selectedTemplate.displayName} template preview`}
                  className="w-full rounded-lg border border-line shadow-sm"
                />
              </div>
            </div>
          )}
        </div>
      </Card>

      <Card className="mb-6">
        <h2 className="mb-4 text-lg font-semibold">2 · Generate</h2>
        <div className="flex flex-wrap gap-2">
          <Button onClick={onTailor} disabled={busy !== null} variant="primary">
            Tailor resume
          </Button>
          <Button onClick={onCoverLetter} disabled={busy !== null}>
            Generate cover letter
          </Button>
        </div>
        {busy && <div className="mt-4"><Spinner label={busy} /></div>}
        {error && <Alert variant="error" className="mt-4">{error}</Alert>}
      </Card>

      {resume && (
        <Card glow className="mb-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">Resume ready</h2>
              <p className="mt-1 text-sm text-muted">
                <Badge tone={resume.renderStatus === "RENDERED" ? "success" : "warning"}>{resume.renderStatus}</Badge>{" "}
                · model {resume.llmModel} · {resume.tokenCount} tokens
              </p>
            </div>
            <Button
              variant="primary"
              onClick={() =>
                downloadResumePdf(resume.resumeVersionId, token!).catch((e) =>
                  setError(e instanceof Error ? e.message : "Download failed"),
                )
              }
            >
              Download PDF
            </Button>
          </div>
        </Card>
      )}

      {coverLetter && (
        <Card glow>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-semibold">Cover letter ready</h2>
              <p className="mt-1 text-sm text-muted">
                <Badge tone={coverLetter.renderStatus === "RENDERED" ? "success" : "warning"}>{coverLetter.renderStatus}</Badge>
              </p>
            </div>
            <Button
              variant="primary"
              onClick={() =>
                downloadCoverLetterPdf(coverLetter.coverLetterId, token!).catch((e) =>
                  setError(e instanceof Error ? e.message : "Download failed"),
                )
              }
            >
              Download PDF
            </Button>
          </div>
        </Card>
      )}
    </AppLayout>
  );
}
