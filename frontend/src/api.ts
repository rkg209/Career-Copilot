const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface UserResponse {
  id: string;
  email: string;
}

export interface AuthResponse {
  token: string;
  expiresIn: number;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function register(email: string, password: string): Promise<UserResponse> {
  return request<UserResponse>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function me(token: string): Promise<UserResponse> {
  return request<UserResponse>("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export interface JobPosting {
  id: number;
  source: string;
  externalId: string | null;
  title: string;
  company: string;
  location: string | null;
  remote: boolean;
  employmentType: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  skills: string[];
  responsibilities: string[];
  fullText: string;
  url: string | null;
  postedAt: string | null;
}

export interface Page<T> {
  content: T[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface JobSearchParams {
  q?: string;
  location?: string;
  remoteType?: "remote" | "hybrid" | "onsite";
  employmentType?: "full_time" | "contract" | "part_time";
  salaryMin?: number;
  postedAfter?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface JobPasteRequest {
  title: string;
  company: string;
  location?: string;
  url?: string;
  rawText: string;
}

function queryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}

export function listJobs(page = 0, size = 20): Promise<Page<JobPosting>> {
  return request<Page<JobPosting>>(`/api/v1/jobs${queryString({ page, size })}`);
}

export function searchJobs(params: JobSearchParams): Promise<Page<JobPosting>> {
  return request<Page<JobPosting>>(
    `/api/v1/jobs/search${queryString(params as Record<string, unknown>)}`,
  );
}

export function getJob(id: number | string): Promise<JobPosting> {
  return request<JobPosting>(`/api/v1/jobs/${id}`);
}

export function pasteJob(req: JobPasteRequest, token: string): Promise<JobPosting> {
  return request<JobPosting>("/api/v1/jobs/paste", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(req),
  });
}

export interface Profile {
  fullName: string | null;
  phone: string | null;
  headline: string | null;
  summary: string | null;
  location: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  targetRoles: string[];
  targetLocations: string[];
  openToRemote: boolean;
}

export interface ExperienceRequest {
  role: string;
  company: string;
  location?: string;
  startDate: string;
  endDate?: string;
  isCurrent?: boolean;
  description?: string;
  achievements?: string[];
  skillsUsed?: string[];
}

export interface Experience {
  id: number;
  role: string;
  company: string;
  location: string | null;
  startDate: string;
  endDate: string | null;
  isCurrent: boolean;
  description: string | null;
  achievements: string[];
  skillsUsed: string[];
  embeddingStatus: "PENDING" | "EMBEDDED";
  createdAt: string;
  updatedAt: string;
}

function authHeader(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}

export function getProfile(token: string): Promise<Profile> {
  return request<Profile>("/api/v1/profile", { headers: authHeader(token) });
}

export function putProfile(profile: Profile, token: string): Promise<Profile> {
  return request<Profile>("/api/v1/profile", {
    method: "PUT",
    headers: authHeader(token),
    body: JSON.stringify(profile),
  });
}

export function listExperience(token: string): Promise<Experience[]> {
  return request<Experience[]>("/api/v1/profile/experience", { headers: authHeader(token) });
}

export function addExperience(req: ExperienceRequest, token: string): Promise<Experience> {
  return request<Experience>("/api/v1/profile/experience", {
    method: "POST",
    headers: authHeader(token),
    body: JSON.stringify(req),
  });
}

export function updateExperience(
  id: number,
  req: ExperienceRequest,
  token: string,
): Promise<Experience> {
  return request<Experience>(`/api/v1/profile/experience/${id}`, {
    method: "PUT",
    headers: authHeader(token),
    body: JSON.stringify(req),
  });
}

export function deleteExperience(id: number, token: string): Promise<void> {
  return request<void>(`/api/v1/profile/experience/${id}`, {
    method: "DELETE",
    headers: authHeader(token),
  });
}

export interface TemplateInfo {
  id: string;
  displayName: string;
  kind: "RESUME" | "COVER_LETTER";
}

export interface ResumeVersion {
  resumeVersionId: number;
  templateId: string;
  jobPostingId: number;
  llmModel: string;
  tokenCount: number;
  renderStatus: "RENDERED" | "FAILED" | "PENDING";
  createdAt: string;
}

export interface ResumeDetail extends Omit<ResumeVersion, "renderStatus"> {
  renderStatus: string;
  renderError: string | null;
  jsonPayload: unknown;
}

export interface CoverLetterVersion {
  coverLetterId: number;
  templateId: string;
  jobPostingId: number;
  resumeVersionId: number | null;
  llmModel: string;
  tokenCount: number;
  renderStatus: "RENDERED" | "FAILED" | "PENDING";
  createdAt: string;
}

export function listTemplates(token: string): Promise<TemplateInfo[]> {
  return request<TemplateInfo[]>("/api/v1/tailoring/templates", {
    headers: authHeader(token),
  });
}

export function tailorResume(
  jobPostingId: number,
  templateId: string,
  token: string,
): Promise<ResumeVersion> {
  return request<ResumeVersion>("/api/v1/tailoring/tailor", {
    method: "POST",
    headers: authHeader(token),
    body: JSON.stringify({ jobPostingId, templateId }),
  });
}

export function listResumes(token: string, page = 0, size = 20): Promise<Page<ResumeVersion>> {
  return request<Page<ResumeVersion>>(
    `/api/v1/tailoring/resumes${queryString({ page, size })}`,
    { headers: authHeader(token) },
  );
}

export function getResume(id: number, token: string): Promise<ResumeDetail> {
  return request<ResumeDetail>(`/api/v1/tailoring/resumes/${id}`, {
    headers: authHeader(token),
  });
}

export function generateCoverLetter(
  jobPostingId: number,
  token: string,
  options: { templateId?: string; resumeVersionId?: number; tone?: string } = {},
): Promise<CoverLetterVersion> {
  return request<CoverLetterVersion>("/api/v1/tailoring/cover-letter", {
    method: "POST",
    headers: authHeader(token),
    body: JSON.stringify({ jobPostingId, ...options }),
  });
}

export interface AtsGapReport {
  scoringResultId: number;
  resumeVersionId: number;
  jobPostingId: number;
  score: number;
  breakdown: {
    requiredCoverage: number;
    preferredCoverage: number;
    keywordDensity: number;
  };
  missingRequired: string[];
  missingPreferred: string[];
  nearMisses: { skill: string; similarity: number; suggestedPhrase: string }[];
  fixes: { skill: string; suggestedBullet: string; priority: number }[];
  scoredAt: string;
}

export function scoreResume(
  resumeVersionId: number,
  jobPostingId: number,
  token: string,
): Promise<AtsGapReport> {
  return request<AtsGapReport>("/api/v1/scoring/score", {
    method: "POST",
    headers: authHeader(token),
    body: JSON.stringify({ resumeVersionId, jobPostingId }),
  });
}

export function getScore(resumeVersionId: number, token: string): Promise<AtsGapReport> {
  return request<AtsGapReport>(`/api/v1/scoring/results/${resumeVersionId}`, {
    headers: authHeader(token),
  });
}

/** PDF endpoints return binary — bypasses the JSON `request` wrapper and triggers a download. */
export async function downloadPdf(path: string, filename: string, token: string): Promise<void> {
  const response = await fetch(`${API_BASE}${path}`, { headers: authHeader(token) });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Download failed with status ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function downloadResumePdf(id: number, token: string): Promise<void> {
  return downloadPdf(`/api/v1/tailoring/resumes/${id}/pdf`, `resume-${id}.pdf`, token);
}

export function downloadCoverLetterPdf(id: number, token: string): Promise<void> {
  return downloadPdf(`/api/v1/tailoring/cover-letters/${id}/pdf`, `cover-letter-${id}.pdf`, token);
}
