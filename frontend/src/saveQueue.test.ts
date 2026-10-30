// @vitest-environment jsdom
import { beforeEach, describe, it, expect, vi } from "vitest";
import { SaveQueue } from "./saveQueue";
import { api, ApiError, Answer } from "./api";
const answer: Answer = {
  selected: [1],
  language: null,
  code: null,
  flagged: false,
};
beforeEach(() => localStorage.clear());
describe("durable save acknowledgments", () => {
  it("does not notify saved-state UI before the server acknowledgment", async () => {
    let complete!: (value: { revision: number }) => void;
    const transport = vi.fn(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    ) as typeof api;
    const acknowledged = vi.fn();
    const queue = new SaveQueue(
      "pending-colors",
      0,
      "a",
      "t",
      () => {},
      transport,
      acknowledged,
    );
    const saving = queue.enqueue("q1", { ...answer, skipped: true }, "q2");
    expect(acknowledged).not.toHaveBeenCalled();
    complete({ revision: 1 });
    await saving;
    expect(acknowledged).toHaveBeenCalledWith(
      expect.objectContaining({
        nextQuestionId: "q2",
        answer: expect.objectContaining({ skipped: true }),
      }),
    );
  });
  it("serializes revisions and persists the acknowledged revision", async () => {
    const requests: unknown[] = [];
    const transport = vi.fn(async (_path: string, init: RequestInit) => {
      const body = JSON.parse(String(init.body));
      requests.push(body);
      return { revision: body.expectedRevision + 1 };
    }) as unknown as typeof api;
    const queue = new SaveQueue(
      "test",
      0,
      "attempt",
      "tab",
      () => {},
      transport,
    );
    await queue.enqueue("q1", answer);
    await queue.enqueue("q2", answer);
    expect(queue.revision).toBe(2);
    expect(requests).toMatchObject([
      { expectedRevision: 0 },
      { expectedRevision: 1 },
    ]);
    expect(queue.pending).toBe(0);
  });
  it("restores an unacknowledged request and reuses its idempotency key", async () => {
    localStorage.setItem(
      "test",
      JSON.stringify({
        revision: 0,
        queue: [
          { key: "fixed-key", questionId: "q1", expectedRevision: 0, answer },
        ],
      }),
    );
    const send = vi.fn(async () => ({ revision: 1 }));
    const queue = new SaveQueue(
      "test",
      1,
      "attempt",
      "tab",
      () => {},
      send as typeof api,
    );
    expect(queue.pending).toBe(1);
    await queue.flush();
    expect(send.mock.calls[0]).toBeDefined();
    expect(queue.revision).toBe(1);
    expect(queue.pending).toBe(0);
  });
  it("never overwrites a newer answer after a revision conflict", async () => {
    const status = vi.fn();
    const send = vi.fn(async () => {
      throw new ApiError(409, "Stale revision");
    });
    const queue = new SaveQueue(
      "test",
      0,
      "attempt",
      "tab",
      status,
      send as typeof api,
    );
    await expect(queue.enqueue("q1", answer)).rejects.toThrow("Stale revision");
    expect(send).toHaveBeenCalledTimes(1);
    expect(queue.pending).toBe(1);
    expect(queue.revision).toBe(0);
    expect(status).toHaveBeenCalledWith("Save conflict / sign-in required");
  });
  it("does not discard pending data during submit cleanup", async () => {
    localStorage.setItem(
      "test",
      JSON.stringify({
        revision: 0,
        queue: [
          { key: "fixed", questionId: "q1", expectedRevision: 0, answer },
        ],
      }),
    );
    const queue = new SaveQueue("test", 0, "a", "t", () => {});
    expect(() => queue.clear()).toThrow("Unsaved answers remain");
    expect(localStorage.getItem("test")).not.toBeNull();
  });
});
