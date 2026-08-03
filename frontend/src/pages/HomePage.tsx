import { type ComponentType, type SVGProps } from "react";
import { Link } from "react-router-dom";
import { getProfile } from "../api";
import { useFetch } from "../lib/useFetch";
import { AppLayout } from "../components/AppLayout";
import {
  BriefcaseIcon,
  ClipboardIcon,
  FileIcon,
  BuildingIcon,
  UserIcon,
  SparklesIcon,
  ArrowRightIcon,
  TargetIcon,
} from "../components/icons";

type Action = {
  to: string;
  title: string;
  description: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
};

const ACTIONS: Action[] = [
  {
    to: "/jobs",
    title: "Browse jobs",
    description: "Search live and saved postings, then tailor a resume.",
    icon: BriefcaseIcon,
  },
  {
    to: "/jobs/paste",
    title: "Paste a job",
    description: "Drop in a job description to score and tailor against.",
    icon: SparklesIcon,
  },
  {
    to: "/resumes",
    title: "My resumes",
    description: "View tailored resumes and download PDFs.",
    icon: FileIcon,
  },
  {
    to: "/applications",
    title: "My applications",
    description: "Track every application from saved to offer.",
    icon: ClipboardIcon,
  },
  {
    to: "/intel",
    title: "Company interview intel",
    description: "Anonymized, community-shared interview questions.",
    icon: BuildingIcon,
  },
  {
    to: "/profile",
    title: "My profile",
    description: "Manage your experience corpus and contact details.",
    icon: UserIcon,
  },
];

const STEPS = [
  {
    icon: BriefcaseIcon,
    title: "Find a job",
    description: "Browse live postings or paste any job description.",
  },
  {
    icon: SparklesIcon,
    title: "Tailor with AI",
    description: "Generate a resume grounded in your real experience.",
  },
  {
    icon: TargetIcon,
    title: "Score & apply",
    description: "Check your ATS match, then track it to an offer.",
  },
];

export function HomePage() {
  const token = localStorage.getItem("token");
  const { data: profile } = useFetch(
    () => (token ? getProfile(token) : Promise.resolve(null)),
    [token],
  );

  // Greet with the first name from the profile; fall back to a generic hero. A failed/absent
  // profile fetch just means no greeting name — never blocks the page, so useFetch's error state
  // is intentionally unused here.
  const fullName = profile?.fullName ?? null;
  const greetingName = fullName?.trim() ? fullName.trim().split(/\s+/)[0] : null;

  return (
    <AppLayout>
      {/* Hero */}
      <section className="relative overflow-hidden rounded-2xl border bg-surface p-8 sm:p-10">
        <div
          className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full blur-3xl"
          style={{ background: "var(--color-accent-soft)" }}
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -bottom-32 -left-16 h-72 w-72 rounded-full blur-3xl opacity-70"
          style={{ background: "var(--color-accent-soft)" }}
          aria-hidden="true"
        />
        <div className="relative max-w-2xl">
          <span className="badge mb-4">
            <SparklesIcon width={13} height={13} className="mr-1" />
            AI-powered career workspace
          </span>
          <h1 className="text-3xl font-semibold sm:text-4xl">
            {greetingName ? (
              <>
                Welcome back, <span className="text-accent">{greetingName}</span>
              </>
            ) : (
              <>Your career, on autopilot</>
            )}
          </h1>
          <p className="mt-3 max-w-xl text-muted">
            Tailor resumes with AI, score them against any job, track applications, and learn from
            real interview intel — all in one place.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link to="/jobs" className="btn btn-primary">
              Browse jobs
              <ArrowRightIcon width={16} height={16} />
            </Link>
            <Link to="/jobs/paste" className="btn btn-secondary">
              Paste a job description
            </Link>
          </div>
        </div>
      </section>

      {/* Quick actions */}
      <section className="mt-10">
        <h2 className="mb-4 text-lg font-semibold">Quick actions</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {ACTIONS.map((action) => {
            const Ico = action.icon;
            return (
              <Link
                key={action.to}
                to={action.to}
                className="group relative flex flex-col rounded-xl border bg-surface p-5 transition-all duration-150 hover:-translate-y-0.5 hover:border-accent hover:shadow-lg"
              >
                <div className="mb-3 inline-flex h-11 w-11 items-center justify-center rounded-lg bg-accent-soft text-accent">
                  <Ico width={22} height={22} />
                </div>
                <h3 className="flex items-center gap-1.5 text-base font-semibold group-hover:text-accent">
                  {action.title}
                  <ArrowRightIcon
                    width={15}
                    height={15}
                    className="opacity-0 -translate-x-1 transition-all duration-150 group-hover:translate-x-0 group-hover:opacity-100"
                  />
                </h3>
                <p className="mt-1 text-sm text-muted">{action.description}</p>
              </Link>
            );
          })}
        </div>
      </section>

      {/* How it works */}
      <section className="mt-10 rounded-2xl border bg-surface/50 p-6 sm:p-8">
        <h2 className="text-lg font-semibold">How it works</h2>
        <p className="mt-1 text-sm text-muted">Three steps from posting to offer.</p>
        <div className="mt-6 grid grid-cols-1 gap-6 sm:grid-cols-3">
          {STEPS.map((step, i) => {
            const Ico = step.icon;
            return (
              <div key={step.title} className="relative flex gap-4">
                <div className="flex flex-col items-center">
                  <span className="grid h-10 w-10 place-items-center rounded-full bg-accent text-white">
                    <Ico width={18} height={18} />
                  </span>
                  {i < STEPS.length - 1 && (
                    <span className="mt-2 hidden h-full w-px bg-line sm:block" aria-hidden="true" />
                  )}
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-muted">Step {i + 1}</span>
                  </div>
                  <h3 className="text-base font-semibold">{step.title}</h3>
                  <p className="mt-1 text-sm text-muted">{step.description}</p>
                </div>
              </div>
            );
          })}
        </div>
      </section>
    </AppLayout>
  );
}
