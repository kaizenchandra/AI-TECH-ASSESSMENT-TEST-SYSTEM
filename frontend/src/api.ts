let csrf: { token: string; header: string } | undefined;
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function refreshCsrf() {
  csrf = await fetch("/api/v1/csrf", { credentials: "same-origin" }).then((r) =>
    r.json(),
  );
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!csrf) await refreshCsrf();
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");
  if (init.method && !["GET", "HEAD"].includes(init.method))
    headers.set(csrf!.header, csrf!.token);
  const response = await fetch("/api/v1" + path, {
    ...init,
    signal: init.signal ?? AbortSignal.timeout(15000),
    headers,
    credentials: "same-origin",
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      problem.detail ||
        problem.message ||
        `Request failed (${response.status})`,
    );
  }
  if (response.status === 204 || response.headers.get("content-length") === "0")
    return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
export const post = <T>(
  path: string,
  body?: unknown,
  headers?: Record<string, string>,
) =>
  api<T>(path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
    headers,
  });
export type Actor = {
  id: string;
  org: string;
  role: string;
  verified: boolean;
  email: string;
};
export type Answer = {
  selected: number[];
  language: string | null;
  code: string | null;
  flagged: boolean;
  skipped?: boolean;
};
export type Question = {
  id: string;
  type: "SINGLE" | "MULTIPLE" | "CODING";
  title: string;
  description: string;
  skill: string;
  options: string[];
  language: string | null;
  starterCode: string | null;
  samples: { input: string; output: string }[];
};
export type Content = {
  job?: {
    title?: string;
    department?: string;
    skills?: {
      skill: string;
      weight: number;
      required: boolean;
      proficiency: string;
    }[];
  };
  categories?: Record<string, number>;
  skills?: Record<string, number>;
  passingPercent?: number;
  latestStart?: string;
  completionDeadline?: string;
  maxPauses?: number;
  maxPauseSeconds?: number;
  totalPauseSeconds?: number;
  languages?: string[];
  marking?: {
    category: string;
    points: number;
    penalty: number;
    partialCredit: boolean;
  }[];
  policyVersion?: string;
  attemptLimit?: number;
  questionCount?: number;
  title: string;
  instructions: string;
  durationMinutes: number;
  timerPolicy: string;
  backNavigation: boolean;
  cameraRequired: boolean;
  microphoneRequired: boolean;
  questions: Question[];
};
export type Attempt = {
  monitoringAllowed?: boolean;
  id: string;
  status: string;
  revision: number;
  currentQuestion: string | null;
  remainingMs: number;
  serverTime: string;
  assessment: Content;
  answers: { question_id: string; answer: Answer; revision: number }[];
  visited: string[];
};
