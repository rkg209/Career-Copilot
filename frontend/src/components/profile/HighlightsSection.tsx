import { useEffect, useState, type FormEvent } from "react";
import {
  addHighlight,
  deleteHighlight,
  listHighlights,
  updateHighlight,
  type Highlight,
  type HighlightType,
} from "../../api";
import { Button, Card, EmptyState, Field, Input } from "../ui";

const TYPE_LABELS: Record<HighlightType, { title: string; hint: string; example: string }> = {
  SCHOLASTIC_ACHIEVEMENT: {
    title: "Scholastic achievements",
    hint: "Ranks, percentiles, scholarships, academic awards",
    example: "97.4 %ile in JEE Advanced among 0.16M candidates",
  },
  CERTIFICATION: {
    title: "Certifications",
    hint: "Name + issuer",
    example: "AWS Certified Solutions Architect – Associate (Amazon)",
  },
  EXTRACURRICULAR: {
    title: "Extracurriculars",
    hint: "Sports, cultural activities, competitions, volunteering",
    example: "1st runner-up in inter-hostel chess championship",
  },
};

export function HighlightsSection({
  token,
  type,
  onError,
}: {
  token: string;
  type: HighlightType;
  onError: (message: string) => void;
}) {
  const [items, setItems] = useState<Highlight[]>([]);
  const [text, setText] = useState("");
  const [yearLabel, setYearLabel] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);

  const labels = TYPE_LABELS[type];

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, type]);

  function refresh() {
    listHighlights(token, type)
      .then(setItems)
      .catch(() => onError(`Could not load ${labels.title.toLowerCase()}.`));
  }

  function resetForm() {
    setText("");
    setYearLabel("");
    setEditingId(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const req = { type, text: text.trim(), yearLabel: yearLabel.trim() || undefined };
    try {
      if (editingId === null) {
        await addHighlight(req, token);
      } else {
        await updateHighlight(editingId, req, token);
      }
      resetForm();
      refresh();
    } catch {
      onError(`Could not save the entry (max 300 characters).`);
    }
  }

  async function handleDelete(id: number) {
    try {
      await deleteHighlight(id, token);
      refresh();
    } catch {
      onError("Could not delete the entry.");
    }
  }

  return (
    <Card className="mb-6">
      <h2 className="mb-1 text-lg font-semibold">{labels.title}</h2>
      <p className="mb-4 text-sm text-muted">{labels.hint}</p>

      {items.length === 0 ? (
        <EmptyState title={`No ${labels.title.toLowerCase()} yet`} description={`e.g. “${labels.example}”`} />
      ) : (
        <ul className="mb-4 flex flex-col gap-2">
          {items.map((item) => (
            <li
              key={item.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 text-sm"
            >
              <span>
                {item.text}
                {item.yearLabel && <span className="text-muted"> ({item.yearLabel})</span>}
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  onClick={() => {
                    setEditingId(item.id);
                    setText(item.text);
                    setYearLabel(item.yearLabel ?? "");
                  }}
                >
                  Edit
                </Button>
                <Button size="sm" variant="danger" onClick={() => handleDelete(item.id)}>
                  Delete
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={handleSubmit} className="grid grid-cols-1 items-end gap-3 sm:grid-cols-[3fr_1fr_auto]">
        <Field label="Entry">
          <Input value={text} onChange={(e) => setText(e.target.value)} placeholder={labels.example} required />
        </Field>
        <Field label="Year" hint="Optional">
          <Input value={yearLabel} onChange={(e) => setYearLabel(e.target.value)} placeholder="2024" />
        </Field>
        <div className="flex gap-2 pb-1">
          <Button type="submit" variant="primary" size="sm">
            {editingId === null ? "Add" : "Save"}
          </Button>
          {editingId !== null && (
            <Button type="button" size="sm" onClick={resetForm}>
              Cancel
            </Button>
          )}
        </div>
      </form>
    </Card>
  );
}
