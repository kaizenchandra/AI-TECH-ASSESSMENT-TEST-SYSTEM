import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";
import type { GeneratedQuestion } from "./GeneratedQuestions";
type Artifact = {
  id: string;
  revision: number;
  purpose: string;
  state: string;
  content: {
    skills: { id: string; weight: number }[];
    questions: GeneratedQuestion[];
  };
};
type Form = {
  id: string;
  state: string;
  revision: number;
  result: {
    feasible: boolean;
    unmet: string[];
    seed: number;
    algorithm: string;
    versions: string[];
  };
  constraints: {
    maxMinutes: number;
    reviewMinutes: number;
    requirements: { skill: string; type: string; count: number }[];
  };
  review_reason?: string;
};
export function Blueprints({ artifacts }: { artifacts: Artifact[] }) {
  const [mapId, setMap] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  const cache = useQueryClient();
  const list = useQuery({
    queryKey: ["blueprints"],
    queryFn: () => api<Form[]>("/blueprints"),
    refetchInterval: 5000,
  });
  const maps = artifacts.filter(
      (a) => a.purpose === "COMPETENCY" && a.state === "APPROVED",
    ),
    map = maps.find((a) => a.id === mapId);
  const pool = artifacts
    .filter((a) => a.purpose === "QUESTION" && a.state === "APPROVED")
    .flatMap((a) =>
      a.content.questions.map((q, i) => ({
        artifactId: a.id,
        revision: a.revision,
        questionIndex: i,
        q,
      })),
    );
  async function action(work: () => Promise<unknown>) {
    setBusy(true);
    try {
      await work();
      await cache.invalidateQueries({ queryKey: ["blueprints"] });
      await cache.invalidateQueries({ queryKey: ["assessments"] });
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h2>Constrained assessment blueprints</h2>
      <p>
        Choose approved competencies, exact skill/type counts and a time budget.
        All selected items carry equal points; item coverage must match the
        approved skill weights. Solving times are reviewer estimates. Selection
        never silently relaxes a constraint.
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          if (!map) return;
          void action(async () => {
            const requirements = map.content.skills
              .flatMap((s) =>
                ["SINGLE", "MULTIPLE", "CODING"].map((type) => ({
                  skill: s.id,
                  type,
                  count: Number(data.get(`${s.id}:${type}`)),
                })),
              )
              .filter((r) => r.count > 0);
            const chosen = pool.filter((_, i) => data.get(`include-${i}`));
            await post("/blueprints", {
              competencyId: map.id,
              competencyRevision: map.revision,
              requirements,
              pool: chosen.map((p) => ({
                ...p,
                q: undefined,
                minutes: Number(data.get(`minutes-${pool.indexOf(p)}`)),
              })),
              maxMinutes: Number(data.get("duration")),
              reviewMinutes: Number(data.get("review")),
              seed: Number(data.get("seed")),
              language: data.get("language"),
            });
            setMessage(
              "Blueprint evaluated. Review unmet constraints or request independent approval below.",
            );
          });
        }}
      >
        <label>
          Approved competency map
          <select
            required
            value={mapId}
            onChange={(e) => setMap(e.target.value)}
          >
            <option value="">Choose map</option>
            {maps.map((m) => (
              <option key={m.id} value={m.id}>
                {m.content.skills.map((s) => `${s.id} ${s.weight}%`).join(", ")}{" "}
                · {m.id.slice(0, 8)}
              </option>
            ))}
          </select>
        </label>
        {map && (
          <fieldset>
            <legend>Required question counts</legend>
            {map.content.skills.map((s) => (
              <div className="evidence" key={s.id}>
                <strong>
                  {s.id} · {s.weight}% target
                </strong>
                {["SINGLE", "MULTIPLE", "CODING"].map((type) => (
                  <label key={type}>
                    {s.id} {type} count
                    <input
                      name={`${s.id}:${type}`}
                      type="number"
                      min={0}
                      max={100}
                      defaultValue={0}
                    />
                  </label>
                ))}
              </div>
            ))}
          </fieldset>
        )}
        <fieldset>
          <legend>Eligible approved pool · exclude items by unchecking</legend>
          {pool.length === 0 && <p>No approved questions available.</p>}
          {pool.map((p, i) => (
            <div
              className="evidence"
              key={`${p.artifactId}:${p.questionIndex}`}
            >
              <label>
                <input name={`include-${i}`} type="checkbox" defaultChecked />
                {p.q.title} · {p.q.skill} · {p.q.type}
              </label>
              <label>
                Estimated solving minutes for {p.q.title}
                <input
                  type="number"
                  min={1}
                  max={240}
                  defaultValue={p.q.type === "CODING" ? 20 : 3}
                  name={`minutes-${i}`}
                  required
                />
              </label>
            </div>
          ))}
        </fieldset>
        <label>
          Total assessment minutes
          <input
            name="duration"
            type="number"
            min={1}
            max={480}
            defaultValue={60}
            required
          />
        </label>
        <label>
          Review allowance minutes
          <input
            name="review"
            type="number"
            min={0}
            max={479}
            defaultValue={5}
            required
          />
        </label>
        <label>
          Selection seed
          <input name="seed" type="number" defaultValue={1} required />
        </label>
        <label>
          Coding language constraint
          <select name="language">
            <option>java</option>
            <option>python</option>
            <option>javascript</option>
          </select>
        </label>
        <button disabled={busy || !map || pool.length === 0}>
          Validate and propose blueprint
        </button>
      </form>
      {list.isError && (
        <p role="alert">
          Unable to load blueprints.{" "}
          <button onClick={() => void list.refetch()}>Retry</button>
        </p>
      )}
      {list.data?.map((b) => (
        <article className="evidence" key={b.id}>
          <h3>
            Blueprint {b.id.slice(0, 8)} · {b.state}
          </h3>
          <p>
            {b.result.versions.length} selected · {b.constraints.maxMinutes}{" "}
            minutes including {b.constraints.reviewMinutes} review · seed{" "}
            {b.result.seed} · {b.result.algorithm}
          </p>
          <ul>
            {b.constraints.requirements.map((r) => (
              <li key={`${r.skill}:${r.type}`}>
                {r.skill} / {r.type}: requested {r.count}
              </li>
            ))}
          </ul>
          {!b.result.feasible && (
            <>
              <h4>Unmet constraints</h4>
              <ul>
                {b.result.unmet.map((s, i) => (
                  <li key={i}>{s}</li>
                ))}
              </ul>
              <p>
                Generate/review more questions or explicitly propose revised
                constraints.
              </p>
            </>
          )}
          {b.review_reason && <p>Review: {b.review_reason}</p>}
          {b.state === "REVIEW_REQUIRED" && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const data = new FormData(e.currentTarget);
                void action(async () => {
                  await post(
                    `/blueprint-versions/${b.id}/review-decisions`,
                    {
                      decision: data.get("decision"),
                      reason: data.get("reason"),
                    },
                    { "If-Match": String(b.revision) },
                  );
                  setMessage("Review recorded.");
                });
              }}
            >
              <label>
                Review decision
                <select name="decision">
                  <option>APPROVED</option>
                  <option>REJECTED</option>
                </select>
              </label>
              <label>
                Review reason
                <textarea name="reason" maxLength={4000} required />
              </label>
              <button disabled={busy}>Record independent review</button>
            </form>
          )}
          {b.state === "APPROVED" && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const data = new FormData(e.currentTarget);
                void action(async () => {
                  await post(
                    `/blueprints/${b.id}/assessments`,
                    {
                      title: data.get("title"),
                      durationMinutes: b.constraints.maxMinutes,
                      passingPercent: Number(data.get("passing")),
                      latestStart: new Date(
                        String(data.get("latest")),
                      ).toISOString(),
                      completionDeadline: new Date(
                        String(data.get("deadline")),
                      ).toISOString(),
                      items: [],
                    },
                    { "If-Match": String(b.revision) },
                  );
                  setMessage(
                    "Assessment draft created from pinned blueprint. Preview and publish in Assessments.",
                  );
                });
              }}
            >
              <label>
                Blueprint assessment title
                <input name="title" required maxLength={200} />
              </label>
              <label>
                Passing percentage
                <input
                  name="passing"
                  type="number"
                  min={0}
                  max={100}
                  defaultValue={60}
                />
              </label>
              <label>
                Latest start (local time)
                <input name="latest" type="datetime-local" required />
              </label>
              <label>
                Completion deadline (local time)
                <input name="deadline" type="datetime-local" required />
              </label>
              <button disabled={busy}>Prepare blueprint assessment</button>
            </form>
          )}
        </article>
      ))}
      <p role="status">{message}</p>
      <p className="muted">
        This form validates counts, skill weights, estimated time, language and
        selected-pool exclusions. It does not establish calibrated difficulty,
        item-exposure limits or statistical form equivalence.
      </p>
    </section>
  );
}
