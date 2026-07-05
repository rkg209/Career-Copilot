import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { pasteJob } from "../api";
import { AppLayout } from "../components/AppLayout";
import { Alert, Button, Card, Field, Input, PageHeader, Textarea } from "../components/ui";

export function PasteJobPage() {
  const token = localStorage.getItem("token");
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [company, setCompany] = useState("");
  const [location, setLocation] = useState("");
  const [url, setUrl] = useState("");
  const [rawText, setRawText] = useState("");
  const [error, setError] = useState<string | null>(null);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const job = await pasteJob(
        { title, company, location: location || undefined, url: url || undefined, rawText },
        token!,
      );
      navigate(`/jobs/${job.id}`);
    } catch {
      setError("Could not save job. Check the fields (description needs at least 50 characters).");
    }
  }

  return (
    <AppLayout>
      <p className="mb-4">
        <Link to="/jobs" className="text-sm text-accent hover:underline">
          ← Back to jobs
        </Link>
      </p>
      <PageHeader title="Paste a job" subtitle="Add a posting to score and tailor against." />

      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Title">
              <Input placeholder="Senior Backend Engineer" value={title} onChange={(e) => setTitle(e.target.value)} required />
            </Field>
            <Field label="Company">
              <Input placeholder="Acme Inc." value={company} onChange={(e) => setCompany(e.target.value)} required />
            </Field>
            <Field label="Location" hint="Optional">
              <Input placeholder="Remote · Bengaluru" value={location} onChange={(e) => setLocation(e.target.value)} />
            </Field>
            <Field label="Original URL" hint="Optional">
              <Input placeholder="https://…" value={url} onChange={(e) => setUrl(e.target.value)} />
            </Field>
          </div>
          <Field label="Job description" hint="Minimum 50 characters">
            <Textarea
              placeholder="Paste the full job description here…"
              value={rawText}
              onChange={(e) => setRawText(e.target.value)}
              rows={12}
              required
            />
          </Field>
          {error && <Alert variant="error">{error}</Alert>}
          <div>
            <Button type="submit" variant="primary">
              Save job
            </Button>
          </div>
        </form>
      </Card>
    </AppLayout>
  );
}
