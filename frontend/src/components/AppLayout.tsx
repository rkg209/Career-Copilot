import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ThemeToggle } from "./ThemeToggle";

const NAV_LINKS = [
  { to: "/jobs", label: "Jobs" },
  { to: "/applications", label: "Applications" },
  { to: "/resumes", label: "Resumes" },
  { to: "/intel", label: "Company Intel" },
  { to: "/profile", label: "Profile" },
];

function Brand() {
  return (
    <Link to="/" className="flex items-center gap-2 font-semibold text-fg">
      <span className="text-accent" aria-hidden="true">◆</span>
      <span>Career Copilot</span>
    </Link>
  );
}

/** Authenticated shell: sticky nav + centered content container. */
export function AppLayout({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();

  const logout = () => {
    localStorage.removeItem("token");
    navigate("/login");
  };

  const isActive = (to: string) =>
    to === "/" ? location.pathname === "/" : location.pathname.startsWith(to);

  return (
    <div className="min-h-screen bg-canvas">
      <header className="sticky top-0 z-20 border-b bg-canvas/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3 sm:px-6">
          <Brand />
          <nav className="flex flex-1 flex-wrap items-center gap-1">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
                  isActive(link.to)
                    ? "bg-accent-soft text-accent"
                    : "text-muted hover:text-fg"
                }`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <button type="button" onClick={logout} className="btn btn-ghost btn-sm">
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">{children}</main>
    </div>
  );
}

/** Minimal header (brand + theme toggle) for public, unauthenticated pages. */
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-canvas">
      <header className="sticky top-0 z-20 border-b bg-canvas/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <Brand />
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">{children}</main>
    </div>
  );
}
