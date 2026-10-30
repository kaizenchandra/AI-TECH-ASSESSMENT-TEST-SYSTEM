import { useState } from "react";
import { post } from "./api";
export function Retakes({
  invitations,
  refreshed,
}: {
  invitations: { id: string; email: string; revoked: boolean }[];
  refreshed: () => Promise<unknown>;
}) {
  const [source, setSource] = useState(""),
    [expiry, setExpiry] = useState(""),
    [reason, setReason] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [key, setKey] = useState(() => crypto.randomUUID());
  return (
    <form
      className="card"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        try {
          await post(
            `/invitations/${source}/retakes`,
            { expiresAt: new Date(expiry).toISOString(), reason },
            { "Idempotency-Key": key },
          );
          setMessage(
            "Retake invitation queued. The original submission remains unchanged.",
          );
          setKey(crypto.randomUUID());
          await refreshed();
        } catch (error) {
          setMessage(error instanceof Error ? error.message : "Request failed");
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2>Authorize a retake</h2>
      <p>
        One retake is permitted after a submitted or expired original attempt.
        It uses the same published assessment and must start within its existing
        availability window.
      </p>
      <label>
        Original invitation
        <select
          required
          value={source}
          disabled={busy}
          onChange={(e) => {
            setSource(e.target.value);
            setKey(crypto.randomUUID());
          }}
        >
          <option value="">Choose invitation</option>
          {invitations
            .filter((i) => !i.revoked)
            .map((i) => (
              <option key={i.id} value={i.id}>
                {i.email} · {i.id.slice(0, 8)}
              </option>
            ))}
        </select>
      </label>
      <label>
        Retake expiry (local time)
        <input
          type="datetime-local"
          required
          value={expiry}
          disabled={busy}
          onChange={(e) => {
            setExpiry(e.target.value);
            setKey(crypto.randomUUID());
          }}
        />
      </label>
      <label>
        Authorization reason
        <textarea
          required
          maxLength={2000}
          value={reason}
          disabled={busy}
          onChange={(e) => {
            setReason(e.target.value);
            setKey(crypto.randomUUID());
          }}
        />
      </label>
      <button disabled={busy}>Authorize and send retake</button>
      {message && <p role="status">{message}</p>}
    </form>
  );
}
