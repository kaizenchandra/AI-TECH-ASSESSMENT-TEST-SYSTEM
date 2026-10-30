import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, post } from "./api";
export function Accommodations() {
  const [message, setMessage] = useState("");
  const rows = useQuery({
    queryKey: ["readiness-requests"],
    queryFn: () =>
      api<
        {
          id: string;
          invitation_id: string;
          reason: string;
          expires_at: string | null;
        }[]
      >("/readiness-requests"),
  });
  return (
    <section className="card">
      <h2>Readiness accommodation requests</h2>
      <p>
        Administrators may authorize specific device-check exceptions for 24
        hours. Other prerequisites still apply.
      </p>
      {rows.data?.map((r) => (
        <article key={r.id}>
          <p>{r.reason}</p>
          <p>
            {r.expires_at
              ? `Exception expires ${new Date(r.expires_at).toLocaleString()}`
              : "Pending review"}
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const data = new FormData(e.currentTarget);
              void post(`/invitations/${r.invitation_id}/readiness-exception`, {
                checks: data.getAll("checks"),
                reason: data.get("reason"),
              })
                .then(() => {
                  setMessage(
                    "Exception recorded. Candidate can refresh and repeat readiness checks.",
                  );
                  void rows.refetch();
                })
                .catch((e) => setMessage(e.message));
            }}
          >
            {["camera", "microphone", "microphoneLevel", "playback"].map(
              (k) => (
                <label key={k}>
                  <input name="checks" type="checkbox" value={k} />
                  {k}
                </label>
              ),
            )}
            <label>
              Approval reason
              <input name="reason" required maxLength={2000} />
            </label>
            <button>Authorize selected exceptions</button>
          </form>
        </article>
      ))}
      <p role="status">{message}</p>
    </section>
  );
}
