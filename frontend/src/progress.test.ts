import { describe, it, expect } from "vitest";
import { questionStatus } from "./progress";
import { Question, Answer } from "./api";
const q: Question = {
  id: "1",
  type: "SINGLE",
  title: "Choice",
  description: "",
  skill: "java",
  options: ["A", "B"],
  language: null,
  starterCode: null,
  samples: [],
};
const empty: Answer = {
  selected: [],
  language: null,
  code: null,
  flagged: false,
};
describe("acknowledged question states", () => {
  it("distinguishes new, viewed, skipped, saved and cleared", () => {
    expect(questionStatus(q, undefined, false)).toBe("Not visited");
    expect(questionStatus(q, undefined, true)).toBe("Viewed, unanswered");
    expect(questionStatus(q, { ...empty, skipped: true }, true)).toBe(
      "Skipped",
    );
    expect(
      questionStatus(q, { ...empty, selected: [1], skipped: true }, true),
    ).toBe("Answered");
    expect(questionStatus(q, empty, true)).toBe("Viewed, unanswered");
  });
  it("never counts untouched starter code as completed", () => {
    const code = {
      ...q,
      type: "CODING" as const,
      starterCode: "class Main {}",
    };
    expect(
      questionStatus(code, { ...empty, code: "class Main {}" }, true),
    ).toBe("Viewed, unanswered");
    expect(
      questionStatus(code, { ...empty, code: "class Main {int a;}" }, true),
    ).toBe("Answered");
  });
});
