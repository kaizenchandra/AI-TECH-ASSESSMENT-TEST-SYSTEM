import {
  Check,
  Code2,
  Flag,
  CircleAlert,
  Pencil,
  ArrowUpRight,
} from "lucide-react";
import { Answer, Question } from "./api";
import { questionStatus } from "./progress";

function normalized(question: Question, answer?: Answer) {
  return {
    selected: answer?.selected ?? [],
    code:
      answer?.code ??
      (question.type === "CODING" ? (question.starterCode ?? "") : null),
    language: answer?.language ?? question.language,
    flagged: answer?.flagged ?? false,
    skipped: answer?.skipped ?? false,
  };
}
export function SubmissionReview({
  questions,
  answers,
  savedAnswers,
  visited,
  currentIndex,
  backNavigation,
  disabled,
  onEdit,
}: {
  questions: Question[];
  answers: Record<string, Answer>;
  savedAnswers: Record<string, Answer>;
  visited: string[];
  currentIndex: number;
  backNavigation: boolean;
  disabled: boolean;
  onEdit: (index: number) => void;
}) {
  return (
    <div className="submission-review-list">
      {questions.map((question, index) => {
        const answer = normalized(
          question,
          answers[question.id] ?? savedAnswers[question.id],
        );
        const pending =
          JSON.stringify(answer) !==
          JSON.stringify(normalized(question, savedAnswers[question.id]));
        const state = questionStatus(
          question,
          savedAnswers[question.id],
          visited.includes(question.id),
        );
        const saved = state === "Answered";
        const label = pending
          ? "Unsaved changes"
          : saved
            ? "Saved answer"
            : state === "Skipped"
              ? "Skipped · no answer"
              : "No answer saved";
        return (
          <article
            key={question.id}
            className={`review-question ${pending ? "pending" : saved ? "answered" : "unanswered"}`}
            aria-labelledby={`review-title-${question.id}`}
          >
            <header className="review-question-header">
              <span
                className="review-question-number"
                aria-label={`Question ${index + 1}`}
              >
                {index + 1}
              </span>
              <div>
                <span className="review-question-meta">
                  {question.skill} ·{" "}
                  {question.type === "CODING"
                    ? "Coding"
                    : question.type === "MULTIPLE"
                      ? "Multiple choice"
                      : "Single choice"}
                </span>
                <h3 id={`review-title-${question.id}`}>{question.title}</h3>
              </div>
              <span className="review-answer-state">
                {pending ? (
                  <CircleAlert size={14} />
                ) : saved ? (
                  <Check size={14} />
                ) : (
                  <ArrowUpRight size={14} />
                )}{" "}
                {label}
              </span>
            </header>
            <p className="review-question-description">
              {question.description}
            </p>
            {pending && (
              <p className="review-pending-note">
                Shown below is your latest local draft. These changes must be
                saved before submission.
              </p>
            )}
            {question.type === "CODING" ? (
              <section
                className="review-code"
                aria-label={`Code answer for question ${index + 1}`}
              >
                <div className="review-code-heading">
                  <span>
                    <Code2 size={16} />
                    Your code
                  </span>
                  <span>{answer.language || "Language not selected"}</span>
                </div>
                {answer.code?.trim() ? (
                  <pre tabIndex={0} aria-label={`Question ${index + 1} code`}>
                    <code>{answer.code}</code>
                  </pre>
                ) : (
                  <p className="review-empty">No code entered.</p>
                )}
                {answer.code?.trim() &&
                  answer.code.trim() ===
                    (question.starterCode ?? "").trim() && (
                    <p className="review-empty">
                      Starter template unchanged. This does not count as an
                      answered question.
                    </p>
                  )}
              </section>
            ) : (
              <ul
                className="review-options"
                aria-label={`Options for question ${index + 1}`}
              >
                {question.options.map((option, optionIndex) => {
                  const selected = answer.selected.includes(optionIndex);
                  return (
                    <li
                      key={optionIndex}
                      className={selected ? "is-selected" : ""}
                    >
                      <span className="review-option-letter">
                        {String.fromCharCode(65 + optionIndex)}
                      </span>
                      <span>{option}</span>
                      {selected && (
                        <span className="review-option-selected">
                          <Check size={14} aria-hidden="true" />
                          Selected
                        </span>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
            <footer className="review-question-footer">
              <span>
                {answer.flagged && (
                  <>
                    <Flag size={14} />
                    Marked for review
                  </>
                )}
              </span>
              {backNavigation || index >= currentIndex ? (
                <button
                  type="button"
                  className="secondary"
                  disabled={disabled}
                  onClick={() => onEdit(index)}
                >
                  <Pencil size={14} />
                  Edit answer{" "}
                  <span className="sr-only">for question {index + 1}</span>
                </button>
              ) : (
                <small>
                  Editing earlier questions is disabled for this assessment.
                </small>
              )}
            </footer>
          </article>
        );
      })}
    </div>
  );
}
