import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { listDueCards } from "../api";
import { useFetch } from "../lib/useFetch";
import { AppLayout } from "../components/AppLayout";
import { PracticeCard } from "../components/trainer/PracticeCard";
import { Alert, Badge, Button, EmptyState, PageHeader, SkeletonList } from "../components/ui";

export function ReviewQueuePage() {
  const token = localStorage.getItem("token");
  const [answeredIds, setAnsweredIds] = useState<number[]>([]);
  const {
    data: page,
    error,
    loading,
  } = useFetch(() => listDueCards(token!), [token]);
  const cards = page?.content ?? [];

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  const remaining = cards.filter((c) => !answeredIds.includes(c.mockQuestionId));

  return (
    <AppLayout>
      <PageHeader title="Due for review" subtitle="Weakest and oldest cards first." />

      {error && <Alert variant="error" className="mb-4">{error}</Alert>}

      {loading ? (
        <SkeletonList rows={3} rowClassName="h-28" />
      ) : remaining.length === 0 && !error ? (
        <EmptyState
          title="Nothing due right now"
          description="Generate a fresh batch of practice questions to keep building your queue."
          action={
            <Link to="/trainer">
              <Button variant="primary">Go to practice</Button>
            </Link>
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          {remaining.map((card) => (
            <div key={card.mockQuestionId} className="flex flex-col gap-2">
              {card.isNew ? (
                <Badge tone="accent">New</Badge>
              ) : (
                <Badge tone="neutral">Repetition {card.repetitions}</Badge>
              )}
              <PracticeCard
                questionId={card.mockQuestionId}
                questionText={card.questionText}
                token={token}
                onAnswered={() =>
                  setAnsweredIds((ids) => [...ids, card.mockQuestionId])
                }
              />
            </div>
          ))}
        </div>
      )}
    </AppLayout>
  );
}
