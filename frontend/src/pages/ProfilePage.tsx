import { useEffect, useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import {
  addExperience,
  deleteExperience,
  getProfile,
  listExperience,
  putProfile,
  updateExperience,
  type CorpusEntryType,
  type Experience,
  type ExperienceRequest,
  type Profile,
  type ProjectType,
} from "../api";
import { AppLayout } from "../components/AppLayout";
import { EducationSection } from "../components/profile/EducationSection";
import { HighlightsSection } from "../components/profile/HighlightsSection";
import { SkillGroupsSection } from "../components/profile/SkillGroupsSection";
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
  Textarea,
} from "../components/ui";

const ENTRY_TYPE_LABELS: Record<CorpusEntryType, string> = {
  WORK: "Work experience",
  INTERNSHIP: "Internship",
  PROJECT: "Project",
  POSITION_OF_RESPONSIBILITY: "Position of responsibility",
  PUBLICATION: "Publication",
};

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
  entryType: "WORK",
  guide: "",
  context: "",
  url: "",
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
  const [courseworkText, setCourseworkText] = useState("");
  const [interestsText, setInterestsText] = useState("");
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
        setCourseworkText(p.coursework.join(", "));
        setInterestsText(p.areasOfInterest.join(", "));
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
          coursework: splitList(courseworkText),
          areasOfInterest: splitList(interestsText),
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
      projectType: form.entryType === "PROJECT" ? form.projectType : undefined,
      guide: form.guide || undefined,
      context: form.context || undefined,
      url: form.url || undefined,
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
      entryType: entry.entryType,
      projectType: entry.projectType ?? undefined,
      guide: entry.guide ?? "",
      context: entry.context ?? "",
      url: entry.url ?? "",
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
    <AppLayout>
      <PageHeader title="My profile" subtitle="Your details and experience corpus power resume tailoring." />

      {message && <Alert variant="success" className="mb-4">{message}</Alert>}
      {error && <Alert variant="error" className="mb-4">{error}</Alert>}

      {profile && (
        <Card className="mb-6">
          <h2 className="mb-4 text-lg font-semibold">Details</h2>
          <form onSubmit={handleProfileSubmit} className="flex flex-col gap-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Full name" hint="Appears on your resume">
                <Input value={profile.fullName ?? ""} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} />
              </Field>
              <Field label="Phone" hint="Appears on your resume">
                <Input value={profile.phone ?? ""} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} />
              </Field>
              <Field label="Headline">
                <Input placeholder="Backend Engineer" value={profile.headline ?? ""} onChange={(e) => setProfile({ ...profile, headline: e.target.value })} />
              </Field>
              <Field label="Location">
                <Input value={profile.location ?? ""} onChange={(e) => setProfile({ ...profile, location: e.target.value })} />
              </Field>
            </div>
            <Field label="Summary">
              <Textarea value={profile.summary ?? ""} onChange={(e) => setProfile({ ...profile, summary: e.target.value })} rows={4} />
            </Field>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Field label="LinkedIn URL">
                <Input value={profile.linkedinUrl ?? ""} onChange={(e) => setProfile({ ...profile, linkedinUrl: e.target.value })} />
              </Field>
              <Field label="GitHub URL">
                <Input value={profile.githubUrl ?? ""} onChange={(e) => setProfile({ ...profile, githubUrl: e.target.value })} />
              </Field>
              <Field label="Portfolio URL">
                <Input value={profile.portfolioUrl ?? ""} onChange={(e) => setProfile({ ...profile, portfolioUrl: e.target.value })} />
              </Field>
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Target roles" hint="Comma-separated">
                <Input value={targetRolesText} onChange={(e) => setTargetRolesText(e.target.value)} />
              </Field>
              <Field label="Target locations" hint="Comma-separated">
                <Input value={targetLocationsText} onChange={(e) => setTargetLocationsText(e.target.value)} />
              </Field>
              <Field label="Key coursework" hint="Comma-separated — appears on your resume">
                <Input placeholder="Data Structures, Operating Systems" value={courseworkText} onChange={(e) => setCourseworkText(e.target.value)} />
              </Field>
              <Field label="Areas of interest" hint="Comma-separated">
                <Input placeholder="Distributed Systems, ML" value={interestsText} onChange={(e) => setInterestsText(e.target.value)} />
              </Field>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 accent-[var(--color-accent)]"
                checked={profile.openToRemote}
                onChange={(e) => setProfile({ ...profile, openToRemote: e.target.checked })}
              />
              Open to remote
            </label>
            <div>
              <Button type="submit" variant="primary">
                Save profile
              </Button>
            </div>
          </form>
        </Card>
      )}

      <EducationSection token={token} onError={setError} />

      <Card className="mb-6">
        <h2 className="mb-1 text-lg font-semibold">
          {editingId === null ? "Add corpus entry" : "Edit corpus entry"}
        </h2>
        <p className="mb-4 text-sm text-muted">
          Work, internships, projects, positions of responsibility, publications — the evidence
          pool that resume tailoring selects from.
        </p>
        <form onSubmit={handleExperienceSubmit} className="flex flex-col gap-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Entry type">
              <Select
                value={form.entryType ?? "WORK"}
                onChange={(e) =>
                  setForm({ ...form, entryType: e.target.value as CorpusEntryType })
                }
              >
                {Object.entries(ENTRY_TYPE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </Field>
            {form.entryType === "PROJECT" && (
              <Field label="Project type">
                <Select
                  value={form.projectType ?? ""}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      projectType: (e.target.value || undefined) as ProjectType | undefined,
                    })
                  }
                >
                  <option value="">Unspecified</option>
                  <option value="COURSE">Course project</option>
                  <option value="SELF">Self project</option>
                  <option value="RESEARCH">Research project</option>
                  <option value="THESIS">Thesis</option>
                </Select>
              </Field>
            )}
            <Field label={form.entryType === "PROJECT" || form.entryType === "PUBLICATION" ? "Title" : "Role / title"}>
              <Input value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} required />
            </Field>
            <Field
              label="Organization"
              hint={form.entryType === "PROJECT" ? 'Company, team, or "Self Project"' : undefined}
            >
              <Input value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} required />
            </Field>
            <Field label="Location" hint="Optional">
              <Input value={form.location ?? ""} onChange={(e) => setForm({ ...form, location: e.target.value })} />
            </Field>
            <Field label="Guide / mentor" hint="Optional, e.g. a professor">
              <Input placeholder="Prof. Leena Vachhani" value={form.guide ?? ""} onChange={(e) => setForm({ ...form, guide: e.target.value })} />
            </Field>
            <Field label="Context" hint="Course code, club, competition…">
              <Input placeholder="Inter-IIT Tech Meet 2024" value={form.context ?? ""} onChange={(e) => setForm({ ...form, context: e.target.value })} />
            </Field>
            <Field label="URL" hint="Repo / DOI / demo link">
              <Input value={form.url ?? ""} onChange={(e) => setForm({ ...form, url: e.target.value })} />
            </Field>
            <Field label="Start date">
              <Input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} required />
            </Field>
            <Field label="End date">
              <Input
                type="date"
                value={form.endDate ?? ""}
                onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                disabled={form.isCurrent}
              />
            </Field>
            <label className="flex items-center gap-2 self-end pb-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 accent-[var(--color-accent)]"
                checked={form.isCurrent ?? false}
                onChange={(e) => setForm({ ...form, isCurrent: e.target.checked })}
              />
              Current role
            </label>
          </div>
          <Field label="Description">
            <Textarea value={form.description ?? ""} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={5} placeholder="What did you do in this role?" />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Achievements" hint="Comma-separated">
              <Input value={achievementsText} onChange={(e) => setAchievementsText(e.target.value)} />
            </Field>
            <Field label="Skills used" hint="Comma-separated">
              <Input value={skillsText} onChange={(e) => setSkillsText(e.target.value)} />
            </Field>
          </div>
          <div className="flex gap-2">
            <Button type="submit" variant="primary">
              {editingId === null ? "Add entry" : "Save changes"}
            </Button>
            {editingId !== null && (
              <Button
                type="button"
                onClick={() => {
                  setEditingId(null);
                  setForm(emptyForm);
                  setAchievementsText("");
                  setSkillsText("");
                }}
              >
                Cancel edit
              </Button>
            )}
          </div>
        </form>
      </Card>

      <h2 className="mb-3 text-lg font-semibold">Corpus entries</h2>
      {entries.length === 0 ? (
        <EmptyState title="No corpus entries yet" description="Add your first one above — these power resume tailoring." />
      ) : (
        <div className="mb-6 flex flex-col gap-3">
          {entries.map((entry) => (
            <Card key={entry.id}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-semibold">
                    {entry.role} <span className="font-normal text-muted">· {entry.company}</span>
                  </p>
                  <p className="text-sm text-muted">
                    {ENTRY_TYPE_LABELS[entry.entryType]}
                    {entry.projectType && ` (${entry.projectType.toLowerCase()})`}
                    {" · "}
                    {entry.startDate} – {entry.isCurrent ? "present" : (entry.endDate ?? "?")}
                    {entry.guide && ` · Guide: ${entry.guide}`}
                  </p>
                </div>
                <Badge tone={entry.embeddingStatus === "EMBEDDED" ? "success" : "warning"}>
                  {entry.embeddingStatus}
                </Badge>
              </div>
              {entry.description && <p className="mt-2 text-sm text-muted">{entry.description}</p>}
              <div className="mt-3 flex gap-2">
                <Button size="sm" onClick={() => startEdit(entry)}>
                  Edit
                </Button>
                <Button size="sm" variant="danger" onClick={() => handleDelete(entry.id)}>
                  Delete
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <SkillGroupsSection token={token} onError={setError} onSaved={setMessage} />
      <HighlightsSection token={token} type="SCHOLASTIC_ACHIEVEMENT" onError={setError} />
      <HighlightsSection token={token} type="CERTIFICATION" onError={setError} />
      <HighlightsSection token={token} type="EXTRACURRICULAR" onError={setError} />
    </AppLayout>
  );
}
