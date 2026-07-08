const KNOWN_HEADINGS =
  /^(responsibilities|requirements|qualifications|preferred qualifications|minimum qualifications|nice to have|about the role|about us|about the company|about you|what you'?ll do|what you will do|what we'?re looking for|who you are|benefits|perks|compensation|salary|education|experience|duties|overview|summary|job description|role overview|why join us|equal opportunity(?: (?:employer|statement))?|skills)$/i;

const BULLET_PREFIX = /^\s*(?:[-*•‣▪]|\d+[.)])\s+/;

export interface JobDescriptionSection {
  heading: string | null;
  bullets: string[];
  paragraphs: string[];
}

function isHeading(line: string): boolean {
  const trimmed = line.trim();
  if (!trimmed || trimmed.length > 60) return false;
  if (/[.!?]$/.test(trimmed)) return false;
  const stripped = trimmed.replace(/:\s*$/, "");
  if (KNOWN_HEADINGS.test(stripped)) return true;
  if (/:$/.test(trimmed) && stripped.split(/\s+/).length <= 6) return true;
  return false;
}

export function parseJobDescription(text: string): JobDescriptionSection[] {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  const sections: JobDescriptionSection[] = [];
  let current: JobDescriptionSection = { heading: null, bullets: [], paragraphs: [] };
  let paragraphBuffer: string[] = [];

  const flushParagraph = () => {
    if (paragraphBuffer.length > 0) {
      current.paragraphs.push(paragraphBuffer.join(" ").trim());
      paragraphBuffer = [];
    }
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();

    if (!line) {
      flushParagraph();
      continue;
    }

    if (isHeading(line)) {
      flushParagraph();
      if (current.heading !== null || current.bullets.length > 0 || current.paragraphs.length > 0) {
        sections.push(current);
      }
      current = { heading: line.replace(/:\s*$/, ""), bullets: [], paragraphs: [] };
      continue;
    }

    if (BULLET_PREFIX.test(line)) {
      flushParagraph();
      current.bullets.push(line.replace(BULLET_PREFIX, "").trim());
      continue;
    }

    paragraphBuffer.push(line);
  }

  flushParagraph();
  if (current.heading !== null || current.bullets.length > 0 || current.paragraphs.length > 0) {
    sections.push(current);
  }

  return sections;
}
