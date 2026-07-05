import type { ApplicationStatus } from "../../api";
import { Badge, type Tone } from "./Badge";

const STATUS_TONE: Record<ApplicationStatus, Tone> = {
  SAVED: "neutral",
  APPLIED: "accent",
  PHONE_SCREEN: "accent",
  TECHNICAL_INTERVIEW: "accent",
  FINAL_ROUND: "warning",
  OFFER: "success",
  REJECTED: "danger",
  WITHDRAWN: "neutral",
};

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{status.replaceAll("_", " ")}</Badge>;
}
