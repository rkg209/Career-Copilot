import { Link } from "react-router-dom";

const FOOTER_LINKS = [
  { to: "/jobs", label: "Jobs" },
  { to: "/resumes", label: "Resumes" },
  { to: "/applications", label: "Applications" },
  { to: "/intel", label: "Company Intel" },
];

export function Footer() {
  return (
    <footer className="border-t bg-surface/40">
      <div className="mx-auto flex max-w-6xl flex-col items-center gap-4 px-4 py-6 text-sm text-muted sm:flex-row sm:justify-between sm:px-6">
        <div className="flex items-center gap-2">
          <span className="text-accent" aria-hidden="true">◆</span>
          <span>© {new Date().getFullYear()} Career Copilot</span>
        </div>
        <nav className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2">
          {FOOTER_LINKS.map((l) => (
            <Link key={l.to} to={l.to} className="transition-colors hover:text-fg">
              {l.label}
            </Link>
          ))}
        </nav>
      </div>
    </footer>
  );
}
