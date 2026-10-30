import { useEffect, useRef, useState } from "react";
import Editor, { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import * as Dialog from "@radix-ui/react-dialog";
import { Answer, Attempt, post, api } from "./api";
import { SaveQueue } from "./saveQueue";
import { questionStatus } from "./progress";
import { useIntegrity } from "./Integrity";
import { QuestionTile } from "./QuestionTile";
import { SubmissionReview } from "./SubmissionReview";
self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
loader.config({ monaco });
const blank = (q: Attempt["assessment"]["questions"][number]): Answer => ({
  selected: [],
  language: q.type === "CODING" ? q.language : null,
  code: q.type === "CODING" ? (q.starterCode ?? "") : null,
  flagged: false,
});
export function Workspace({
  initial,
  tab,
  user,
  onExit,
}: {
  initial: Attempt;
  tab: string;
  user: string;
  onExit: () => void;
}) {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  const [attempt, setAttempt] = useState(initial),
    [status, setStatus] = useState("Saved"),
    [error, setError] = useState(""),
    [index, setIndex] = useState(
      Math.max(
        0,
        initial.assessment.questions.findIndex(
          (q) => q.id === initial.currentQuestion,
        ),
      ),
    ),
    [seconds, setSeconds] = useState(Math.ceil(initial.remainingMs / 1000)),
    [confirm, setConfirm] = useState(false),
    [submitting, setSubmitting] = useState(false),
    [execution, setExecution] = useState<{
      id: string;
      state: string;
      failure_code?: string;
      result?: {
        cases: { status: string; stdout: string }[];
        passedTests: number;
        totalTests: number;
      };
    } | null>(null);
  const anchor = useRef({
    remaining: initial.remainingMs,
    at: performance.now(),
  });
  const observationsLost = useIntegrity(
    initial.id,
    attempt.status === "IN_PROGRESS" && !!initial.monitoringAllowed,
  );
  const queue = useRef<SaveQueue | null>(null);
  const [savedAnswers, setSavedAnswers] = useState<Record<string, Answer>>(() =>
    Object.fromEntries(initial.answers.map((a) => [a.question_id, a.answer])),
  );
  const [visited, setVisited] = useState(initial.visited ?? []);
  if (!queue.current)
    queue.current = new SaveQueue(
      `assessment-pending:${user}:${initial.id}`,
      initial.revision,
      initial.id,
      tab,
      setStatus,
      api,
      (item) => {
        setSavedAnswers((prev) => ({
          ...prev,
          [item.questionId]: item.answer,
        }));
        setVisited((prev) => [
          ...new Set([
            ...prev,
            item.questionId,
            ...(item.nextQuestionId ? [item.nextQuestionId] : []),
          ]),
        ]);
      },
    );
  const [answers, setAnswers] = useState<Record<string, Answer>>(() => {
    const result = Object.fromEntries(
      initial.answers.map((a) => [a.question_id, a.answer]),
    );
    for (const p of queue.current!.snapshots) result[p.questionId] = p.answer;
    return result;
  });
  const q = attempt.assessment.questions[index],
    answer = answers[q.id] ?? blank(q);
  const debounce = useRef<ReturnType<typeof setTimeout> | null>(null),
    draft = useRef<{ id: string; answer: Answer } | null>(null);
  const report = (e: unknown) =>
    setError(e instanceof Error ? e.message : "Request failed");
  async function flushDraft() {
    if (debounce.current) clearTimeout(debounce.current);
    if (draft.current) {
      const value = draft.current;
      draft.current = null;
      await queue.current!.enqueue(value.id, value.answer);
    } else await queue.current!.flush();
  }
  function change(next: Answer) {
    setAnswers((prev) => ({ ...prev, [q.id]: next }));
    setStatus("Saving");
    draft.current = { id: q.id, answer: next };
    if (debounce.current) clearTimeout(debounce.current);
    debounce.current = setTimeout(() => void flushDraft().catch(report), 400);
  }
  useEffect(() => {
    let active = true;
    async function heartbeat() {
      try {
        const next = await post<Attempt>(
          `/candidate/attempts/${initial.id}/heartbeat`,
          undefined,
          { "X-Tab-ID": tab },
        );
        if (active) {
          anchor.current = {
            remaining: next.remainingMs,
            at: performance.now(),
          };
          setAttempt(next);
        }
      } catch (e) {
        if (active) report(e);
      }
    }
    const interval = setInterval(() => void heartbeat(), 10000);
    const tick = setInterval(
      () =>
        setSeconds(
          Math.max(
            0,
            Math.ceil(
              (anchor.current.remaining -
                performance.now() +
                anchor.current.at) /
                1000,
            ),
          ),
        ),
      500,
    );
    const reconnect = () => {
      void post<Attempt>(
        `/candidate/attempts/${initial.id}/resume`,
        undefined,
        { "X-Tab-ID": tab },
      )
        .then((next) => {
          if (active) {
            setAttempt(next);
            anchor.current = {
              remaining: next.remainingMs,
              at: performance.now(),
            };
            return queue.current!.flush();
          }
        })
        .catch(report);
    };
    const unload = (e: BeforeUnloadEvent) => {
      if (queue.current!.pending || draft.current) {
        e.preventDefault();
        e.returnValue = "Unsaved answers";
      }
    };
    window.addEventListener("online", reconnect);
    window.addEventListener("beforeunload", unload);
    void queue.current!.flush().catch(report);
    return () => {
      active = false;
      clearInterval(interval);
      clearInterval(tick);
      window.removeEventListener("online", reconnect);
      window.removeEventListener("beforeunload", unload);
    };
  }, [initial.id, tab]);
  async function navigate(next: number, skip = false) {
    try {
      await flushDraft();
      const updated = {
        ...(answers[q.id] ?? blank(q)),
        skipped: skip || !!answers[q.id]?.skipped,
      };
      const target = attempt.assessment.questions[next]?.id;
      await queue.current!.enqueue(q.id, updated, target ?? q.id);
      setAnswers((prev) => ({ ...prev, [q.id]: updated }));
      if (target) setIndex(next);
      else setConfirm(true);
      setError("");
    } catch (e) {
      report(e);
    }
  }
  async function submit() {
    setSubmitting(true);
    try {
      await flushDraft();
      const result = await post<Attempt>(
        `/candidate/attempts/${attempt.id}/submit`,
        undefined,
        { "X-Tab-ID": tab },
      );
      queue.current!.clear();
      setAttempt(result);
      setConfirm(false);
    } catch (e) {
      report(e);
    } finally {
      setSubmitting(false);
    }
  }
  useEffect(() => {
    if (!execution || ["SUCCEEDED", "FAILED"].includes(execution.state)) return;
    let active = true;
    const id = execution.id;
    const timer = setInterval(
      () =>
        void api<NonNullable<typeof execution>>(`/candidate/executions/${id}`)
          .then((value) => {
            if (active) setExecution(value);
          })
          .catch(report),
      2000,
    );
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [execution?.id, execution?.state]);
  async function runSamples() {
    try {
      await flushDraft();
      setExecution(
        await post(
          `/candidate/attempts/${attempt.id}/run`,
          { questionId: q.id, expectedRevision: queue.current!.revision },
          { "X-Tab-ID": tab, "Idempotency-Key": crypto.randomUUID() },
        ),
      );
    } catch (e) {
      report(e);
    }
  }
  const states = attempt.assessment.questions.map((q) =>
    questionStatus(q, savedAnswers[q.id], visited.includes(q.id)),
  );
  const answered = states.filter((s) => s === "Answered").length,
    skipped = states.filter((s) => s === "Skipped").length;
  const flagged = Object.values(savedAnswers).filter((a) => a.flagged).length;
  if (attempt.status !== "IN_PROGRESS")
    return (
      <main className="center card exam-complete">
        <p className="eyebrow">Assessment complete</p>
        <h1>
          {attempt.status === "EXPIRED"
            ? "Time allowance ended"
            : "Submission received"}
        </h1>
        <p>
          Your acknowledged answers are stored. Evaluation is separate from
          optional AI feedback.
        </p>
        <p>Reference: {attempt.id}</p>
        {queue.current!.pending > 0 && (
          <p role="alert">
            Some local changes were not acknowledged before finalization and are
            not part of your submission.
          </p>
        )}
        <button onClick={onExit}>Back to assignments</button>
      </main>
    );
  return (
    <div className="exam">
      <a className="exam-skip-link" href="#assessment-question">
        Skip to current question
      </a>
      <header className="exam-header">
        <div>
          <span className="brand">C /</span>
          <strong>{attempt.assessment.title}</strong>
          <small>
            {answered} of {attempt.assessment.questions.length} answered
          </small>
        </div>
        <div className="exam-tools">
          <div className="exam-save">
            <span
              className={online ? "connection online" : "connection offline"}
            >
              {online ? "● Connected" : "○ Offline"}
            </span>
            <span role="status" aria-live="polite">
              {status}
            </span>
          </div>
          <div className="timer-block">
            <small>Time remaining</small>
            <strong
              className={seconds < 300 ? "timer warning" : "timer"}
              aria-label="Time remaining"
            >
              {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, "0")}
            </strong>
          </div>
          <button className="secondary" onClick={() => setConfirm(true)}>
            Finish assessment
          </button>
        </div>
      </header>
      {error && (
        <div role="alert" className="error">
          {error}{" "}
          <button
            onClick={() =>
              void flushDraft()
                .then(() => setError(""))
                .catch(report)
            }
          >
            Retry save
          </button>
        </div>
      )}
      <div className="exam-body">
        <aside className="palette">
          <p className="eyebrow">Your progress</p>
          <h3>Question navigator</h3>
          <div className="exam-progress-label">
            <strong>{Math.round((answered / states.length) * 100)}%</strong>
            <span>answered and saved</span>
          </div>
          <progress
            className="exam-progress"
            max={states.length}
            value={answered}
            aria-label="Acknowledged answers"
          />
          <div className="exam-counts">
            <div>
              <strong>{answered}</strong>
              <span>Answered</span>
            </div>
            <div>
              <strong>{skipped}</strong>
              <span>Skipped</span>
            </div>
            <div>
              <strong>{states.length - answered - skipped}</strong>
              <span>Remaining</span>
            </div>
          </div>
          <div className="palette-grid">
            {attempt.assessment.questions.map((item, i) => (
              <QuestionTile
                number={i + 1}
                status={states[i]}
                current={i === index}
                flagged={!!savedAnswers[item.id]?.flagged}
                disabled={!attempt.assessment.backNavigation && i < index}
                key={item.id}
                onClick={() => void navigate(i)}
              />
            ))}
          </div>
          <div className="exam-legend" aria-label="Question status legend">
            <span>
              <i className="legend-dot green" aria-hidden="true" />
              Saved
            </span>
            <span>
              <i className="legend-dot blue" aria-hidden="true" />
              Deferred
            </span>
            <span>
              <i className="legend-dot gray" aria-hidden="true" />
              Unanswered
            </span>
            <span>⚑ Review</span>
          </div>
          <details className="exam-guidance">
            <summary>Timing & assessment guidance</summary>
            <p>
              {attempt.assessment.timerPolicy === "CONTROLLED"
                ? "Bounded pause and resume"
                : "Elapsed time continues offline"}
            </p>
            <p className="muted">
              Green numbered tiles indicate answers acknowledged by the server.
            </p>
            <p>Green ✓: saved answer. Blue: skipped. Gray: unanswered.</p>
            <p>
              Focus and connection observations support human review, never
              automatic scoring.{" "}
              {observationsLost
                ? "Some observations could not be recorded. Test access is unaffected."
                : ""}
            </p>
            <button
              className="secondary"
              onClick={() => {
                const note = prompt(
                  "Explain a device, connection or focus event (maximum 2000 characters).",
                );
                if (note)
                  void post(
                    `/candidate/attempts/${attempt.id}/integrity-explanations`,
                    { note },
                  ).catch(report);
              }}
            >
              Explain an interruption
            </button>
            <p>
              {skipped} skipped · {states.length - answered - skipped} other
              unanswered · {flagged} marked for review (overlapping).
            </p>
          </details>
        </aside>
        <main className="question card" id="assessment-question" tabIndex={-1}>
          <div className="question-toolbar">
            <span className="tag">
              {q.skill} ·{" "}
              {q.type === "SINGLE"
                ? "Single choice"
                : q.type === "MULTIPLE"
                  ? "Multiple choice"
                  : "Coding"}
            </span>
            <label>
              <input
                type="checkbox"
                checked={answer.flagged}
                onChange={(e) =>
                  change({ ...answer, flagged: e.target.checked })
                }
              />{" "}
              Mark for review
            </label>
          </div>
          <p className="eyebrow mt-8">
            Question {index + 1} / {attempt.assessment.questions.length}
          </p>
          <h1>{q.title}</h1>
          <p className="description">{q.description}</p>
          {q.type === "CODING" ? (
            <>
              <label htmlFor="language">Language</label>
              <select id="language" value={answer.language ?? ""} disabled>
                <option>{q.language}</option>
              </select>
              <Editor
                theme="vs-dark"
                height="360px"
                language={answer.language ?? "java"}
                value={answer.code ?? ""}
                onChange={(value) => change({ ...answer, code: value ?? "" })}
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  automaticLayout: true,
                  accessibilitySupport: "on",
                }}
              />
              {q.samples.map((s, i) => (
                <pre key={i}>
                  Input: {s.input}
                  {"\n"}Output: {s.output}
                </pre>
              ))}
              <button
                className="secondary"
                onClick={() => void runSamples()}
                disabled={
                  !!execution &&
                  !["SUCCEEDED", "FAILED"].includes(execution.state)
                }
              >
                Run sample tests
              </button>
              {execution && (
                <section aria-live="polite">
                  <h3>Sample execution: {execution.state}</h3>
                  {execution.failure_code && (
                    <p>
                      Execution infrastructure unavailable. Saved answers and
                      time policy are unaffected.
                    </p>
                  )}
                  {execution.result && (
                    <>
                      <p>
                        {execution.result.passedTests} /{" "}
                        {execution.result.totalTests} samples passed
                      </p>
                      {execution.result.cases.map((result, index) => (
                        <pre key={index}>
                          {result.status}
                          {"\n"}
                          {result.stdout}
                        </pre>
                      ))}
                    </>
                  )}
                </section>
              )}
            </>
          ) : (
            <fieldset>
              <legend className="muted">
                {q.type === "SINGLE"
                  ? "Select one answer"
                  : "Select all applicable answers"}
              </legend>
              {q.options.map((option, i) => (
                <label
                  className={`option ${answer.selected.includes(i) ? "chosen" : ""}`}
                  key={i}
                >
                  <input
                    name={q.id}
                    type={q.type === "SINGLE" ? "radio" : "checkbox"}
                    checked={answer.selected.includes(i)}
                    onChange={() =>
                      change({
                        ...answer,
                        selected:
                          q.type === "SINGLE"
                            ? [i]
                            : answer.selected.includes(i)
                              ? answer.selected.filter((v) => v !== i)
                              : [...answer.selected, i],
                      })
                    }
                  />
                  <span>{option}</span>
                </label>
              ))}
            </fieldset>
          )}
          <footer className="question-actions">
            {!attempt.assessment.backNavigation && (
              <p>Moving forward prevents returning to this question.</p>
            )}
            <button
              className="secondary clear-answer"
              onClick={() => {
                change({ ...blank(q), flagged: answer.flagged });
              }}
            >
              Clear answer
            </button>
            <button
              className="secondary"
              onClick={() => void navigate(index + 1, true)}
            >
              Skip
            </button>
            <button
              className="secondary"
              disabled={index === 0 || !attempt.assessment.backNavigation}
              onClick={() => void navigate(index - 1)}
            >
              Previous
            </button>
            <button onClick={() => void navigate(index + 1)}>
              {index === attempt.assessment.questions.length - 1
                ? "Review answers →"
                : "Save & next →"}
            </button>
          </footer>
        </main>
      </div>
      <Dialog.Root
        open={confirm}
        onOpenChange={(open) => {
          if (!submitting) setConfirm(open);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="overlay exam-overlay" />
          <Dialog.Content className="modal exam-modal submission-review-modal">
            <header className="submission-review-header">
              <p className="eyebrow">
                One final look · {attempt.assessment.questions.length} questions
              </p>
              <Dialog.Title>Review your answers</Dialog.Title>
              <Dialog.Description>
                {answered} answered · {skipped} skipped ·{" "}
                {attempt.assessment.questions.length - answered - skipped} other
                unanswered · {flagged} marked for review. Submission is final.
                Pending saves must be acknowledged first.
              </Dialog.Description>
              <div className="review-summary-strip">
                <div>
                  <strong>{answered}</strong>
                  <span>Answered</span>
                </div>
                <div>
                  <strong>{skipped}</strong>
                  <span>Skipped</span>
                </div>
                <div>
                  <strong>{flagged}</strong>
                  <span>For review</span>
                </div>
                <div>
                  <strong>
                    {Math.floor(seconds / 60)}:
                    {String(seconds % 60).padStart(2, "0")}
                  </strong>
                  <span>Time remaining</span>
                </div>
              </div>
            </header>
            <div
              className="submission-review-scroll"
              tabIndex={0}
              aria-label="All questions and your answers"
            >
              <SubmissionReview
                questions={attempt.assessment.questions}
                answers={answers}
                savedAnswers={savedAnswers}
                visited={visited}
                currentIndex={index}
                backNavigation={attempt.assessment.backNavigation}
                disabled={submitting}
                onEdit={(target) => {
                  setConfirm(false);
                  void navigate(target);
                }}
              />
            </div>
            <footer className="submission-review-footer">
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <p>
                Submission is final. Your timer continues during review. Only
                server-acknowledged answers can be submitted.
              </p>
              <div className="submission-review-actions">
                <Dialog.Close asChild>
                  <button className="secondary" disabled={submitting}>
                    Continue assessment
                  </button>
                </Dialog.Close>
                <button disabled={submitting} onClick={() => void submit()}>
                  {submitting ? "Saving and submitting…" : "Submit assessment"}
                </button>
              </div>
            </footer>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
