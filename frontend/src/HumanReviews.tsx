import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";
type Review = {
  id: string;
  attempt_id: string;
  reason: string;
  state: string;
  response: string | null;
  revision: number;
  created_at: string;
  assigned_to?: string;
};
export function HumanReviewQueue({
  candidate = false,
}: {
  candidate?: boolean;
}) {
  const [page, setPage] = useState(0),
    [message, setMessage] = useState("");
  const cache = useQueryClient();
  const key = ["human-reviews", candidate, page];
  const list = useQuery({
    queryKey: key,
    queryFn: () =>
      api<Review[]>(
        `${candidate ? "/candidate" : ""}/review-requests?page=${page}`,
      ),
    refetchInterval: 10000,
  });
  async function decide(r: Review, action: string, response?: string) {
    try {
      await post(
        `/review-requests/${r.id}/decisions`,
        { action, response },
        { "If-Match": String(r.revision) },
      );
      await cache.invalidateQueries({ queryKey: ["human-reviews"] });
      setMessage(
        action === "RESOLVE"
          ? "Response shared with the candidate. Scores were not changed."
          : "Review assigned to you.",
      );
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Request failed");
    }
  }
  return (
    <section className="card">
      <h2>{candidate ? "Your human-review requests" : "Human-review queue"}</h2>
      <p>
        {candidate
          ? "Requests are reviewed by your organization. Contact your recruiter for response times; assessment scores stay unchanged unless an evaluator records a separate correction."
          : "Assign a request before resolving it. Responses are visible to the candidate: exclude answer keys, hidden tests and confidential notes. Score corrections use the separate audited evaluation workflow."}
      </p>
      {list.isPending && <p>Loading requests…</p>}
      {list.isError && (
        <p role="alert">
          Unable to load requests.{" "}
          <button onClick={() => void list.refetch()}>Retry</button>
        </p>
      )}
      {list.data?.length === 0 && <p>No requests on this page.</p>}
      {list.data?.map((r) => (
        <article className="evidence" key={r.id}>
          <h3>
            Assessment {r.attempt_id.slice(0, 8)} ·{" "}
            {r.state.replaceAll("_", " ")}
          </h3>
          <time dateTime={r.created_at}>
            {new Date(r.created_at).toLocaleString()}
          </time>
          <p style={{ whiteSpace: "pre-wrap" }}>{r.reason}</p>
          {r.response && (
            <p>
              <strong>Reviewer response:</strong> {r.response}
            </p>
          )}
          {!candidate && r.state !== "RESOLVED" && (
            <>
              <button
                className="secondary"
                onClick={() => void decide(r, "ASSIGN_TO_ME")}
              >
                Assign to me
              </button>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const response = String(
                    new FormData(e.currentTarget).get("response"),
                  );
                  void decide(r, "RESOLVE", response);
                }}
              >
                <label>
                  Candidate-visible response
                  <textarea name="response" required maxLength={4000} />
                </label>
                <button>Resolve request</button>
              </form>
            </>
          )}
        </article>
      ))}
      <div className="row">
        <button
          className="secondary"
          disabled={page === 0}
          onClick={() => setPage((p) => p - 1)}
        >
          Previous requests
        </button>
        <span>Page {page + 1}</span>
        <button
          className="secondary"
          disabled={!list.data || list.data.length < 25}
          onClick={() => setPage((p) => p + 1)}
        >
          Next requests
        </button>
      </div>
      <p role="status">{message}</p>
    </section>
  );
}
