import { useEffect, useState } from "react";
import { listSkillGroups, replaceSkillGroups, type SkillGroup } from "../../api";
import { Button, Card, Field, Input } from "../ui";

interface EditableGroup {
  name: string;
  skillsText: string;
}

export function SkillGroupsSection({
  token,
  onError,
  onSaved,
}: {
  token: string;
  onError: (message: string) => void;
  onSaved: (message: string) => void;
}) {
  const [groups, setGroups] = useState<EditableGroup[]>([]);

  useEffect(() => {
    listSkillGroups(token)
      .then((loaded) =>
        setGroups(loaded.map((g) => ({ name: g.name, skillsText: g.skills.join(", ") }))),
      )
      .catch(() => onError("Could not load skill groups."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function updateGroup(index: number, patch: Partial<EditableGroup>) {
    setGroups(groups.map((g, i) => (i === index ? { ...g, ...patch } : g)));
  }

  async function handleSave() {
    const payload: SkillGroup[] = groups
      .filter((g) => g.name.trim().length > 0)
      .map((g, i) => ({
        name: g.name.trim(),
        skills: g.skillsText
          .split(",")
          .map((s) => s.trim())
          .filter((s) => s.length > 0),
        displayOrder: i,
      }));
    try {
      const saved = await replaceSkillGroups(payload, token);
      setGroups(saved.map((g) => ({ name: g.name, skillsText: g.skills.join(", ") })));
      onSaved("Skill groups saved.");
    } catch {
      onError("Could not save skill groups. Names must be unique and non-empty.");
    }
  }

  return (
    <Card className="mb-6">
      <h2 className="mb-1 text-lg font-semibold">Technical skills</h2>
      <p className="mb-4 text-sm text-muted">
        Group skills by category, e.g. “Programming Languages: C++, Python” — rendered as the
        Technical Skills section on your resume.
      </p>
      <div className="flex flex-col gap-3">
        {groups.map((group, i) => (
          <div key={i} className="grid grid-cols-1 items-end gap-3 sm:grid-cols-[1fr_2fr_auto]">
            <Field label={i === 0 ? "Category" : undefined}>
              <Input
                placeholder="Programming Languages"
                value={group.name}
                onChange={(e) => updateGroup(i, { name: e.target.value })}
              />
            </Field>
            <Field label={i === 0 ? "Skills (comma-separated)" : undefined}>
              <Input
                placeholder="C++, Python, Java"
                value={group.skillsText}
                onChange={(e) => updateGroup(i, { skillsText: e.target.value })}
              />
            </Field>
            <Button
              type="button"
              variant="danger"
              size="sm"
              onClick={() => setGroups(groups.filter((_, j) => j !== i))}
            >
              Remove
            </Button>
          </div>
        ))}
      </div>
      <div className="mt-4 flex gap-2">
        <Button type="button" onClick={() => setGroups([...groups, { name: "", skillsText: "" }])}>
          Add group
        </Button>
        <Button type="button" variant="primary" onClick={handleSave}>
          Save skill groups
        </Button>
      </div>
    </Card>
  );
}
