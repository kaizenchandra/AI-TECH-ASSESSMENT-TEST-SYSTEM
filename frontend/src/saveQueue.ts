import { Answer, api } from "./api";
export type Pending = {
  key: string;
  questionId: string;
  expectedRevision: number;
  answer: Answer;
  nextQuestionId?: string;
};
export class SaveQueue {
  private queue: Pending[];
  private running: Promise<void> | null = null;
  revision: number;
  constructor(
    private storageKey: string,
    revision: number,
    private attempt: string,
    private tab: string,
    private changed: (status: string) => void,
    private transport = api,
    private acknowledged?: (item: Pending) => void,
  ) {
    const saved = localStorage.getItem(storageKey);
    const state = saved
      ? (JSON.parse(saved) as { revision: number; queue: Pending[] })
      : null;
    this.queue = state?.queue ?? [];
    this.revision = this.queue.length ? state!.revision : revision;
  }
  get pending() {
    return this.queue.length;
  }
  get snapshots() {
    return this.queue;
  }
  private persist() {
    localStorage.setItem(
      this.storageKey,
      JSON.stringify({ revision: this.revision, queue: this.queue }),
    );
  }
  enqueue(questionId: string, answer: Answer, nextQuestionId?: string) {
    this.queue.push({
      key: crypto.randomUUID(),
      questionId,
      expectedRevision: this.revision + this.queue.length,
      answer: structuredClone(answer),
      nextQuestionId,
    });
    this.persist();
    this.changed("Saving");
    return this.flush();
  }
  flush(): Promise<void> {
    if (this.running) return this.running;
    this.running = this.drain().finally(() => {
      this.running = null;
    });
    return this.running;
  }
  private async drain() {
    while (this.queue.length) {
      const item = this.queue[0];
      this.changed(navigator.onLine ? "Saving" : "Offline / unsynced");
      let result: { revision: number } | undefined;
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          result = await this.transport<{ revision: number }>(
            `/candidate/attempts/${this.attempt}/answers`,
            {
              method: "PUT",
              headers: { "X-Tab-ID": this.tab, "Idempotency-Key": item.key },
              body: JSON.stringify({
                questionId: item.questionId,
                expectedRevision: item.expectedRevision,
                answer: item.answer,
                nextQuestionId: item.nextQuestionId,
              }),
            },
          );
          break;
        } catch (error) {
          if (
            error instanceof Error &&
            "status" in error &&
            [400, 401, 403, 409, 422].includes(Number(error.status))
          ) {
            this.changed("Save conflict / sign-in required");
            throw error;
          }
          if (attempt === 2) {
            this.changed("Offline / unsynced — retry needed");
            throw error;
          }
          await new Promise((resolve) =>
            setTimeout(resolve, 500 * 2 ** attempt),
          );
        }
      }
      if (!result) throw new Error("No save acknowledgment");
      this.revision = result.revision;
      this.queue.shift();
      this.persist();
      this.acknowledged?.(item);
    }
    this.changed("Saved");
  }
  clear() {
    if (this.pending) throw new Error("Unsaved answers remain");
    localStorage.removeItem(this.storageKey);
  }
}
