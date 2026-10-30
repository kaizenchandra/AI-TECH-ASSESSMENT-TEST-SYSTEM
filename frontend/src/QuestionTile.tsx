import { Check, Flag, CornerUpRight } from "lucide-react";

/** Visual status is derived only from acknowledged progress, never pending edits. */
export function QuestionTile({
  number,
  status,
  current,
  flagged,
  disabled,
  onClick,
}: {
  number: number;
  status: string;
  current: boolean;
  flagged: boolean;
  disabled: boolean;
  onClick: () => void;
}) {
  const state =
    status === "Answered"
      ? "answered"
      : status === "Skipped"
        ? "skipped"
        : status === "Not visited"
          ? "unvisited"
          : "visited";
  return (
    <button
      type="button"
      className={`question-tile ${state}${current ? " selected" : ""}`}
      aria-current={current ? "step" : undefined}
      aria-label={`Question ${number}, ${status}${flagged ? ", marked for review" : ""}`}
      disabled={disabled}
      onClick={onClick}
    >
      <span className="question-number">{number}</span>
      {state === "answered" && (
        <Check className="tile-state-icon" size={12} aria-hidden="true" />
      )}
      {state === "skipped" && (
        <CornerUpRight
          className="tile-state-icon"
          size={12}
          aria-hidden="true"
        />
      )}
      {flagged && (
        <Flag className="tile-review-icon" size={11} aria-hidden="true" />
      )}
    </button>
  );
}
