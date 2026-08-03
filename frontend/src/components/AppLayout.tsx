import { useEffect, useState, type ReactNode } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { me } from "../api";
import { ThemeToggle } from "./ThemeToggle";
import { Sidebar, Brand, PRIMARY_NAV } from "./Sidebar";
import { Footer } from "./Footer";
import { MenuIcon } from "./icons";

function HeaderNav() {
  const { pathname } = useLocation();
  const isActive = (to: string) => pathname.startsWith(to);
  return (
    <nav className="hidden items-center gap-1 md:flex">
      {PRIMARY_NAV.map((item) => (
        <Link
          key={item.to}
          to={item.to}
          aria-current={isActive(item.to) ? "page" : undefined}
          className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
            isActive(item.to) ? "bg-accent-soft text-accent" : "text-muted hover:text-fg"
          }`}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}

/**
 * Authenticated app shell: filled top header (brand + horizontal nav) plus a
 * togglable right-side drawer (hidden by default) and a footer. Centralizes the
 * auth guard and loads the signed-in user once for the drawer identity block.
 */
export function AppLayout({ children }: { children: ReactNode }) {
  const token = localStorage.getItem("token");
  const location = useLocation();
  const [email, setEmail] = useState<string | null>(null);
  const [authFailed, setAuthFailed] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    if (!token) return;
    me(token)
      .then((u) => setEmail(u.email))
      .catch(() => setAuthFailed(true));
  }, [token]);

  // Close the drawer whenever the route changes.
  useEffect(() => setMenuOpen(false), [location.pathname]);

  // Close on Escape.
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [menuOpen]);

  if (!token || authFailed) {
    if (authFailed) localStorage.removeItem("token");
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-accent focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-white"
      >
        Skip to main content
      </a>
      <header className="sticky top-0 z-20 border-b bg-canvas/80 backdrop-blur">
        <div className="flex h-16 items-center gap-4 px-4 sm:px-6 lg:px-8">
          <Brand />
          <div className="mx-2 hidden h-6 w-px bg-line md:block" aria-hidden="true" />
          <HeaderNav />
          <div className="flex flex-1 items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              className="btn btn-secondary btn-sm !px-2"
              aria-label="Open menu"
              aria-expanded={menuOpen}
            >
              <MenuIcon width={18} height={18} />
            </button>
          </div>
        </div>
      </header>

      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} email={email} />

      <main id="main-content" className="flex-1">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">{children}</div>
      </main>

      <Footer />
    </div>
  );
}

/** Minimal public shell (brand + theme toggle + footer) for unauthenticated pages. */
export function PublicLayout({ children }: { children: ReactNode }) {
  const isAuthed = Boolean(localStorage.getItem("token"));
  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <header className="sticky top-0 z-20 border-b bg-canvas/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
          <Brand />
          <div className="flex items-center gap-2">
            <ThemeToggle />
            {isAuthed ? (
              <Link to="/" className="btn btn-secondary btn-sm">
                Dashboard
              </Link>
            ) : (
              <Link to="/login" className="btn btn-primary btn-sm">
                Log in
              </Link>
            )}
          </div>
        </div>
      </header>
      <main className="flex-1">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">{children}</div>
      </main>
      <Footer />
    </div>
  );
}
