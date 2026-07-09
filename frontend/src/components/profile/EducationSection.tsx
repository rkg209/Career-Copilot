import { useEffect, useState, type FormEvent } from "react";
import {
  addEducation,
  deleteEducation,
  listEducation,
  updateEducation,
  type Education,
  type EducationRequest,
  type EducationScoreType,
} from "../../api";
import { Button, Card, EmptyState, Field, Input, Select } from "../ui";

const emptyForm: EducationRequest = {
  degree: "",
  institution: "",
  university: "",
  specialization: "",
  scoreValue: "",
};

export function EducationSection({
  token,
  onError,
}: {
  token: string;
  onError: (message: string) => void;
}) {
  const [entries, setEntries] = useState<Education[]>([]);
  const [form, setForm] = useState<EducationRequest>(emptyForm);
  const [scoreType, setScoreType] = useState<EducationScoreType | "">("");
  const [editingId, setEditingId] = useState<number | null>(null);

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function refresh() {
    listEducation(token)
      .then(setEntries)
      .catch(() => onError("Could not load education entries."));
  }

  function resetForm() {
    setForm(emptyForm);
    setScoreType("");
    setEditingId(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const req: EducationRequest = {
      ...form,
      university: form.university || undefined,
      specialization: form.specialization || undefined,
      scoreType: scoreType || undefined,
      scoreValue: form.scoreValue || undefined,
      startYear: form.startYear || undefined,
      endYear: form.endYear || undefined,
    };
    try {
      if (editingId === null) {
        await addEducation(req, token);
      } else {
        await updateEducation(editingId, req, token);
      }
      resetForm();
      refresh();
    } catch {
      onError("Could not save education entry. Degree and institution are required.");
    }
  }

  function startEdit(entry: Education) {
    setEditingId(entry.id);
    setForm({
      degree: entry.degree,
      institution: entry.institution,
      university: entry.university ?? "",
      specialization: entry.specialization ?? "",
      startYear: entry.startYear,
      endYear: entry.endYear,
      scoreValue: entry.scoreValue ?? "",
      displayOrder: entry.displayOrder,
    });
    setScoreType(entry.scoreType ?? "");
  }

  async function handleDelete(id: number) {
    try {
      await deleteEducation(id, token);
      refresh();
    } catch {
      onError("Could not delete education entry.");
    }
  }

  return (
    <Card className="mb-6">
      <h2 className="mb-1 text-lg font-semibold">Education</h2>
      <p className="mb-4 text-sm text-muted">
        Degrees and schooling, most recent first — rendered as the education table on your resume.
      </p>

      {entries.length === 0 ? (
        <EmptyState
          title="No education entries yet"
          description="Add your degree(s) and schooling below."
        />
      ) : (
        <div className="mb-4 flex flex-col gap-2">
          {entries.map((entry) => (
            <div
              key={entry.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 text-sm"
            >
              <div>
                <span className="font-medium">{entry.degree}</span>
                {entry.specialization && <span className="text-muted"> · {entry.specialization}</span>}
                <span className="text-muted"> — {entry.institution}</span>
                {entry.scoreValue && (
                  <span className="text-muted">
                    {" "}
                    ({entry.scoreType === "PERCENTAGE" ? `${entry.scoreValue}%` : `${entry.scoreType} ${entry.scoreValue}`})
                  </span>
                )}
                {(entry.startYear || entry.endYear) && (
                  <span className="text-muted">
                    {" "}
                    · {entry.startYear ?? ""}{entry.startYear && entry.endYear ? "–" : ""}{entry.endYear ?? ""}
                  </span>
                )}
              </div>
              <div className="flex gap-2">
                <Button size="sm" onClick={() => startEdit(entry)}>
                  Edit
                </Button>
                <Button size="sm" variant="danger" onClick={() => handleDelete(entry.id)}>
                  Delete
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Degree" hint='e.g. "B.Tech.", "Class XII"'>
            <Input value={form.degree} onChange={(e) => setForm({ ...form, degree: e.target.value })} required />
          </Field>
          <Field label="Institution">
            <Input value={form.institution} onChange={(e) => setForm({ ...form, institution: e.target.value })} required />
          </Field>
          <Field label="University / board" hint="Optional, if different">
            <Input value={form.university ?? ""} onChange={(e) => setForm({ ...form, university: e.target.value })} />
          </Field>
          <Field label="Specialization" hint="Optional">
            <Input value={form.specialization ?? ""} onChange={(e) => setForm({ ...form, specialization: e.target.value })} />
          </Field>
          <Field label="Start year">
            <Input
              type="number"
              value={form.startYear ?? ""}
              onChange={(e) => setForm({ ...form, startYear: e.target.value ? Number(e.target.value) : undefined })}
            />
          </Field>
          <Field label="End year" hint="Expected or actual">
            <Input
              type="number"
              value={form.endYear ?? ""}
              onChange={(e) => setForm({ ...form, endYear: e.target.value ? Number(e.target.value) : undefined })}
            />
          </Field>
          <Field label="Score type">
            <Select value={scoreType} onChange={(e) => setScoreType(e.target.value as EducationScoreType | "")}>
              <option value="">None</option>
              <option value="CPI">CPI / CGPA</option>
              <option value="PERCENTAGE">Percentage</option>
              <option value="GRADE">Grade</option>
            </Select>
          </Field>
          <Field label="Score" hint='e.g. "9.24", "94.2", "A"'>
            <Input value={form.scoreValue ?? ""} onChange={(e) => setForm({ ...form, scoreValue: e.target.value })} disabled={!scoreType} />
          </Field>
        </div>
        <div className="flex gap-2">
          <Button type="submit" variant="primary">
            {editingId === null ? "Add education" : "Save changes"}
          </Button>
          {editingId !== null && (
            <Button type="button" onClick={resetForm}>
              Cancel edit
            </Button>
          )}
        </div>
      </form>
    </Card>
  );
}
