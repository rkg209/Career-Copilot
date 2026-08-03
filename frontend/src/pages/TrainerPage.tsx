import { useState } from "react";
import { Navigate } from "react-router-dom";
import {
  generateQuestions,
  type InterviewRoundType,
  type MockQuestion,
} from "../api";
import { AppLayout } from "../components/AppLayout";
import { PracticeCard } from "../components/trainer/PracticeCard";
import { Alert, Button, Card, Field, Input, PageHeader, Select } from "../components/ui";

const ROUND_TYPES: InterviewRoundType[] = [
  "PHONE_SCREEN",
  "TECHNICAL",
  "SYSTEM_DESIGN",
  "BEHAVIOURAL",
  "TAKE_HOME",
  "PANEL",
  "HR",
  "OTHER",
];

export function TrainerPage() {
  const token = localStorage.getItem("token");

  const [company, setCompany] = useState("");
  const [roleCategory, setRoleCategory] = useState("");
  const [roundType, setRoundType] = useState<InterviewRoundType | "">("");
  const [count, setCount] = useState(5);
  const [questions, setQuestions] = useState<MockQuestion[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const generate = (e: React.FormEvent) => {
    e.preventDefault();
    setGenerating(true);
    setError(null);
    generateQuestions(
      {
        company: company.trim() || undefined,
        roleCategory: roleCategory.trim() || undefined,
        roundType: roundType || undefined,
        count,
      },
      token,
    )
      .then((created) => {
        setQuestions(created);
        setSelectedId(created[0]?.id ?? null);
      })
      .catch(() => setError("Failed to generate practice questions."))
      .finally(() => setGenerating(false));
  };

  const selected = questions.find((q) => q.id === selectedId) ?? null;

  return (
    <AppLayout>
      <PageHeader
        title="Practice interviews"
        subtitle="Generate questions from your own logs, public intel, and role patterns."
      />

      {error && <Alert variant="error" className="mb-4">{error}</Alert>}

      <Card className="mb-6">
        <form onSubmit={generate} className="flex flex-col gap-4 sm:flex-row sm:items-end sm:flex-wrap">
          <Field label="Company" className="sm:w-56">
            <Input value={company} onChange={(e) => setCompany(e.target.value)} placeholder="Optional" />
          </Field>
          <Field label="Role category" className="sm:w-56">
            <Input
              value={roleCategory}
              onChange={(e) => setRoleCategory(e.target.value)}
              placeholder="e.g. Software Engineer"
            />
          </Field>
          <Field label="Round type" className="sm:w-56">
            <Select
              value={roundType}
              onChange={(e) => setRoundType(e.target.value as InterviewRoundType | "")}
            >
              <option value="">Any</option>
              {ROUND_TYPES.map((rt) => (
                <option key={rt} value={rt}>
                  {rt.replaceAll("_", " ")}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Count" className="sm:w-28">
            <Input
              type="number"
              min={1}
              max={25}
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
            />
          </Field>
          <Button type="submit" variant="primary" disabled={generating}>
            {generating ? "Generating…" : "Generate questions"}
          </Button>
        </form>
      </Card>

      {questions.length > 0 && (
        <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
          <div className="flex flex-col gap-2">
            {questions.map((q) => (
              <button
                key={q.id}
                type="button"
                onClick={() => setSelectedId(q.id)}
                className={`card p-3 text-left text-sm transition-colors duration-150 ${
                  q.id === selectedId ? "border-accent" : "hover:border-accent"
                }`}
              >
                <p className="line-clamp-2 font-medium">{q.questionText}</p>
                <p className="mt-1 text-xs text-muted">{q.source.replaceAll("_", " ")}</p>
              </button>
            ))}
          </div>

          <div>
            {selected && (
              <PracticeCard
                key={selected.id}
                questionId={selected.id}
                questionText={selected.questionText}
                token={token}
              />
            )}
          </div>
        </div>
      )}
    </AppLayout>
  );
}
