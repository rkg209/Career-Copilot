import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate } from "react-router-dom";
import {
  addExperience,
  deleteExperience,
  getProfile,
  listExperience,
  putProfile,
  updateExperience,
  type Experience,
  type ExperienceRequest,
  type Profile,
} from "../api";

const emptyForm: ExperienceRequest = {
  role: "",
  company: "",
  location: "",
  startDate: "",
  endDate: "",
  isCurrent: false,
  description: "",
  achievements: [],
  skillsUsed: [],
};

export function ProfilePage() {
  const token = localStorage.getItem("token");
  const [profile, setProfile] = useState<Profile | null>(null);
  const [targetRolesText, setTargetRolesText] = useState("");
  const [targetLocationsText, setTargetLocationsText] = useState("");
  const [entries, setEntries] = useState<Experience[]>([]);
  const [form, setForm] = useState<ExperienceRequest>(emptyForm);
  const [achievementsText, setAchievementsText] = useState("");
  const [skillsText, setSkillsText] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    getProfile(token)
      .then((p) => {
        setProfile(p);
        setTargetRolesText(p.targetRoles.join(", "));
        setTargetLocationsText(p.targetLocations.join(", "));
      })
      .catch(() => setError("Could not load profile."));
    refreshEntries(token);
  }, [token]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  function refreshEntries(t: string) {
    listExperience(t)
      .then(setEntries)
      .catch(() => setError("Could not load experience entries."));
  }

  function splitList(text: string): string[] {
    return text
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  async function handleProfileSubmit(e: FormEvent) {
    e.preventDefault();
    if (!profile) return;
    setError(null);
    setMessage(null);
    try {
      const saved = await putProfile(
        {
          ...profile,
          targetRoles: splitList(targetRolesText),
          targetLocations: splitList(targetLocationsText),
        },
        token!,
      );
      setProfile(saved);
      setMessage("Profile saved.");
    } catch {
      setError("Could not save profile.");
    }
  }

  async function handleExperienceSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    const req: ExperienceRequest = {
      ...form,
      location: form.location || undefined,
      endDate: form.isCurrent ? undefined : form.endDate || undefined,
      description: form.description || undefined,
      achievements: splitList(achievementsText),
      skillsUsed: splitList(skillsText),
    };
    try {
      if (editingId === null) {
        await addExperience(req, token!);
      } else {
        await updateExperience(editingId, req, token!);
      }
      setForm(emptyForm);
      setAchievementsText("");
      setSkillsText("");
      setEditingId(null);
      refreshEntries(token!);
    } catch {
      setError("Could not save experience entry. Check required fields and dates.");
    }
  }

  function startEdit(entry: Experience) {
    setEditingId(entry.id);
    setForm({
      role: entry.role,
      company: entry.company,
      location: entry.location ?? "",
      startDate: entry.startDate,
      endDate: entry.endDate ?? "",
      isCurrent: entry.isCurrent,
      description: entry.description ?? "",
    });
    setAchievementsText(entry.achievements.join(", "));
    setSkillsText(entry.skillsUsed.join(", "));
  }

  async function handleDelete(id: number) {
    setError(null);
    try {
      await deleteExperience(id, token!);
      refreshEntries(token!);
    } catch {
      setError("Could not delete experience entry.");
    }
  }

  return (
    <div>
      <p>
        <Link to="/jobs">← Back to jobs</Link>
      </p>
      <h1>My profile</h1>
      {profile && (
        <form onSubmit={handleProfileSubmit}>
          <input
            placeholder="Headline (e.g. Backend Engineer)"
            value={profile.headline ?? ""}
            onChange={(e) => setProfile({ ...profile, headline: e.target.value })}
          />
          <textarea
            placeholder="Summary"
            value={profile.summary ?? ""}
            onChange={(e) => setProfile({ ...profile, summary: e.target.value })}
            rows={4}
          />
          <input
            placeholder="Location"
            value={profile.location ?? ""}
            onChange={(e) => setProfile({ ...profile, location: e.target.value })}
          />
          <input
            placeholder="LinkedIn URL"
            value={profile.linkedinUrl ?? ""}
            onChange={(e) => setProfile({ ...profile, linkedinUrl: e.target.value })}
          />
          <input
            placeholder="GitHub URL"
            value={profile.githubUrl ?? ""}
            onChange={(e) => setProfile({ ...profile, githubUrl: e.target.value })}
          />
          <input
            placeholder="Portfolio URL"
            value={profile.portfolioUrl ?? ""}
            onChange={(e) => setProfile({ ...profile, portfolioUrl: e.target.value })}
          />
          <input
            placeholder="Target roles (comma-separated)"
            value={targetRolesText}
            onChange={(e) => setTargetRolesText(e.target.value)}
          />
          <input
            placeholder="Target locations (comma-separated)"
            value={targetLocationsText}
            onChange={(e) => setTargetLocationsText(e.target.value)}
          />
          <label>
            <input
              type="checkbox"
              checked={profile.openToRemote}
              onChange={(e) => setProfile({ ...profile, openToRemote: e.target.checked })}
            />
            Open to remote
          </label>
          <button type="submit">Save profile</button>
        </form>
      )}

      <h2>Experience</h2>
      <form onSubmit={handleExperienceSubmit}>
        <input
          placeholder="Role"
          value={form.role}
          onChange={(e) => setForm({ ...form, role: e.target.value })}
          required
        />
        <input
          placeholder="Company"
          value={form.company}
          onChange={(e) => setForm({ ...form, company: e.target.value })}
          required
        />
        <input
          placeholder="Location (optional)"
          value={form.location ?? ""}
          onChange={(e) => setForm({ ...form, location: e.target.value })}
        />
        <label>
          Start date
          <input
            type="date"
            value={form.startDate}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })}
            required
          />
        </label>
        <label>
          End date
          <input
            type="date"
            value={form.endDate ?? ""}
            onChange={(e) => setForm({ ...form, endDate: e.target.value })}
            disabled={form.isCurrent}
          />
        </label>
        <label>
          <input
            type="checkbox"
            checked={form.isCurrent ?? false}
            onChange={(e) => setForm({ ...form, isCurrent: e.target.checked })}
          />
          Current role
        </label>
        <textarea
          placeholder="What did you do in this role?"
          value={form.description ?? ""}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
          rows={5}
        />
        <input
          placeholder="Achievements (comma-separated)"
          value={achievementsText}
          onChange={(e) => setAchievementsText(e.target.value)}
        />
        <input
          placeholder="Skills used (comma-separated)"
          value={skillsText}
          onChange={(e) => setSkillsText(e.target.value)}
        />
        <button type="submit">{editingId === null ? "Add experience" : "Save changes"}</button>
        {editingId !== null && (
          <button
            type="button"
            onClick={() => {
              setEditingId(null);
              setForm(emptyForm);
              setAchievementsText("");
              setSkillsText("");
            }}
          >
            Cancel edit
          </button>
        )}
      </form>

      {message && <p>{message}</p>}
      {error && <p role="alert">{error}</p>}

      <ul>
        {entries.map((entry) => (
          <li key={entry.id}>
            <strong>{entry.role}</strong> at {entry.company}{" "}
            <em>
              ({entry.startDate} – {entry.isCurrent ? "present" : (entry.endDate ?? "?")})
            </em>{" "}
            <span>[{entry.embeddingStatus}]</span>
            {entry.description && <p>{entry.description}</p>}
            <button type="button" onClick={() => startEdit(entry)}>
              Edit
            </button>
            <button type="button" onClick={() => handleDelete(entry.id)}>
              Delete
            </button>
          </li>
        ))}
      </ul>
      {entries.length === 0 && <p>No experience entries yet — add your first one above.</p>}
    </div>
  );
}
