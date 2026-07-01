import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { listJobs, searchJobs, type JobPosting, type Page } from "../api";

const PAGE_SIZE = 20;

export function JobsPage() {
  const [data, setData] = useState<Page<JobPosting> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const [q, setQ] = useState("");
  const [location, setLocation] = useState("");
  const [remoteType, setRemoteType] = useState("");
  const [employmentType, setEmploymentType] = useState("");
  const [salaryMin, setSalaryMin] = useState("");
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    const anyFilter = q || location || remoteType || employmentType || salaryMin;
    const promise = anyFilter
      ? searchJobs({
          q: q || undefined,
          location: location || undefined,
          remoteType: (remoteType || undefined) as never,
          employmentType: (employmentType || undefined) as never,
          salaryMin: salaryMin ? Number(salaryMin) : undefined,
          page,
          size: PAGE_SIZE,
        })
      : listJobs(page, PAGE_SIZE);

    promise.then(setData).catch(() => setError("Could not load jobs"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, searching]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPage(0);
    setSearching((s) => !s); // toggle to retrigger the effect
  }

  return (
    <div>
      <h1>Jobs</h1>
      <p>
        <Link to="/">Home</Link> · <Link to="/jobs/paste">Paste a job</Link> ·{" "}
        <Link to="/profile">My profile</Link>
      </p>

      <form onSubmit={handleSearch}>
        <input placeholder="Keyword" value={q} onChange={(e) => setQ(e.target.value)} />
        <input
          placeholder="Location"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
        />
        <select value={remoteType} onChange={(e) => setRemoteType(e.target.value)}>
          <option value="">Any location type</option>
          <option value="remote">Remote</option>
          <option value="hybrid">Hybrid</option>
          <option value="onsite">Onsite</option>
        </select>
        <select value={employmentType} onChange={(e) => setEmploymentType(e.target.value)}>
          <option value="">Any type</option>
          <option value="full_time">Full-time</option>
          <option value="contract">Contract</option>
          <option value="part_time">Part-time</option>
        </select>
        <input
          type="number"
          placeholder="Min salary"
          value={salaryMin}
          onChange={(e) => setSalaryMin(e.target.value)}
        />
        <button type="submit">Search</button>
      </form>

      {error && <p role="alert">{error}</p>}
      {!data && !error && <p>Loading…</p>}
      {data && data.content.length === 0 && <p>No jobs found.</p>}

      <ul>
        {data?.content.map((job) => (
          <li key={job.id}>
            <Link to={`/jobs/${job.id}`}>
              <strong>{job.title}</strong>
            </Link>{" "}
            — {job.company}
            {job.location ? ` (${job.location})` : ""}
            {job.remote ? " · Remote" : ""}
          </li>
        ))}
      </ul>

      {data && data.page.totalPages > 1 && (
        <div>
          <button disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span>
            {" "}
            Page {data.page.number + 1} of {data.page.totalPages}{" "}
          </span>
          <button
            disabled={page >= data.page.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
