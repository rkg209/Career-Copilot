import type { ComponentType, SVGProps } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  HomeIcon,
  BriefcaseIcon,
  ClipboardIcon,
  FileIcon,
  BuildingIcon,
  UserIcon,
  SettingsIcon,
  LogoutIcon,
  CloseIcon,
} from "./icons";

export type NavItem = {
  to: string;
  label: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  end?: boolean;
};

/** Flat primary nav for the horizontal header bar. */
export const PRIMARY_NAV: NavItem[] = [
  { to: "/jobs", label: "Jobs", icon: BriefcaseIcon },
  { to: "/applications", label: "Applications", icon: ClipboardIcon },
  { to: "/resumes", label: "Resumes", icon: FileIcon },
  { to: "/intel", label: "Company Intel", icon: BuildingIcon },
];

/** Account actions shown in the right-hand drawer. */
const MENU_ITEMS: NavItem[] = [
  { to: "/", label: "Home", icon: HomeIcon, end: true },
  { to: "/profile", label: "Profile", icon: UserIcon },
  { to: "/settings", label: "Settings", icon: SettingsIcon },
];

export function Brand({ onClick }: { onClick?: () => void }) {
  return (
    <Link
      to="/"
      onClick={onClick}
      className="flex items-center gap-2.5 font-semibold text-fg"
    >
      <span className="grid h-8 w-8 place-items-center rounded-lg bg-accent text-white shadow-sm">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12 2 3 7v10l9 5 9-5V7z" opacity="0.9" />
        </svg>
      </span>
      <span className="text-[15px] tracking-tight">Career Copilot</span>
    </Link>
  );
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
  email: string | null;
}

/** Togglable right-hand account drawer (hidden by default). */
export function Sidebar({ open, onClose, email }: SidebarProps) {
  const navigate = useNavigate();

  const logout = () => {
    localStorage.removeItem("token");
    navigate("/login");
  };

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        className={`fixed inset-0 z-30 bg-black/40 backdrop-blur-sm transition-opacity duration-200 ${
          open ? "opacity-100" : "pointer-events-none opacity-0"
        }`}
        aria-hidden="true"
      />

      <aside
        role="dialog"
        aria-modal="true"
        aria-hidden={!open}
        className={`fixed inset-y-0 right-0 z-40 flex w-72 max-w-[85vw] flex-col border-l bg-surface shadow-2xl transition-transform duration-200 ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <span className="text-sm font-semibold text-muted">Account</span>
          <button
            type="button"
            onClick={onClose}
            className="btn btn-ghost btn-sm !px-2"
            aria-label="Close menu"
          >
            <CloseIcon width={18} height={18} />
          </button>
        </div>

        {/* Identity */}
        <div className="mx-3 flex items-center gap-3 rounded-lg bg-elevated px-3 py-3">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-accent text-sm font-semibold text-white">
            {(email ?? "?").charAt(0).toUpperCase()}
          </span>
          <span className="min-w-0 flex-1 truncate text-sm text-fg" title={email ?? undefined}>
            {email ?? "…"}
          </span>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {MENU_ITEMS.map((item) => {
            const Ico = item.icon;
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={onClose}
                className="group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-muted transition-colors duration-150 hover:bg-elevated hover:text-fg"
              >
                <Ico width={18} height={18} className="text-muted group-hover:text-fg" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t p-3">
          <button
            type="button"
            onClick={logout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-danger transition-colors duration-150 hover:bg-accent-soft"
          >
            <LogoutIcon width={18} height={18} />
            Log out
          </button>
        </div>
      </aside>
    </>
  );
}
