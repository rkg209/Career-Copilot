import { useState } from "react";
import { answerQuestion, type AnswerResult } from "../../api";
import { Alert, Badge, Button, Card, Textarea } from "../ui";
import type { Tone } from "../ui";

interface PracticeCardProps {
  questionId: number;
  questionText: string;
  token: string;
  onAnswered?: (result: AnswerResult) => void;
}

function gradeTone(grade: number): Tone {
  if (grade <= 2) return "danger";
  if (grade === 3) return "warning";
  return "success";
}

/** Question -> typed answer -> LLM grade + feedback + next-review summary. Shared by the
 * generate/practice flow and the due-review queue. */
export function PracticeCard({ questionId, questionText, token, onAnswered }: PracticeCardProps) {
  const [answerText, setAnswerText] = useState("");
  const [result, setResult] = useState<AnswerResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!answerText.trim()) return;
    setSubmitting(true);
    setError(null);
    answerQuestion(questionId, answerText, token)
      .then((r) => {
        setResult(r);
        onAnswered?.(r);
      })
      .catch(() => setError("Failed to grade your answer. Please try again."))
      .finally(() => setSubmitting(false));
  };

  return (
    <Card className="flex flex-col gap-4">
      <p className="font-medium text-fg">{questionText}</p>

      {error && <Alert variant="error">{error}</Alert>}

      {result ? (
        <div className="flex flex-col gap-2" aria-live="polite">
          <div className="flex items-center gap-2">
            <Badge tone={gradeTone(result.qualityGrade)}>Grade {result.qualityGrade}/5</Badge>
            <span className="text-xs text-muted">
              Next review in {result.nextReview.intervalDays} day
              {result.nextReview.intervalDays === 1 ? "" : "s"} (EF {result.nextReview.easeFactor})
            </span>
          </div>
          <p className="text-sm text-muted">{result.feedback}</p>
          <Button size="sm" onClick={() => { setResult(null); setAnswerText(""); }}>
            Try another answer
          </Button>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-3">
          <Textarea
            rows={5}
            placeholder="Type your answer..."
            value={answerText}
            onChange={(e) => setAnswerText(e.target.value)}
          />
          <Button type="submit" variant="primary" disabled={submitting || !answerText.trim()}>
            {submitting ? "Grading…" : "Submit answer"}
          </Button>
        </form>
      )}
    </Card>
  );
}
