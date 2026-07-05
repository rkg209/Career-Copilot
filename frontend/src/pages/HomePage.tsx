import { useEffect, useState, type ReactNode } from "react";
import { Navigate, Link } from "react-router-dom";
import { me } from "../api";
import { AppLayout } from "../components/AppLayout";
import { Spinner } from "../components/ui";

interface Action {
  to: string;
  title: string;
  description: string;
  icon: ReactNode;
}

const ACTIONS: Action[] = [
  {
    to: "/jobs",
    title: "Browse jobs",
    description: "Search live and saved postings, then tailor a resume.",
    icon: <path d="M3 7h18M3 12h18M3 17h18" />,
  },
  {
    to: "/jobs/paste",
    title: "Paste a job",
    description: "Drop in a job description to score and tailor against.",
    icon: <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2" />,
  },
  {
    to: "/resumes",
    title: "My resumes",
    description: "View tailored resumes and download PDFs.",
    icon: <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h8" />,
  },
  {
    to: "/applications",
    title: "My applications",
    description: "Track every application from saved to offer.",
    icon: <path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />,
  },
  {
    to: "/intel",
    title: "Company interview intel",
    description: "Anonymized, community-shared interview questions.",
    icon: <path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM12 16v-4M12 8h.01" />,
  },
  {
    to: "/profile",
    title: "My profile",
    description: "Manage your experience corpus and contact details.",
    icon: <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" />,
  },
];

export function HomePage() {
  const token = localStorage.getItem("token");
  const [email, setEmail] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!token) return;
    me(token)
      .then((user) => setEmail(user.email))
      .catch(() => setFailed(true));
  }, [token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (failed) {
    localStorage.removeItem("token");
    return <Navigate to="/login" replace />;
  }

  return (
    <AppLayout>
      <div className="mb-8">
        <p className="text-sm text-muted">
          {email ? `Signed in as ${email}` : <Spinner label="Loading…" />}
        </p>
        <h1 className="mt-1 text-3xl font-semibold">Your career, on autopilot</h1>
        <p className="mt-2 max-w-xl text-muted">
          Tailor resumes with AI, score them against any job, track applications, and learn from
          real interview intel — all in one place.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {ACTIONS.map((action) => (
          <Link
            key={action.to}
            to={action.to}
            className="group card transition-colors duration-150 hover:border-accent"
          >
            <div className="mb-3 inline-flex h-10 w-10 items-center justify-center rounded-lg bg-accent-soft text-accent">
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                {action.icon}
              </svg>
            </div>
            <h2 className="text-base font-semibold group-hover:text-accent">{action.title}</h2>
            <p className="mt-1 text-sm text-muted">{action.description}</p>
          </Link>
        ))}
      </div>
    </AppLayout>
  );
}
