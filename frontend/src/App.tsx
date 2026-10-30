import { AssessmentOverview } from "./AssessmentOverview";
import { Readiness } from "./Readiness";
import { Accommodations } from "./Accommodations";
import { Blueprints } from "./Blueprints";
import { JobsPanel } from "./Jobs";
import { QuestionBank } from "./QuestionBank";
import { Retakes } from "./Retakes";
import { MemberRoles } from "./MemberRoles";
import {
  GeneratedQuestion,
  GeneratedQuestionView,
  AiAssessmentPreparation,
} from "./GeneratedQuestions";

import { lazy, Suspense, useState, useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ReportsPanel, ReleasedResults } from "./Reports";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  LayoutDashboard,
  ClipboardList,
  Users,
  Sparkles,
  BarChart3,
  Settings,
  LogOut,
  ArrowUpRight,
  Plus,
} from "lucide-react";
import { Actor, Attempt, Content, api, post, refreshCsrf } from "./api";
const Workspace = lazy(() =>
  import("./Workspace").then((module) => ({ default: module.Workspace })),
);
const errorMessage = (e: unknown) =>
  e instanceof Error ? e.message : "Request failed";
const authSchema = z.object({
  email: z.email(),
  password: z.string().min(12).max(128),
  organization: z.string(),
});
type Auth = z.infer<typeof authSchema>;
function Authentication({ done }: { done: () => void }) {
  const [mode, setMode] = useState("login"),
    [message, setMessage] = useState(""),
    [token, setToken] = useState("");
  const invitation =
    new URLSearchParams(location.hash.slice(1)).get("invite") ?? "";
  const form = useForm<Auth>({
    resolver: zodResolver(authSchema),
    defaultValues: { organization: "", email: "", password: "" },
  });
  async function submit(values: Auth) {
    try {
      if (mode === "signup") {
        await post("/register", { ...values, invitation });
        setMode("verify");
        setMessage("Check your email for your verification token.");
      } else {
        await api("/login", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: new URLSearchParams({
            username: values.email,
            password: values.password,
          }).toString(),
        });
        await refreshCsrf();
        done();
      }
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  return (
    <div className="auth">
      <section className="auth-story">
        <span className="brand">C / CRITERION</span>
        <h1>
          Good decisions
          <br />
          start with
          <br />
          <em>clear evidence.</em>
        </h1>
        <p>
          A considered space for technical assessments.
          <br />
          Built around skills, with people in control.
        </p>
        <div className="story-stat">
          <strong>Skills first.</strong>
          <span>Evidence-led assessment · Human-reviewed AI</span>
        </div>
      </section>
      <main className="auth-form">
        <p className="eyebrow">Welcome to Criterion</p>
        <h1>
          {mode === "verify"
            ? "Verify your email"
            : mode === "signup"
              ? "Create your account"
              : "Sign in to your workspace"}
        </h1>
        <p className="muted">
          {invitation
            ? "Your invitation is ready. Use the invited email address."
            : "Manage assessments, invite talent, and review the evidence."}
        </p>
        {mode === "verify" ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void post("/verify", { token })
                .then(() => {
                  setMode("login");
                  setMessage("Email verified. You can now sign in.");
                })
                .catch((e) => setMessage(errorMessage(e)));
            }}
          >
            <label>
              Email verification token
              <input
                value={token}
                onChange={(e) => setToken(e.target.value)}
                required
              />
            </label>
            <button>Verify email</button>
          </form>
        ) : (
          <form onSubmit={form.handleSubmit(submit)}>
            <label>
              Email address
              <input
                type="email"
                autoComplete="username"
                {...form.register("email")}
              />
            </label>
            <label>
              Password
              <input
                type="password"
                autoComplete={
                  mode === "signup" ? "new-password" : "current-password"
                }
                {...form.register("password")}
              />
            </label>
            {mode === "signup" && !invitation && (
              <label>
                Organization name
                <input {...form.register("organization")} required />
              </label>
            )}
            {Object.values(form.formState.errors).map((error, i) => (
              <p role="alert" key={i}>
                {error.message}
              </p>
            ))}
            <button
              className="w-full mt-3"
              disabled={form.formState.isSubmitting}
            >
              {mode === "signup" ? "Create account" : "Sign in"} →
            </button>
          </form>
        )}
        <p role="status">{message}</p>
        <div className="flex gap-3">
          <button
            className="text-button"
            onClick={() => setMode(mode === "signup" ? "login" : "signup")}
          >
            {mode === "signup"
              ? "Already registered? Sign in"
              : "Create an account"}
          </button>
          <button className="text-button" onClick={() => setMode("verify")}>
            Verify email
          </button>
        </div>
      </main>
    </div>
  );
}
const sampleQuestions = () => [
  {
    id: crypto.randomUUID(),
    type: "SINGLE",
    title: "Choose the collection contract",
    description:
      "Which Java interface specifies a collection with no duplicate elements?",
    skill: "java",
    options: ["List", "Set", "Queue", "Deque"],
    correct: [1],
    points: 10,
    penalty: 0,
    partialCredit: false,
    language: null,
    starterCode: null,
    samples: [],
  },
  {
    id: crypto.randomUUID(),
    type: "MULTIPLE",
    title: "Identify transaction properties",
    description: "Select the properties in the ACID transaction model.",
    skill: "sql",
    options: ["Atomicity", "Consistency", "Inheritance", "Durability"],
    correct: [0, 1, 3],
    points: 10,
    penalty: 3,
    partialCredit: true,
    language: null,
    starterCode: null,
    samples: [],
  },
  {
    id: crypto.randomUUID(),
    type: "CODING",
    title: "Sum two integers",
    description:
      "Read two signed integers from standard input and print their sum. Inputs are between -1000000 and 1000000.",
    skill: "coding",
    options: [],
    correct: [],
    points: 20,
    penalty: 0,
    partialCredit: false,
    language: "java",
    starterCode:
      "import java.util.*;\npublic class Main {\n  public static void main(String[] args) {\n    // Read input and print the sum\n  }\n}",
    samples: [{ input: "2 3", output: "5" }],
    coding: {
      timeoutMs: 2000,
      memoryMb: 256,
      hiddenTests: [
        { input: "-4 9", output: "5", weight: 1 },
        { input: "0 0", output: "0", weight: 1 },
        { input: "1000000 -1000000", output: "0", weight: 1 },
      ],
    },
  },
];
function Builder({ done }: { done: () => void }) {
  const [title, setTitle] = useState("Java Backend Developer"),
    [duration, setDuration] = useState(60),
    [policy, setPolicy] = useState("CONTROLLED"),
    [questions, setQuestions] = useState(() =>
      JSON.stringify(sampleQuestions(), null, 2),
    ),
    [error, setError] = useState("");
  async function save(e: React.FormEvent) {
    e.preventDefault();
    try {
      await post("/assessments", {
        title,
        instructions:
          "Complete the questions independently. Do not use answer assistance. Answers are final after submission. Controlled pauses are bounded to two interruptions of up to five minutes each; the first 30 seconds of each interruption consume active time.",
        durationMinutes: duration,
        timerPolicy: policy,
        maxPauses: 2,
        maxPauseSeconds: 300,
        totalPauseSeconds: 600,
        availableFrom: new Date().toISOString(),
        latestStart: new Date(Date.now() + 7 * 86400000).toISOString(),
        completionDeadline: new Date(Date.now() + 8 * 86400000).toISOString(),
        cameraRequired: false,
        microphoneRequired: false,
        backNavigation: true,
        passingPercent: 60,
        questions: JSON.parse(questions),
      });
      done();
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <form onSubmit={save} className="card">
      <div className="flex justify-between">
        <h2>Create assessment</h2>
        <button type="button" className="text-button" onClick={done}>
          Close
        </button>
      </div>
      <div className="form-grid">
        <label>
          Assessment name
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
          />
        </label>
        <label>
          Duration (minutes)
          <input
            type="number"
            min="1"
            max="480"
            value={duration}
            onChange={(e) => setDuration(Number(e.target.value))}
          />
        </label>
        <label>
          Time policy
          <select value={policy} onChange={(e) => setPolicy(e.target.value)}>
            <option value="CONTROLLED">Controlled pause & resume</option>
            <option value="STRICT">Strict elapsed time</option>
          </select>
        </label>
      </div>
      <p className="muted">
        Draft opens now; latest start is seven days from creation. Overall
        completion deadline is eight days. Publishing pins these settings and
        questions.
      </p>
      <label>
        Question definitions (validated JSON)
        <textarea
          rows={16}
          className="code-input"
          value={questions}
          onChange={(e) => setQuestions(e.target.value)}
        />
      </label>
      <p className="notice">
        Coding execution requires a configured isolated runner. Infrastructure
        failures keep evaluation pending and never assign an automatic zero.
      </p>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <button>Create draft</button>
    </form>
  );
}
function AiStudio() {
  const [kind, setKind] = useState("SINGLE"),
    [difficulty, setDifficulty] = useState("INTERMEDIATE"),
    [count, setCount] = useState(1),
    [language, setLanguage] = useState("java");
  const cache = useQueryClient(),
    [source, setSource] = useState(
      "Java backend engineer with Spring, Kafka, SQL and coding skills.",
    ),
    [message, setMessage] = useState("");
  const jobs = useQuery({
    queryKey: ["ai-jobs"],
    queryFn: () =>
      api<
        { id: string; purpose: string; state: string; failure_code: string }[]
      >("/ai-jobs"),
    refetchInterval: 4000,
  });
  const artifacts = useQuery({
    queryKey: ["ai-artifacts"],
    queryFn: () =>
      api<
        {
          id: string;
          purpose: string;
          state: string;
          revision: number;
          content: {
            summary: string;
            skills: {
              id: string;
              weight: number;
              evidence: string;
              observation: string;
            }[];
            questions: GeneratedQuestion[];
            limitations: string[];
          };
          provenance: { fake: boolean; model: string };
        }[]
      >("/ai-artifacts"),
    refetchInterval: 4000,
  });
  async function generate(purpose: string, approvedMapId?: string) {
    try {
      await post(
        "/ai-jobs",
        {
          purpose,
          source,
          approvedMapId,
          generation:
            purpose === "QUESTION"
              ? {
                  kind,
                  difficulty,
                  count: ["SINGLE", "MULTIPLE"].includes(kind) ? count : 1,
                  language,
                }
              : null,
        },
        { "Idempotency-Key": crypto.randomUUID() },
      );
      await cache.invalidateQueries({ queryKey: ["ai-jobs"] });
      setMessage("Queued. Results remain drafts until independently reviewed.");
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  async function review(id: string, revision: number, decision: string) {
    const reason = window.prompt(
      "Record the evidence and reason for this review decision:",
    );
    if (!reason) return;
    try {
      await post(
        `/ai-artifact-versions/${id}/review-decisions`,
        { decision, reason },
        { "If-Match": String(revision) },
      );
      await cache.invalidateQueries({ queryKey: ["ai-artifacts"] });
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Human judgment, supported</p>
          <h1>AI authoring studio</h1>
          <p>
            Draft competencies and questions. Review evidence before approval.
          </p>
        </div>
        <Sparkles size={32} />
      </div>
      <div className="card">
        <label>
          Role requirements
          <textarea
            rows={4}
            value={source}
            onChange={(e) => setSource(e.target.value)}
          />
        </label>
        <button onClick={() => void generate("COMPETENCY")}>
          Draft competency map
        </button>
        <p className="muted">
          Use canonical skills: java, spring, kafka, sql, coding, system-design.
          Do not include personal or confidential data.
        </p>
      </div>
      <p role="status">{message}</p>
      <section className="card">
        <h2>Question generation settings</h2>
        <p>
          Approve a competency map, choose your format, then generate drafts
          from that map below. A different authorized reviewer must approve the
          results.
        </p>
        <label>
          Question format
          <select value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="SINGLE">Single choice · one correct option</option>
            <option value="MULTIPLE">
              Multiple choice · selected option set
            </option>
            <option value="PROBLEM_SOLVING">Problem solving · coding</option>
            <option value="SYSTEM_DESIGN">
              System design · implementation task
            </option>
          </select>
        </label>
        <label>
          Estimated difficulty
          <select
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value)}
          >
            {["FOUNDATION", "INTERMEDIATE", "ADVANCED"].map((d) => (
              <option key={d}>{d}</option>
            ))}
          </select>
        </label>
        {["SINGLE", "MULTIPLE"].includes(kind) ? (
          <label>
            Questions per batch
            <select
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
            >
              {[1, 2, 3].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
        ) : (
          <label>
            Programming language
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
            >
              {["java", "python", "javascript"].map((l) => (
                <option key={l}>{l}</option>
              ))}
            </select>
          </label>
        )}
        <p className="muted">
          Coding drafts require isolated reference-solution validation.
          System-design tasks assess a concrete component; they do not replace
          an architecture interview.
        </p>
      </section>
      <AiAssessmentPreparation artifacts={artifacts.data ?? []} />
      <Blueprints artifacts={artifacts.data ?? []} />
      <section className="card">
        <h2>Generation queue</h2>
        {jobs.data?.map((j) => (
          <div className="row" key={j.id}>
            <span>{j.purpose}</span>
            <span className="tag">{j.state}</span>
            {j.failure_code && <span>{j.failure_code}</span>}
            {["QUEUED", "RUNNING", "RETRY_WAIT"].includes(j.state) && (
              <button
                className="text-button"
                onClick={() =>
                  void post(`/ai-jobs/${j.id}/cancel`).catch((e) =>
                    setMessage(errorMessage(e)),
                  )
                }
              >
                Cancel
              </button>
            )}
          </div>
        ))}
        {jobs.data?.length === 0 && <p>No generation jobs yet.</p>}
      </section>
      {artifacts.data?.map((a) => (
        <article className="card" key={a.id}>
          <div className="flex justify-between">
            <h2>{a.purpose}</h2>
            <span className="tag">{a.state}</span>
          </div>
          {a.provenance.fake && (
            <p className="notice">DEVELOPMENT FAKE · Not a model evaluation</p>
          )}
          <p>{a.content.summary}</p>
          {a.content.skills.map((s) => (
            <div className="evidence" key={s.id}>
              <strong>
                {s.id} · {s.weight}%
              </strong>
              <blockquote>“{s.evidence}”</blockquote>
              <p>{s.observation}</p>
            </div>
          ))}
          {a.content.questions.map((q, i) => (
            <GeneratedQuestionView
              key={i}
              question={q}
              artifactId={a.id}
              reviewable={a.state === "REVIEW_REQUIRED"}
            />
          ))}
          <p className="muted">{a.content.limitations.join(" · ")}</p>
          <div className="flex gap-3">
            {a.state === "REVIEW_REQUIRED" && (
              <>
                <button
                  onClick={() => void review(a.id, a.revision, "APPROVED")}
                >
                  Approve with reason
                </button>
                <button
                  className="secondary"
                  onClick={() => void review(a.id, a.revision, "REJECTED")}
                >
                  Reject
                </button>
              </>
            )}
            {a.state === "APPROVED" && a.purpose === "COMPETENCY" && (
              <button onClick={() => void generate("QUESTION", a.id)}>
                Generate question drafts
              </button>
            )}
          </div>
        </article>
      ))}
    </>
  );
}
function Recruiter({ actor, logout }: { actor: Actor; logout: () => void }) {
  const [page, setPage] = useState("Overview"),
    [builder, setBuilder] = useState(false),
    [message, setMessage] = useState("");
  const cache = useQueryClient();
  const stats = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => api<Record<string, number>>("/dashboard"),
  });
  const assessments = useQuery({
    queryKey: ["assessments"],
    queryFn: () =>
      api<
        {
          id: string;
          title: string;
          status: string;
          revision: number;
          created_at: string;
        }[]
      >("/assessments"),
  });
  const invitations = useQuery({
    queryKey: ["invitations"],
    queryFn: () =>
      api<
        {
          id: string;
          email: string;
          expires_at: string;
          revoked: boolean;
          claimed_by: string | null;
        }[]
      >("/invitations"),
  });
  const reports = useQuery({
    queryKey: ["reports"],
    queryFn: () =>
      api<
        {
          attempt_id: string;
          status: string;
          baseline: {
            earned: number;
            maximum: number;
            percentage: number;
            provisional: boolean;
            skills: Record<string, number[]>;
          };
        }[]
      >("/reports"),
  });
  async function refresh() {
    await cache.invalidateQueries();
  }
  async function publish(id: string, revision: number) {
    try {
      await post(`/assessments/${id}/publish`, undefined, {
        "If-Match": String(revision),
      });
      setMessage("Published immutable assessment version.");
      await refresh();
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  async function invite(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    try {
      await post("/invitations", {
        assessmentId: form.get("assessmentId"),
        email: form.get("email"),
        expiresAt: new Date(String(form.get("expiresAt"))).toISOString(),
      });
      setMessage("Invitation queued for email delivery.");
      await refresh();
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  const navigation = [
    ["Overview", LayoutDashboard],
    ["Assessments", ClipboardList],
    ["Jobs", ClipboardList],
    ["Question bank", ClipboardList],
    ["Candidates", Users],
    ["AI studio", Sparkles],
    ["Results", BarChart3],
    ["Settings", Settings],
  ] as const;
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href="#">
          C / <span>criterion</span>
        </a>
        <span className="workspace-label">YOUR WORKSPACE</span>
        <nav aria-label="Main navigation">
          {navigation.map(([label, Icon]) => (
            <button
              key={label}
              className={page === label ? "active" : ""}
              aria-current={page === label ? "page" : undefined}
              onClick={() => {
                setPage(label);
                setBuilder(false);
                setMessage("");
              }}
            >
              <Icon size={19} />
              {label}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="avatar">{actor.email[0].toUpperCase()}</div>
          <div>
            <strong>{actor.role.toLowerCase()}</strong>
            <small>{actor.email}</small>
          </div>
          <button aria-label="Sign out" onClick={logout}>
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span>
            Workspace <span className="muted"> / {page}</span>
          </span>
          <span className="tag">Local development</span>
        </header>
        <main className="dashboard">
          {page === "AI studio" ? (
            <AiStudio />
          ) : (
            <>
              <div className="page-heading">
                <div>
                  <p className="eyebrow">Your talent, in focus</p>
                  <h1>{page === "Overview" ? "Assessment overview" : page}</h1>
                  <p className="muted">
                    {page === "Overview"
                      ? "A clear view of your assessment pipeline."
                      : "Build a fair, evidence-led recruitment process."}
                  </p>
                </div>
                {["Overview", "Assessments"].includes(page) && (
                  <button onClick={() => setBuilder(true)}>
                    <Plus size={17} /> Create assessment
                  </button>
                )}
              </div>
              {message && (
                <p className="notice" role="status">
                  {message}
                </p>
              )}
              {[
                stats.error,
                assessments.error,
                invitations.error,
                reports.error,
              ]
                .filter(Boolean)
                .map((e, i) => (
                  <p className="error" role="alert" key={i}>
                    {errorMessage(e)}
                  </p>
                ))}
              {builder ? (
                <Builder
                  done={() => {
                    setBuilder(false);
                    void refresh();
                  }}
                />
              ) : (
                <>
                  {page === "Overview" && (
                    <>
                      <div className="stats">
                        {[
                          ["assessments", "Assessments"],
                          ["invitations", "Invited candidates"],
                          ["inProgress", "In progress"],
                          ["completed", "Completed"],
                        ].map(([key, label]) => (
                          <article className="stat" key={key}>
                            <span>
                              {label}
                              <ArrowUpRight size={16} />
                            </span>
                            <strong>{stats.data?.[key] ?? "—"}</strong>
                            <small>Across your organization</small>
                          </article>
                        ))}
                      </div>
                      <section className="feature-banner">
                        <div>
                          <span className="eyebrow">
                            Thoughtful assessment design
                          </span>
                          <h2>Start with the skills that matter.</h2>
                          <p>
                            Turn role requirements into a competency draft.
                            <br />
                            Keep the final judgment with your team.
                          </p>
                          <button
                            className="secondary"
                            onClick={() => setPage("AI studio")}
                          >
                            Explore AI authoring <ArrowUpRight size={16} />
                          </button>
                        </div>
                        <div className="orb">
                          <Sparkles size={58} />
                        </div>
                      </section>
                    </>
                  )}
                  {["Overview", "Assessments"].includes(page) && (
                    <section className="card table-card">
                      <div className="section-heading">
                        <h2>Your assessments</h2>
                        <span className="muted">
                          {assessments.data?.length ?? 0} total
                        </span>
                      </div>
                      <table>
                        <thead>
                          <tr>
                            <th>Assessment</th>
                            <th>Status</th>
                            <th>Created</th>
                            <th>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {assessments.data?.map((a) => (
                            <tr key={a.id}>
                              <td>
                                <strong>{a.title}</strong>
                                <small>Technical assessment</small>
                              </td>
                              <td>
                                <span className="tag">{a.status}</span>
                              </td>
                              <td>
                                {new Date(a.created_at).toLocaleDateString()}
                              </td>
                              <td>
                                {a.status === "DRAFT" ? (
                                  <button
                                    className="text-button"
                                    onClick={() =>
                                      void publish(a.id, a.revision)
                                    }
                                  >
                                    Publish pinned version →
                                  </button>
                                ) : (
                                  <span className="muted">Ready to invite</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                      {assessments.data?.length === 0 && (
                        <div className="empty">
                          <ClipboardList />
                          <h3>Your first assessment starts here</h3>
                          <p>
                            Create a draft, review the questions, then publish.
                          </p>
                          <button onClick={() => setBuilder(true)}>
                            Create assessment
                          </button>
                        </div>
                      )}
                    </section>
                  )}
                  {page === "Candidates" && (
                    <>
                      {actor.role !== "EVALUATOR" && (
                        <Retakes
                          invitations={invitations.data ?? []}
                          refreshed={refresh}
                        />
                      )}
                      <form className="card" onSubmit={invite}>
                        <h2>Invite a candidate</h2>
                        <div className="form-grid">
                          <label>
                            Email
                            <input name="email" type="email" required />
                          </label>
                          <label>
                            Assessment
                            <select name="assessmentId" required>
                              <option value="">
                                Select published assessment
                              </option>
                              {assessments.data
                                ?.filter((a) => a.status === "PUBLISHED")
                                .map((a) => (
                                  <option value={a.id} key={a.id}>
                                    {a.title}
                                  </option>
                                ))}
                            </select>
                          </label>
                          <label>
                            Invitation expiry (local time)
                            <input
                              name="expiresAt"
                              type="datetime-local"
                              required
                            />
                          </label>
                        </div>
                        <button>Send invitation</button>
                      </form>
                      <section className="card">
                        <h2>Invitation tracking</h2>
                        {invitations.data?.map((i) => (
                          <div className="row" key={i.id}>
                            <strong>{i.email}</strong>
                            <span>
                              {i.revoked
                                ? "Revoked"
                                : i.claimed_by
                                  ? "Claimed"
                                  : new Date(i.expires_at) < new Date()
                                    ? "Expired"
                                    : "Sent / queued"}
                            </span>
                            <button
                              className="text-button"
                              disabled={i.revoked}
                              onClick={() =>
                                void post(`/invitations/${i.id}/revoke`)
                                  .then(refresh)
                                  .catch((e) => setMessage(errorMessage(e)))
                              }
                            >
                              Revoke
                            </button>
                          </div>
                        ))}
                      </section>
                    </>
                  )}
                  {page === "Results" && <ReportsPanel />}
                  {page === "Jobs" && <JobsPanel />}
                  {page === "Question bank" && (
                    <QuestionBank userId={actor.id} role={actor.role} />
                  )}
                  {page === "Settings" && <Accommodations />}
                  {page === "Settings" && actor.role === "ADMIN" && (
                    <MemberRoles userId={actor.id} />
                  )}
                  {page === "Settings" && actor.role === "ADMIN" && (
                    <section className="card">
                      <h2>Add an independent staff reviewer</h2>
                      <p>
                        Organization administrators can create a reviewer
                        account. The reviewer must verify their email before
                        acting.
                      </p>
                      <form
                        onSubmit={(e) => {
                          e.preventDefault();
                          const data = new FormData(e.currentTarget);
                          void post("/members", Object.fromEntries(data))
                            .then(() =>
                              setMessage(
                                "Reviewer created. Verification email queued.",
                              ),
                            )
                            .catch((e) => setMessage(errorMessage(e)));
                        }}
                      >
                        <label>
                          Email
                          <input name="email" type="email" required />
                        </label>
                        <label>
                          Initial password (share securely)
                          <input
                            name="password"
                            type="password"
                            minLength={12}
                            required
                          />
                        </label>
                        <label>
                          Role
                          <select name="role">
                            <option>EVALUATOR</option>
                            <option>RECRUITER</option>
                          </select>
                        </label>
                        <button>Create staff account</button>
                      </form>
                      <p className="notice">
                        Provider policy, retention administration, and monetary
                        budgets are not yet implemented. Live AI requires
                        deployment configuration and explicit approval.
                      </p>
                    </section>
                  )}
                </>
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}
function Candidate({ actor, logout }: { actor: Actor; logout: () => void }) {
  const [ready, setReady] = useState(false);
  const [current, setCurrent] = useState<Attempt | null>(null),
    [selected, setSelected] = useState<{
      id: string;
      assessment_version_id: string;
      company: string;
      exceptions: string[];
      assessment: Content;
    } | null>(null),
    [message, setMessage] = useState(""),
    [accepted, setAccepted] = useState(false),
    [optionalAi, setOptionalAi] = useState(false),
    [consentLocked, setConsentLocked] = useState(false),
    [checks, setChecks] = useState<Record<string, boolean>>({
      browser: false,
      cookies: false,
      network: false,
      camera: false,
      microphone: false,
    });
  const [tab] = useState(() => {
    const id = sessionStorage.getItem("assessment-tab") ?? crypto.randomUUID();
    sessionStorage.setItem("assessment-tab", id);
    return id;
  });
  const cache = useQueryClient();
  const assignments = useQuery({
    queryKey: ["assignments"],
    queryFn: () =>
      api<
        {
          id: string;
          attempt_id: string | null;
          status: string | null;
          assessment: Content;
          assessment_version_id: string;
          company: string;
          exceptions: string[];
          consent: boolean | null;
        }[]
      >("/candidate/assignments"),
  });
  const invitation = new URLSearchParams(location.hash.slice(1)).get("invite");
  useEffect(() => {
    let active = true;
    setReady(false);
    if (
      !selected ||
      !accepted ||
      Object.keys(checks).length < 9 ||
      !Object.entries(checks).every(
        ([key, passed]) => passed || selected.exceptions?.includes(key),
      )
    )
      return;
    void post(`/candidate/invitations/${selected.id}/acknowledgment`, {
      ...checks,
      instructions: accepted,
      optionalAi,
      assessmentVersionId: selected.assessment_version_id,
      policyVersion: selected.assessment.policyVersion,
    })
      .then(() => {
        if (active) {
          setReady(true);
          setConsentLocked(true);
        }
      })
      .catch((e) => {
        if (active) setMessage(errorMessage(e));
      });
    const expiry = setTimeout(() => {
      setReady(false);
      setMessage("Readiness expired. Run system checks again.");
    }, 600000);
    return () => {
      active = false;
      clearTimeout(expiry);
    };
  }, [selected, accepted, optionalAi, checks]);
  async function claim() {
    try {
      await post("/candidate/invitations/claim", { token: invitation });
      history.replaceState(null, "", location.pathname);
      await cache.invalidateQueries({ queryKey: ["assignments"] });
      setMessage("Invitation claimed.");
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  async function start() {
    if (!selected) return;
    try {
      setCurrent(
        await post<Attempt>(
          `/candidate/invitations/${selected.id}/start`,
          undefined,
          { "X-Tab-ID": tab },
        ),
      );
    } catch (e) {
      setMessage(errorMessage(e));
    }
  }
  if (current)
    return (
      <Suspense
        fallback={<main className="center">Loading assessment workspace…</main>}
      >
        <Workspace
          initial={current}
          tab={tab}
          user={actor.id}
          onExit={() => {
            setCurrent(null);
            setSelected(null);
            void cache.invalidateQueries({ queryKey: ["assignments"] });
          }}
        />
      </Suspense>
    );
  return (
    <>
      <header className="topbar">
        <span className="brand">C / criterion</span>
        <button className="text-button" onClick={logout}>
          Sign out
        </button>
      </header>
      <main className="candidate-page">
        {!selected && <ReleasedResults />}
        <p className="eyebrow">Your assessment space</p>
        <h1>Ready when you are.</h1>
        <p className="muted">
          Read the instructions, check your setup, and take your next step.
        </p>
        {message && (
          <p role="alert" className="notice">
            {message}
          </p>
        )}
        {invitation && (
          <div className="card">
            <h2>You have an invitation</h2>
            <button onClick={() => void claim()}>
              Validate and claim invitation
            </button>
          </div>
        )}
        {selected ? (
          <section className="assessment-onboarding">
            <AssessmentOverview
              assessment={selected.assessment}
              company={selected.company}
            />
            <section className="onboarding-section consent-section">
              <h3>Acknowledge & choose your preferences</h3>
              <label>
                <input
                  type="checkbox"
                  checked={accepted}
                  onChange={(e) => setAccepted(e.target.checked)}
                />{" "}
                I have read and accept these instructions.
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={optionalAi}
                  disabled={consentLocked}
                  onChange={(e) => setOptionalAi(e.target.checked)}
                />{" "}
                I opt in to optional AI feedback.
              </label>
              {consentLocked && (
                <p>
                  This choice is already recorded. Return to your assessment
                  overview to change it under Optional AI preferences; repeating
                  device checks does not change consent.
                </p>
              )}
            </section>
            <section className="onboarding-section readiness-section">
              <h3>Check your setup</h3>
              <Readiness
                key={selected.id}
                assessment={selected.assessment}
                onChange={setChecks}
                exceptions={selected.exceptions}
              />
              <div className="readiness-help">
                <p>
                  Need a different setup? Your recruiter can review an
                  accommodation.
                </p>
                <button
                  className="secondary"
                  onClick={() => {
                    const note = prompt(
                      "Describe the device/access accommodation you need, maximum 2000 characters.",
                    );
                    if (note)
                      void post(
                        `/candidate/invitations/${selected.id}/accommodation-requests`,
                        { note },
                      )
                        .then(() =>
                          setMessage(
                            "Request received. Contact your inviting recruiter for the response timeline.",
                          ),
                        )
                        .catch((e) => setMessage(errorMessage(e)));
                  }}
                >
                  Request a readiness accommodation
                </button>
              </div>
            </section>
            <footer className="onboarding-footer">
              <div>
                <strong>
                  {ready
                    ? "You’re ready to begin"
                    : "A few steps before you begin"}
                </strong>
                <p>
                  {ready
                    ? "Your timer starts only after the server confirms Start."
                    : "Accept the instructions and complete all required checks."}
                </p>
              </div>
              <div className="onboarding-actions">
                <button disabled={!ready} onClick={() => void start()}>
                  Start assessment →
                </button>
                <button
                  className="secondary"
                  onClick={() => {
                    setSelected(null);
                    void assignments.refetch();
                  }}
                >
                  Back to assessments
                </button>
              </div>
            </footer>
          </section>
        ) : (
          assignments.data?.map((a) => (
            <section className="card" key={a.id}>
              <div className="flex justify-between">
                <div>
                  <h2>{a.assessment.title}</h2>
                  <p>
                    {a.assessment.durationMinutes} minutes ·{" "}
                    {a.status ?? "Not started"}
                  </p>
                </div>
                {a.attempt_id ? (
                  <button
                    onClick={() =>
                      void post<Attempt>(
                        `/candidate/attempts/${a.attempt_id}/resume`,
                        undefined,
                        { "X-Tab-ID": tab },
                      )
                        .then(setCurrent)
                        .catch((e) => setMessage(errorMessage(e)))
                    }
                  >
                    {a.status === "IN_PROGRESS"
                      ? "Resume assessment"
                      : "View confirmation"}
                  </button>
                ) : (
                  <button
                    onClick={() => {
                      setSelected(a);
                      setOptionalAi(a.consent ?? false);
                      setConsentLocked(a.consent !== null);
                      setAccepted(false);
                      setChecks({});
                    }}
                  >
                    Read instructions →
                  </button>
                )}
              </div>
            </section>
          ))
        )}
        {assignments.data?.length === 0 && !invitation && (
          <section className="card">
            No claimed assessments. Open your recruiter’s invitation link to
            begin.
          </section>
        )}
      </main>
    </>
  );
}
export default function App() {
  const cache = useQueryClient();
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Actor>("/me"),
    retry: false,
  });
  async function logout() {
    await post("/logout");
    await refreshCsrf();
    cache.clear();
    location.assign("/");
  }
  if (me.isPending)
    return <main className="center">Loading your workspace…</main>;
  if (!me.data)
    return (
      <Authentication
        done={() => void cache.invalidateQueries({ queryKey: ["me"] })}
      />
    );
  if (!me.data.verified)
    return (
      <main className="center card">
        <h1>Email verification required</h1>
        <p>
          Sign out, choose Verify email, and use the token delivered to your
          inbox.
        </p>
        <button onClick={() => void logout()}>Sign out</button>
      </main>
    );
  return me.data.role === "CANDIDATE" ? (
    <Candidate actor={me.data} logout={() => void logout()} />
  ) : (
    <Recruiter actor={me.data} logout={() => void logout()} />
  );
}
