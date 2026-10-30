// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QuestionTile } from "./QuestionTile";
afterEach(cleanup);
it.each(["Not visited", "Viewed, unanswered", "Skipped", "Answered"])(
  "renders only the number for %s while preserving accessible status",
  (status) => {
    render(
      <QuestionTile
        number={4}
        status={status}
        current
        flagged
        disabled={false}
        onClick={() => {}}
      />,
    );
    const button = screen.getByRole("button", {
      name: `Question 4, ${status}, marked for review`,
    });
    expect(button.textContent).toBe("4");
    expect(button.getAttribute("aria-current")).toBe("step");
    expect(button.classList.contains("answered")).toBe(status === "Answered");
    expect(button.classList.contains("skipped")).toBe(status === "Skipped");
  },
);
it("preserves navigation restrictions without removing saved status", () => {
  const click = vi.fn();
  render(
    <QuestionTile
      number={2}
      status="Answered"
      current={false}
      flagged={false}
      disabled
      onClick={click}
    />,
  );
  const button = screen.getByRole("button", { name: "Question 2, Answered" });
  fireEvent.click(button);
  expect(click).not.toHaveBeenCalled();
  expect(button.classList.contains("answered")).toBe(true);
});
