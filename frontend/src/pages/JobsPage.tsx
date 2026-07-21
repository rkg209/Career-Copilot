import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  getIngestionStatus,
  getJobsForMe,
  listJobs,
  searchJobs,
  type IngestionStatus,
  type JobPosting,
  type Page,
} from "../api";
import { AppLayout } from "../components/AppLayout";
import {
  Alert,
  Button,
  Card,
  Chip,
  EmptyState,
  Input,
  PageHeader,
  Select,
  Spinner,
} from "../components/ui";

const PAGE_SIZE = 20;

export function JobsPage() {
  const token = localStorage.getItem("token");
  const [tab, setTab] = useState<"preference" | "all">(token ? "preference" : "all");
  const [data, setData] = useState<Page<JobPosting> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [ingestionStatus, setIngestionStatus] = useState<IngestionStatus | null>(null);

  const [q, setQ] = useState("");
  const [location, setLocation] = useState("");
  const [remoteType, setRemoteType] = useState("");
  const [employmentType, setEmploymentType] = useState("");
  const [salaryMin, setSalaryMin] = useState("");
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    setData(null);
    setError(null);

    if (tab === "preference" && token) {
      getJobsForMe(page, PAGE_SIZE, token)
        .then(setData)
        .catch(() => setError("Could not load your preference feed"));
      return;
    }

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
  }, [page, searching, tab]);

  function handleTabChange(next: "preference" | "all") {
    setTab(next);
    setPage(0);
  }

  useEffect(() => {
    getIngestionStatus()
      .then(setIngestionStatus)
      .catch(() => setIngestionStatus(null));
  }, []);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setPage(0);
    setSearching((s) => !s); // toggle to retrigger the effect
  }

  return (
    <AppLayout>
      <PageHeader
        title="Jobs"
        subtitle="Search live and saved postings, then tailor a resume."
        actions={
          <Link to="/jobs/paste">
            <Button variant="primary">Paste a job</Button>
          </Link>
        }
      />

      {ingestionStatus && (
        <p className="mb-4 text-sm text-muted">
          Last scraped{" "}
          {ingestionStatus.lastScrapedAt
            ? new Date(ingestionStatus.lastScrapedAt).toLocaleString()
            : "— not yet run"}
          {" · "}Next scrape{" "}
          {ingestionStatus.nextScrapedAt
            ? new Date(ingestionStatus.nextScrapedAt).toLocaleString()
            : "unknown"}
        </p>
      )}

      {token && (
        <div className="mb-4 flex gap-2">
          <Button
            variant={tab === "preference" ? "primary" : "secondary"}
            onClick={() => handleTabChange("preference")}
          >
            My Preference
          </Button>
          <Button
            variant={tab === "all" ? "primary" : "secondary"}
            onClick={() => handleTabChange("all")}
          >
            All Listings
          </Button>
        </div>
      )}

      {tab === "preference" && token ? (
        <p className="mb-4 text-sm text-muted">
          Jobs matching your Target roles and Areas of Interest from your{" "}
          <Link to="/profile" className="underline">
            profile
          </Link>
          .
        </p>
      ) : (
        <Card className="mb-6">
          <form onSubmit={handleSearch} className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6">
          <Input
            className="lg:col-span-2"
            placeholder="Keyword"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <Input
            placeholder="Location"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
          />
          <Select value={remoteType} onChange={(e) => setRemoteType(e.target.value)}>
            <option value="">Any location type</option>
            <option value="remote">Remote</option>
            <option value="hybrid">Hybrid</option>
            <option value="onsite">Onsite</option>
          </Select>
          <Select value={employmentType} onChange={(e) => setEmploymentType(e.target.value)}>
            <option value="">Any type</option>
            <option value="full_time">Full-time</option>
            <option value="contract">Contract</option>
            <option value="part_time">Part-time</option>
          </Select>
          <div className="flex gap-3">
            <Input
              type="number"
              placeholder="Min salary"
              value={salaryMin}
              onChange={(e) => setSalaryMin(e.target.value)}
            />
            <Button type="submit" variant="primary">
              Search
            </Button>
          </div>
          </form>
        </Card>
      )}

      {error && <Alert variant="error">{error}</Alert>}
      {!data && !error && <Spinner />}
      {data && data.content.length === 0 && tab === "preference" && (
        <EmptyState
          title="No matching jobs yet"
          description="Add Target roles and Areas of Interest on your profile to personalize this feed."
        />
      )}
      {data && data.content.length === 0 && tab === "all" && (
        <EmptyState title="No jobs found" description="Try adjusting your filters or paste a job description." />
      )}

      <div className="flex flex-col gap-3">
        {data?.content.map((job) => (
          <Link key={job.id} to={`/jobs/${job.id}`} className="card transition-colors duration-150 hover:border-accent">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-base font-semibold">{job.title}</h2>
                <p className="text-sm text-muted">
                  {job.company}
                  {job.location ? ` · ${job.location}` : ""}
                </p>
                {job.ingestedAt && (
                  <p className="text-xs text-muted">
                    Listed on our portal {new Date(job.ingestedAt).toLocaleDateString()}
                  </p>
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {job.remote && <Chip>Remote</Chip>}
                {job.employmentType && <Chip>{job.employmentType.replaceAll("_", " ")}</Chip>}
              </div>
            </div>
          </Link>
        ))}
      </div>

      {data && data.page.totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <Button disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-sm text-muted">
            Page {data.page.number + 1} of {data.page.totalPages}
          </span>
          <Button
            disabled={page >= data.page.totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </AppLayout>
  );
}
