import { useState } from "react";
import { IntegrityPanel } from "./Integrity";
import { HumanReviewQueue } from "./HumanReviews";
import { PurposePreferences } from "./PurposePreferences";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";
type Finding = {
  questionId: string;
  firstLine: number;
  lastLine: number;
  excerpt: string;
  criterion: string;
  observation: string;
};
type Artifact = {
  id: string;
  state: string;
  revision: number;
  content: { summary: string; findings: Finding[]; limitations: string[] };
  provenance: { fake: boolean };
};
type Evaluation = {
  attempt_id: string;
  revision: number;
  status: string;
  release_state: string;
  baseline: { percentage: number; earned: number; maximum: number };
  feedbackArtifacts: Artifact[];
  versions: { version: number; feedback: string }[];
};
export function ReportsPanel() {
  const [selected, setSelected] = useState(""),
    [message, setMessage] = useState(""),
    [feedback, setFeedback] = useState(""),
    [recommendation, setRecommendation] = useState("UNDECIDED");
  const cache = useQueryClient();
  const list = useQuery({
    queryKey: ["reports"],
    queryFn: () => api<Evaluation[]>("/reports"),
    refetchInterval: 5000,
  });
  const detail = useQuery({
    queryKey: ["evaluation", selected],
    enabled: !!selected,
    queryFn: () => api<Evaluation>("/evaluations/" + selected),
    refetchInterval: 4000,
  });
  async function action(work: () => Promise<unknown>) {
    try {
      await work();
      setMessage("Saved.");
      await cache.invalidateQueries({ queryKey: ["evaluation", selected] });
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Request failed");
    }
  }
  async function reviewArtifact(a: Artifact, decision: string) {
    const reason = window.prompt("Evidence and reason for this decision");
    if (reason)
      await action(() =>
        post(
          `/ai-artifact-versions/${a.id}/review-decisions`,
          { decision, reason },
          { "If-Match": String(a.revision) },
        ),
      );
  }
  return (
    <>
      <HumanReviewQueue />
      <section className="card">
        <div className="flex justify-between">
          <h2>Submissions and report release</h2>
          <a href="/api/v1/reports/export" download="assessment-results.csv">
            Export CSV
          </a>
        </div>
        {list.data?.length === 0 && <p>No submissions yet.</p>}
        {list.data?.map((e) => (
          <button
            key={e.attempt_id}
            className="secondary mr-3 mb-3"
            onClick={() => setSelected(e.attempt_id)}
          >
            {e.baseline.percentage.toFixed(1)}% · {e.status} ·{" "}
            {e.attempt_id.slice(0, 8)}
          </button>
        ))}
      </section>
      <p role="status">{message}</p>
      {detail.data && (
        <section className="card">
          <h2>Evaluation {selected.slice(0, 8)}</h2>
          <IntegrityPanel id={selected} />
          <p>
            Deterministic baseline: {detail.data.baseline.earned}/
            {detail.data.baseline.maximum} · {detail.data.status}
          </p>
          <p>
            Human review / release: {detail.data.release_state} · Revision{" "}
            {detail.data.revision}
          </p>
          <button
            className="secondary"
            onClick={() =>
              void action(() =>
                post(`/evaluations/${selected}/insight-jobs`, undefined, {
                  "Idempotency-Key": crypto.randomUUID(),
                }),
              )
            }
          >
            Request optional AI feedback
          </button>
          <p className="muted">
            Requires candidate opt-in and completed deterministic grading.
            Manual review remains available if AI is declined or unavailable.
          </p>
          {detail.data.feedbackArtifacts.map((a) => (
            <article className="evidence" key={a.id}>
              <h3>
                {a.state} {a.provenance.fake ? "· DEVELOPMENT FAKE" : ""}
              </h3>
              <p>{a.content.summary}</p>
              {a.content.findings.map((f, i) => (
                <div key={i}>
                  <strong>
                    {f.criterion} · Lines {f.firstLine}–{f.lastLine}
                  </strong>
                  <pre>{f.excerpt}</pre>
                  <p>{f.observation}</p>
                </div>
              ))}
              <p>{a.content.limitations.join(" · ")}</p>
              {a.state === "REVIEW_REQUIRED" && (
                <div className="flex gap-3">
                  <button onClick={() => void reviewArtifact(a, "APPROVED")}>
                    Approve feedback
                  </button>
                  <button
                    className="secondary"
                    onClick={() => void reviewArtifact(a, "REJECTED")}
                  >
                    Reject
                  </button>
                </div>
              )}
            </article>
          ))}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void action(() =>
                post(
                  `/evaluations/${selected}/reviews`,
                  { feedback, recommendation, adjustments: [] },
                  { "If-Match": String(detail.data!.revision) },
                ),
              );
            }}
          >
            <label>
              Human-reviewed candidate feedback
              <textarea
                value={feedback}
                onChange={(e) => setFeedback(e.target.value)}
                required
                maxLength={6000}
              />
            </label>
            <label>
              Human recommendation (recruiter-only)
              <select
                value={recommendation}
                onChange={(e) => setRecommendation(e.target.value)}
              >
                <option>UNDECIDED</option>
                <option>INTERVIEW</option>
                <option>SHORTLIST</option>
                <option>REJECT</option>
              </select>
            </label>
            <button disabled={detail.data.status !== "EVALUATED"}>
              Approve evaluation with feedback
            </button>
          </form>
          <button
            className="secondary mt-5"
            disabled={detail.data.release_state !== "APPROVED"}
            onClick={() =>
              void action(() =>
                post(`/reports/${selected}/release`, undefined, {
                  "If-Match": String(detail.data!.revision),
                  "Idempotency-Key": crypto.randomUUID(),
                }),
              )
            }
          >
            Release approved report to candidate
          </button>
          <h3>Review history</h3>
          {detail.data.versions.map((v) => (
            <p key={v.version}>
              Version {v.version}: {v.feedback}
            </p>
          ))}
        </section>
      )}
    </>
  );
}
type Released = {
  attemptId: string;
  evaluationVersion: number;
  percentage: number;
  feedback: string;
  skills: {
    skill: string;
    status: string;
    earned?: number;
    maximum?: number;
    limitation: string;
  }[];
  aiFeedback: {
    summary: string;
    developmentFake: boolean;
    findings: Finding[];
    limitations: string[];
  }[];
};
export function ReleasedResults() {
  const results = useQuery({
    queryKey: ["released-results"],
    queryFn: () => api<Released[]>("/candidate/results"),
  });
  const [message, setMessage] = useState("");
  return (
    <>
      <HumanReviewQueue candidate />
      <PurposePreferences />
      {results.data?.map((r) => (
        <section className="card" key={r.attemptId}>
          <h2>Released result · {r.percentage.toFixed(1)}%</h2>
          <p>{r.feedback}</p>
          <table>
            <thead>
              <tr>
                <th>Skill</th>
                <th>Evidence</th>
              </tr>
            </thead>
            <tbody>
              {r.skills.map((s) => (
                <tr key={s.skill}>
                  <td>{s.skill}</td>
                  <td>
                    {s.status === "NOT_ASSESSED"
                      ? "Not assessed — insufficient evidence"
                      : `${s.earned} / ${s.maximum}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {r.aiFeedback.map((a, i) => (
            <article key={i}>
              <h3>
                Human-approved AI feedback{" "}
                {a.developmentFake ? "· DEVELOPMENT FAKE" : ""}
              </h3>
              <p>{a.summary}</p>
              {a.findings.map((f, j) => (
                <div key={j}>
                  <pre>
                    Lines {f.firstLine}–{f.lastLine}
                    {"\n"}
                    {f.excerpt}
                  </pre>
                  <p>{f.observation}</p>
                </div>
              ))}
              <p>{a.limitations.join(" · ")}</p>
            </article>
          ))}
          <button
            className="secondary"
            onClick={() => {
              const reason = window.prompt(
                "Describe the correction or human review you need",
              );
              if (reason)
                void post(
                  `/candidate/attempts/${r.attemptId}/review-requests`,
                  { reason },
                )
                  .then(() =>
                    setMessage(
                      "Review request received. Contact your recruiter for the response timeline.",
                    ),
                  )
                  .catch((e) => setMessage(e.message));
            }}
          >
            Request correction / human review
          </button>
        </section>
      ))}
      <p role="status">{message}</p>
    </>
  );
}
