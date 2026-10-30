import {
  Clock3,
  ListChecks,
  Target,
  CalendarDays,
  ShieldCheck,
  Code2,
  Layers,
  ArrowRight,
} from "lucide-react";
import { Content } from "./api";
const category = (name: string) =>
  ({
    SINGLE: "Single choice",
    MULTIPLE: "Multiple choice",
    CODING: "Coding challenge",
  })[name] ?? name;
export function AssessmentOverview({
  assessment: a,
  company,
}: {
  assessment: Content;
  company: string;
}) {
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const date = (value?: string) =>
    value
      ? new Date(value).toLocaleString(undefined, {
          dateStyle: "medium",
          timeStyle: "short",
        })
      : "Not specified";
  return (
    <>
      <header className="onboarding-hero">
        <div className="onboarding-emblem">
          <Layers size={28} />
        </div>
        <div>
          <p className="eyebrow">{company} · Candidate assessment</p>
          <h2>{a.title}</h2>
          <p className="muted">A little preparation. Your best work.</p>
        </div>
        <span className="onboarding-time-note">
          <Clock3 size={15} />
          Timer starts when you begin
        </span>
      </header>
      <ol className="onboarding-steps" aria-label="Preparation steps">
        <li>
          <span>01</span>Review your assessment
        </li>
        <li>
          <span>02</span>Check your setup
        </li>
        <li>
          <span>03</span>Start when ready <ArrowRight size={15} />
        </li>
      </ol>
      <div className="assessment-facts">
        {[
          {
            icon: Clock3,
            value: `${a.durationMinutes} min`,
            label: "Time allowance",
          },
          {
            icon: ListChecks,
            value: a.questionCount ?? a.questions.length,
            label: "Questions",
          },
          {
            icon: Target,
            value: `${a.passingPercent}%`,
            label: "Passing score",
          },
          {
            icon: Code2,
            value: a.attemptLimit ?? 1,
            label: "Attempt per invitation",
          },
        ].map(({ icon: Icon, value, label }) => (
          <div key={label}>
            <Icon size={20} />
            <strong>{value}</strong>
            <span>{label}</span>
          </div>
        ))}
      </div>
      <div className="onboarding-columns">
        <section className="onboarding-section">
          <div className="section-title">
            <ListChecks size={20} />
            <h3>What to expect</h3>
          </div>
          <h4>Question categories</h4>
          <div className="category-cards">
            {Object.entries(a.categories ?? {}).map(([name, count]) => (
              <div key={name}>
                <span>{category(name)}</span>
                <strong>
                  {count}
                  <small>questions</small>
                </strong>
              </div>
            ))}
          </div>
          <h4>Targeted skills</h4>
          <div className="skill-chips">
            {Object.entries(a.skills ?? {}).map(([name, count]) => (
              <span key={name}>
                {name}
                <b>{count}</b>
              </span>
            ))}
          </div>
          <p className="muted">
            Counts show the questions covering each skill.
          </p>
          <div className="language-note">
            <Code2 size={16} />
            {a.languages?.join(" · ") ||
              "No coding questions in this assessment"}
          </div>
        </section>
        <section className="onboarding-section">
          <div className="section-title">
            <CalendarDays size={20} />
            <h3>Your assessment window</h3>
          </div>
          <dl className="deadline-list">
            <div>
              <dt>Latest start</dt>
              <dd>{date(a.latestStart)}</dd>
            </div>
            <div>
              <dt>Complete by</dt>
              <dd>{date(a.completionDeadline)}</dd>
            </div>
          </dl>
          <p className="muted">Times shown in {timezone}.</p>
          <div className="onboarding-callout">
            <Clock3 size={20} />
            <div>
              <strong>
                {a.timerPolicy === "CONTROLLED"
                  ? "Bounded pause & resume"
                  : "Continuous elapsed-time timer"}
              </strong>
              <p>
                {a.timerPolicy === "CONTROLLED"
                  ? `${a.maxPauses} interruptions · ${a.maxPauseSeconds} seconds each · ${a.totalPauseSeconds} seconds total. The first 30 seconds of heartbeat loss count as active time.`
                  : "Your countdown continues during disconnection."}{" "}
                The overall deadline always applies.
              </p>
            </div>
          </div>
        </section>
      </div>
      <section className="onboarding-section">
        <div className="section-title">
          <Layers size={20} />
          <h3>Instructions & assessment rules</h3>
        </div>
        <p className="instruction-copy">{a.instructions}</p>
        <div className="rule-grid">
          <div>
            <h4>Move through questions</h4>
            <p>
              {a.backNavigation
                ? "Return to skipped questions is allowed."
                : "You cannot return to a question after moving forward."}{" "}
              Blue numbered tiles indicate skipped questions; green means
              server-saved, not correct.
            </p>
          </div>
          <div>
            <h4>Before you finish</h4>
            <p>
              Review unanswered questions and save any pending changes.
              Submission is final. Your work is confirmed only after the server
              acknowledges it.
            </p>
          </div>
        </div>
        <details className="onboarding-details">
          <summary>Scoring and marking rules</summary>
          <ul>
            {a.marking?.map((m, i) => (
              <li key={i}>
                {category(m.category)}: {m.points} points, penalty {m.penalty},{" "}
                {m.partialCredit ? "partial credit" : "exact-match scoring"}.
              </li>
            ))}
          </ul>
        </details>
        {a.job?.title && (
          <details className="onboarding-details" open>
            <summary>Job role: {a.job.title}</summary>
            <p>{a.job.department}</p>
            <p className="muted">
              Job target weights are separate from this assessment’s measured
              question coverage.
            </p>
            <ul>
              {a.job.skills?.map((s) => (
                <li key={s.skill}>
                  {s.skill} · target {s.weight}% ·{" "}
                  {s.required ? "required" : "optional"} · {s.proficiency}
                </li>
              ))}
            </ul>
          </details>
        )}
      </section>
      <section className="onboarding-section privacy-section">
        <div className="section-title">
          <ShieldCheck size={20} />
          <h3>Your privacy & support</h3>
        </div>
        <div className="rule-grid">
          <div>
            <h4>Local device checks</h4>
            <p>
              No media recording or clipboard content is collected. Focus,
              visibility and connection observations may be retained for human
              review for 30 days. They are incomplete, not proof of cheating,
              and do not affect scores.
            </p>
          </div>
          <div>
            <h4>AI feedback is optional</h4>
            <p>
              Optional AI may support feedback after submission and human
              review. It cannot change your time or deterministic score. No
              camera or microphone data is sent to AI. This local configuration
              disables external candidate-data AI processing by default.
            </p>
          </div>
        </div>
        <p className="support-note">
          Contact your inviting recruiter for support, human review, retention
          details or accommodations.
        </p>
      </section>
    </>
  );
}
