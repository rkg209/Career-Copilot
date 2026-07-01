import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { pasteJob } from "../api";

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
    <div>
      <p>
        <Link to="/jobs">← Back to jobs</Link>
      </p>
      <h1>Paste a job</h1>
      <form onSubmit={handleSubmit}>
        <input placeholder="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <input
          placeholder="Company"
          value={company}
          onChange={(e) => setCompany(e.target.value)}
          required
        />
        <input
          placeholder="Location (optional)"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
        />
        <input
          placeholder="Original URL (optional)"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        <textarea
          placeholder="Paste the job description here (min 50 characters)"
          value={rawText}
          onChange={(e) => setRawText(e.target.value)}
          rows={12}
          required
        />
        <button type="submit">Save job</button>
      </form>
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
