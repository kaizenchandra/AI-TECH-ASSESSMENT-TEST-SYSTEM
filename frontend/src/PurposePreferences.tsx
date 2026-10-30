import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";
type Consent = {
  invitation_id: string;
  optional_ai: boolean;
  consent_revision: number;
  disclosure_version: string;
};
export function PurposePreferences() {
  const cache = useQueryClient(),
    [message, setMessage] = useState("");
  const list = useQuery({
    queryKey: ["purpose-consents"],
    queryFn: () => api<Consent[]>("/candidate/purpose-consents"),
  });
  return (
    <section className="card">
      <h2>Optional AI preferences</h2>
      <p>
        Optional post-submission AI feedback is reviewed by a person. Declining
        it does not affect test access, timing or deterministic scores.
        Withdrawal stops future processing and invalidates older pending
        feedback. Previously released records are retained under your
        organization’s policy; contact your recruiter for deletion or human
        review.
      </p>
      {list.isPending && <p>Loading preferences…</p>}
      {list.isError && (
        <p role="alert">
          Unable to load preferences.{" "}
          <button onClick={() => void list.refetch()}>Retry</button>
        </p>
      )}
      {list.data?.length === 0 && (
        <p>Preferences appear after you acknowledge assessment instructions.</p>
      )}
      {list.data?.map((c) => (
        <div className="evidence" key={c.invitation_id}>
          <p>
            Assignment {c.invitation_id.slice(0, 8)} · Notice{" "}
            {c.disclosure_version} · {c.optional_ai ? "Allowed" : "Declined"}
          </p>
          <button
            className="secondary"
            onClick={async () => {
              try {
                await post(
                  "/candidate/purpose-consents",
                  {
                    invitationId: c.invitation_id,
                    purpose: "POST_SUBMISSION_FEEDBACK",
                    disclosureVersion: c.disclosure_version,
                    granted: !c.optional_ai,
                  },
                  { "If-Match": String(c.consent_revision) },
                );
                await cache.invalidateQueries({
                  queryKey: ["purpose-consents"],
                });
                await cache.invalidateQueries({ queryKey: ["assignments"] });
                setMessage(
                  c.optional_ai
                    ? "Optional processing withdrawn. Assessment timing and scores are unchanged."
                    : "Optional feedback allowed for new requests.",
                );
              } catch (e) {
                setMessage(e instanceof Error ? e.message : "Request failed");
              }
            }}
          >
            {c.optional_ai
              ? "Withdraw optional AI consent"
              : "Allow optional AI feedback"}
          </button>
        </div>
      ))}
      <p role="status">{message}</p>
    </section>
  );
}
