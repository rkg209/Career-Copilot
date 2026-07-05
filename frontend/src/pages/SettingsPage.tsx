import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { me } from "../api";
import { AppLayout } from "../components/AppLayout";
import { Card, PageHeader } from "../components/ui";
import { getTheme, setTheme, type Theme } from "../theme";

const THEMES: { value: Theme; label: string }[] = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
];

export function SettingsPage() {
  const token = localStorage.getItem("token");
  const navigate = useNavigate();
  const [email, setEmail] = useState<string | null>(null);
  const [theme, setThemeState] = useState<Theme>(getTheme());

  useEffect(() => {
    if (!token) return;
    me(token)
      .then((u) => setEmail(u.email))
      .catch(() => {});
  }, [token]);

  const chooseTheme = (t: Theme) => {
    setTheme(t);
    setThemeState(t);
  };

  const logout = () => {
    localStorage.removeItem("token");
    navigate("/login");
  };

  return (
    <AppLayout>
      <PageHeader title="Settings" subtitle="Manage your account and preferences." />

      <div className="flex max-w-2xl flex-col gap-6">
        {/* Account */}
        <Card>
          <h2 className="text-base font-semibold">Account</h2>
          <p className="mt-1 text-sm text-muted">Your sign-in details.</p>
          <dl className="mt-4 flex items-center justify-between gap-4 border-t pt-4">
            <dt className="text-sm text-muted">Email</dt>
            <dd className="text-sm font-medium text-fg">{email ?? "…"}</dd>
          </dl>
          <div className="mt-4 border-t pt-4">
            <p className="text-sm text-muted">
              Name, contact details, and experience live on your profile.
            </p>
            <Link to="/profile" className="btn btn-secondary btn-sm mt-3">
              Edit profile
            </Link>
          </div>
        </Card>

        {/* Appearance */}
        <Card>
          <h2 className="text-base font-semibold">Appearance</h2>
          <p className="mt-1 text-sm text-muted">Choose how Career Copilot looks.</p>
          <div className="mt-4 inline-flex rounded-lg border bg-canvas p-1">
            {THEMES.map((t) => (
              <button
                key={t.value}
                type="button"
                onClick={() => chooseTheme(t.value)}
                aria-pressed={theme === t.value}
                className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
                  theme === t.value ? "bg-accent text-white" : "text-muted hover:text-fg"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
        </Card>

        {/* Session */}
        <Card>
          <h2 className="text-base font-semibold">Session</h2>
          <p className="mt-1 text-sm text-muted">Sign out of this device.</p>
          <button type="button" onClick={logout} className="btn btn-danger btn-sm mt-4">
            Log out
          </button>
        </Card>
      </div>
    </AppLayout>
  );
}
