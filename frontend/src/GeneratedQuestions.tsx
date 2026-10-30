import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";

export type GeneratedQuestion = {
  type: string;
  title: string;
  skill: string;
  description: string;
  rationale: string;
  options: string[];
  correct: number[];
  coding: null | {
    language: string;
    starterCode: string;
    referenceSolution: string;
    samples: { input: string; output: string }[];
    hiddenTests: { input: string; output: string; weight: number }[];
    timeoutMs: number;
    memoryMb: number;
  };
};
type Artifact = {
  id: string;
  revision: number;
  state: string;
  purpose: string;
  content: { questions: GeneratedQuestion[] };
};
const error = (e: unknown) =>
  e instanceof Error ? e.message : "Request failed";
export function GeneratedQuestionView({
  question: q,
  artifactId,
  reviewable,
}: {
  question: GeneratedQuestion;
  artifactId: string;
  reviewable: boolean;
}) {
  const [message, setMessage] = useState("");
  const cache = useQueryClient();
  const validation = useQuery({
    queryKey: ["coding-validation", artifactId],
    queryFn: () =>
      api<{ state: string; finding?: string }>(
        `/ai-artifacts/${artifactId}/coding-validation`,
      ),
    enabled: q.type === "CODING",
    refetchInterval: 5000,
  });
  return (
    <section className="evidence">
      <h3>{q.title}</h3>
      <p className="muted">
        {q.type} · {q.skill}
      </p>
      <p style={{ whiteSpace: "pre-wrap" }}>{q.description}</p>
      {q.options.length > 0 && (
        <ol>
          {q.options.map((o, i) => (
            <li key={i}>
              {o}
              {q.correct.includes(i) && <strong> · Correct option</strong>}
            </li>
          ))}
        </ol>
      )}
      <p>
        <strong>Reviewer rationale:</strong> {q.rationale}
      </p>
      {q.coding && (
        <>
          <p>
            {q.coding.language} · {q.coding.timeoutMs} ms · {q.coding.memoryMb}{" "}
            MiB
          </p>
          <details>
            <summary>
              Review solution, starter and test evidence (confidential)
            </summary>
            <h4>Starter code</h4>
            <pre>{q.coding.starterCode || "Empty editor"}</pre>
            <h4>Reference solution</h4>
            <pre>{q.coding.referenceSolution}</pre>
            <h4>Public samples</h4>
            <pre>{JSON.stringify(q.coding.samples, null, 2)}</pre>
            <h4>Hidden tests</h4>
            <pre>{JSON.stringify(q.coding.hiddenTests, null, 2)}</pre>
          </details>
          <p>Isolated validation: {validation.data?.state ?? "Loading"}</p>
          <p>{validation.data?.finding}</p>
          {reviewable && (
            <button
              className="secondary"
              onClick={async () => {
                try {
                  await post(`/ai-artifacts/${artifactId}/coding-validation`);
                  await cache.invalidateQueries({
                    queryKey: ["coding-validation", artifactId],
                  });
                  setMessage(
                    "Validation requested. Approval remains separate.",
                  );
                } catch (e) {
                  setMessage(error(e));
                }
              }}
            >
              Validate reference solution
            </button>
          )}
          <p role="status">{message}</p>
        </>
      )}
    </section>
  );
}
export function AiAssessmentPreparation({
  artifacts,
}: {
  artifacts: Artifact[];
}) {
  const [selected, setSelected] = useState<string[]>([]),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  const cache = useQueryClient();
  const choices = artifacts
    .filter((a) => a.purpose === "QUESTION" && a.state === "APPROVED")
    .flatMap((a) =>
      a.content.questions.map((q, index) => ({
        key: `${a.id}:${a.revision}:${index}`,
        q,
        artifactId: a.id,
        revision: a.revision,
        questionIndex: index,
      })),
    );
  return (
    <section className="card">
      <h2>Prepare assessment from approved questions</h2>
      <p>
        Select reviewed items, set the test window, and create a draft. Preview
        and publish it separately from Assessments.
      </p>
      {choices.length === 0 ? (
        <p>
          No approved questions yet. Generate and independently review a batch
          first.
        </p>
      ) : (
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            if (busy) return;
            setBusy(true);
            const data = new FormData(e.currentTarget);
            try {
              const result = await post<{ id: string }>(
                "/assessments/from-ai",
                {
                  title: data.get("title"),
                  durationMinutes: Number(data.get("duration")),
                  passingPercent: Number(data.get("passing")),
                  latestStart: new Date(
                    String(data.get("latest")),
                  ).toISOString(),
                  completionDeadline: new Date(
                    String(data.get("deadline")),
                  ).toISOString(),
                  items: choices
                    .filter((c) => selected.includes(c.key))
                    .map(({ artifactId, revision, questionIndex }) => ({
                      artifactId,
                      revision,
                      questionIndex,
                    })),
                },
              );
              await cache.invalidateQueries({ queryKey: ["assessments"] });
              setSelected([]);
              setMessage(
                `Draft created (${result.id}). Open Assessments to preview and publish.`,
              );
            } catch (err) {
              setMessage(error(err));
            } finally {
              setBusy(false);
            }
          }}
        >
          <fieldset>
            <legend>Approved question versions</legend>
            {choices.map((c) => (
              <label className="row" key={c.key}>
                <input
                  type="checkbox"
                  checked={selected.includes(c.key)}
                  onChange={(e) =>
                    setSelected((old) =>
                      e.target.checked
                        ? [...old, c.key]
                        : old.filter((k) => k !== c.key),
                    )
                  }
                />
                <span>
                  {c.q.title} · {c.q.type} · {c.q.skill}
                </span>
              </label>
            ))}
          </fieldset>
          <label>
            Assessment title
            <input
              name="title"
              required
              maxLength={200}
              placeholder="Backend engineering assessment"
            />
          </label>
          <label>
            Duration (minutes)
            <input
              name="duration"
              type="number"
              min={1}
              max={240}
              defaultValue={60}
              required
            />
          </label>
          <label>
            Passing percentage
            <input
              name="passing"
              type="number"
              min={0}
              max={100}
              defaultValue={60}
              required
            />
          </label>
          <label>
            Latest start (your local timezone)
            <input name="latest" type="datetime-local" required />
          </label>
          <label>
            Overall completion deadline (your local timezone)
            <input name="deadline" type="datetime-local" required />
          </label>
          <p className="muted">
            Each question is worth 10 points. Exact-match choice scoring, no
            negative marks. Controlled pause policy: two pauses, up to five
            minutes each. Back navigation enabled.
          </p>
          <button disabled={busy || selected.length === 0}>
            {busy
              ? "Preparing…"
              : `Create assessment draft (${selected.length} selected)`}
          </button>
        </form>
      )}
      <p role="status">{message}</p>
    </section>
  );
}
