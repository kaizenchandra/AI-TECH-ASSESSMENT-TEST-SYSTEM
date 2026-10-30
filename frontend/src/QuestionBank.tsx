import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";

type Item = {
  id: string;
  revision: number;
  version_id: string;
  version: number;
  state: string;
  archived: boolean;
  author_id: string;
  content: Record<string, unknown> & {
    title: string;
    type: string;
    skill: string;
  };
};
type Assessment = {
  id: string;
  title: string;
  status: string;
  revision: number;
};
export function QuestionBank({
  userId,
  role,
}: {
  userId: string;
  role: string;
}) {
  const client = useQueryClient();
  const [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [editor, setEditor] = useState(""),
    [editing, setEditing] = useState<Item | null>(null),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [reason, setReason] = useState(""),
    [assessment, setAssessment] = useState("");
  const items = useQuery({
    queryKey: ["question-bank", search, page],
    queryFn: () =>
      api<Item[]>(
        `/question-bank?search=${encodeURIComponent(search)}&page=${page}`,
      ),
  });
  const assessments = useQuery({
    queryKey: ["assessments"],
    queryFn: () => api<Assessment[]>("/assessments"),
  });
  const author = role === "ADMIN" || role === "RECRUITER";
  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setMessage("");
    try {
      await action();
      setMessage(success);
      await client.invalidateQueries({ queryKey: ["question-bank"] });
      await client.invalidateQueries({ queryKey: ["assessments"] });
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }
  function newQuestion() {
    setEditing(null);
    setEditor(
      JSON.stringify(
        {
          id: crypto.randomUUID(),
          type: "SINGLE",
          title: "",
          description: "",
          skill: "java",
          options: ["Option A", "Option B"],
          correct: [0],
          points: 10,
          penalty: 0,
          partialCredit: false,
          language: null,
          starterCode: null,
          samples: [],
          coding: null,
        },
        null,
        2,
      ),
    );
  }
  return (
    <section className="panel">
      <h2>Question bank</h2>
      <p>
        Versioned questions for reuse. Saving creates a draft; a different
        reviewer must approve it before selection. Coding review here is manual
        and does not certify sandbox execution.
      </p>
      <label>
        Search question titles
        <input
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </label>
      {author && <button onClick={newQuestion}>Create question</button>}
      {message && <p role="status">{message}</p>}
      {items.isPending && <p>Loading questions…</p>}
      {items.error && <p role="alert">{items.error.message}</p>}
      {author && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            void run(async () => {
              const parsed = JSON.parse(editor);
              if (editing)
                await post(`/question-bank/${editing.id}/versions`, parsed, {
                  "If-Match": String(editing.revision),
                });
              else
                await post("/question-bank/import", {
                  questions: Array.isArray(parsed) ? parsed : [parsed],
                });
              setEditor("");
              setEditing(null);
            }, "Draft saved. Review is required.");
          }}
        >
          <label>
            {editing
              ? "Edit question as a new version"
              : "Question JSON or import array (maximum 50)"}
            <textarea
              rows={14}
              value={editor}
              onChange={(e) => setEditor(e.target.value)}
              required
              maxLength={1000000}
            />
          </label>
          <p>
            Use the assessment question schema: SINGLE, MULTIPLE or CODING;
            canonical skill IDs and zero-based correct-option indexes. Imports
            validate atomically.
          </p>
          <button disabled={busy || !editor}>Save draft</button>
        </form>
      )}
      <label>
        Review reason
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          maxLength={2000}
        />
      </label>
      {author && (
        <label>
          Target draft assessment
          <select
            value={assessment}
            onChange={(e) => setAssessment(e.target.value)}
          >
            <option value="">Choose assessment</option>
            {assessments.data
              ?.filter((a) => a.status === "DRAFT")
              .map((a) => (
                <option key={a.id} value={a.id}>
                  {a.title}
                </option>
              ))}
          </select>
        </label>
      )}
      {items.data?.length === 0 && <p>No questions found.</p>}
      {items.data?.map((item) => (
        <article key={item.id} className="panel">
          <h3>{item.content.title}</h3>
          <p>
            {item.content.type} · {item.content.skill} · Version {item.version}{" "}
            · {item.archived ? "Archived" : item.state}
          </p>
          <details>
            <summary>Review full question and grading details</summary>
            <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
              {JSON.stringify(item.content, null, 2)}
            </pre>
          </details>
          {!item.archived && (
            <div className="actions">
              {author && (
                <button
                  disabled={busy}
                  onClick={() => {
                    setEditing(item);
                    setEditor(JSON.stringify(item.content, null, 2));
                  }}
                >
                  Create revision
                </button>
              )}
              {item.state === "DRAFT" &&
                item.author_id !== userId &&
                [true, false].map((approve) => (
                  <button
                    key={String(approve)}
                    disabled={busy || !reason.trim()}
                    onClick={() =>
                      void run(
                        () =>
                          post(
                            `/question-bank/${item.id}/review`,
                            { approve, reason },
                            { "If-Match": String(item.revision) },
                          ),
                        approve ? "Question approved." : "Question rejected.",
                      )
                    }
                  >
                    {approve ? "Approve question" : "Reject question"}
                  </button>
                ))}
              {author && item.state === "APPROVED" && (
                <button
                  disabled={busy || !assessment}
                  onClick={() =>
                    void run(() => {
                      const target = assessments.data?.find(
                        (a) => a.id === assessment,
                      );
                      if (!target) throw new Error("Select a current draft");
                      return post(
                        `/question-bank/assessments/${target.id}/questions`,
                        { versionId: item.version_id },
                        { "If-Match": String(target.revision) },
                      );
                    }, "Approved version added. Review and publish the assessment separately.")
                  }
                >
                  Add to assessment
                </button>
              )}
              {author && (
                <button
                  disabled={busy}
                  onClick={() =>
                    void run(
                      () =>
                        post(`/question-bank/${item.id}/archive`, undefined, {
                          "If-Match": String(item.revision),
                        }),
                      "Archived. Existing attempts are unchanged; future publication is blocked.",
                    )
                  }
                >
                  Archive question
                </button>
              )}
            </div>
          )}
        </article>
      ))}
      <nav aria-label="Question bank pagination">
        <button disabled={page === 0 || busy} onClick={() => setPage(page - 1)}>
          Previous page
        </button>
        <span>Page {page + 1}</span>
        <button
          disabled={items.data?.length !== 25 || busy}
          onClick={() => setPage(page + 1)}
        >
          Next page
        </button>
      </nav>
    </section>
  );
}
