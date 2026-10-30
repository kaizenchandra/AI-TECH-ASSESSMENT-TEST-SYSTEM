// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { SubmissionReview } from "./SubmissionReview";
import { Answer, Question } from "./api";
afterEach(cleanup);
const questions: Question[] = Array.from({ length: 20 }, (_, i) => ({
  id: String(i),
  title: `Problem ${i + 1}`,
  description: `Description ${i + 1}`,
  skill: "java",
  type: i === 19 ? "CODING" : i === 1 ? "MULTIPLE" : "SINGLE",
  options: ["First option", "Second option", "Third option"],
  language: i === 19 ? "java" : null,
  starterCode: i === 19 ? "// Start here" : null,
  samples: [],
}));
const choice: Answer = {
  selected: [0],
  code: null,
  language: null,
  flagged: false,
};
const code: Answer = {
  selected: [],
  code: "class Main {\n  // <script> stays text\n}",
  language: "java",
  flagged: true,
};
const answers = {
  "0": choice,
  "1": { ...choice, selected: [0, 2] },
  "19": code,
};
it("shows all twenty questions, multiple chosen options and exact saved code", () => {
  render(
    <SubmissionReview
      questions={questions}
      answers={answers}
      savedAnswers={answers}
      visited={[]}
      currentIndex={19}
      backNavigation
      disabled={false}
      onEdit={() => {}}
    />,
  );
  expect(screen.getAllByRole("article")).toHaveLength(20);
  const multi = screen.getByRole("list", { name: "Options for question 2" });
  expect(within(multi).getAllByText("Selected")).toHaveLength(2);
  expect(screen.getByLabelText("Question 20 code").textContent).toBe(code.code);
  expect(
    screen.getByRole("article", { name: "Problem 20" }).querySelector("script"),
  ).toBeNull();
  expect(
    within(screen.getByRole("article", { name: "Problem 3" })).getByText(
      "No answer saved",
    ),
  ).toBeTruthy();
});
it("labels a pending coding draft honestly and enforces return-navigation restrictions", () => {
  const edit = vi.fn();
  render(
    <SubmissionReview
      questions={questions}
      answers={{ ...answers, "19": { ...code, code: "new local draft" } }}
      savedAnswers={answers}
      visited={[]}
      currentIndex={19}
      backNavigation={false}
      disabled={false}
      onEdit={edit}
    />,
  );
  const last = screen.getByRole("article", { name: "Problem 20" });
  expect(within(last).getByText("Unsaved changes")).toBeTruthy();
  expect(screen.getByLabelText("Question 20 code").textContent).toBe(
    "new local draft",
  );
  expect(
    screen.queryByRole("button", { name: "Edit answer for question 1" }),
  ).toBeNull();
  fireEvent.click(
    screen.getByRole("button", { name: "Edit answer for question 20" }),
  );
  expect(edit).toHaveBeenCalledWith(19);
});
