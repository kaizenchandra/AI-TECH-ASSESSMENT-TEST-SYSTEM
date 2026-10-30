import { Answer, Question } from "./api";
export function questionStatus(
  q: Question,
  a: Answer | undefined,
  visited: boolean,
) {
  const answered =
    a &&
    (q.type === "CODING"
      ? !!a.code?.trim() && a.code.trim() !== (q.starterCode ?? "").trim()
      : a.selected.length > 0);
  return answered
    ? "Answered"
    : a?.skipped
      ? "Skipped"
      : visited
        ? "Viewed, unanswered"
        : "Not visited";
}
