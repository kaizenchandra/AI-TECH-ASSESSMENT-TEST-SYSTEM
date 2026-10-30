import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, post } from "./api";
export function useIntegrity(id: string, active: boolean) {
  const [lost, setLost] = useState(false);
  useEffect(() => {
    if (!active) return;
    let queue: { id: string; type: string; observedAt: string }[] = [],
      sending = false,
      closed = false;
    const add = (type: string) => {
      if (queue.length >= 40) {
        setLost(true);
        return;
      }
      queue.push({
        id: crypto.randomUUID(),
        type,
        observedAt: new Date().toISOString(),
      });
    };
    const handlers: Record<string, () => void> = {
      blur: () => add("BLUR"),
      focus: () => add("FOCUS"),
      offline: () => add("OFFLINE"),
      online: () => add("ONLINE"),
    };
    const visibility = () => add(document.hidden ? "HIDDEN" : "VISIBLE");
    document.addEventListener("visibilitychange", visibility);
    Object.entries(handlers).forEach(([k, v]) => window.addEventListener(k, v));
    const timer = setInterval(() => {
      if (sending || !queue.length || !navigator.onLine) return;
      sending = true;
      const batch = queue.slice(0, 20);
      void post(`/candidate/attempts/${id}/integrity-events`, { events: batch })
        .then(() => {
          queue = queue.slice(batch.length);
        })
        .catch(() => {
          if (!closed) setLost(true);
          queue = queue.slice(batch.length);
        })
        .finally(() => {
          sending = false;
        });
    }, 15000);
    return () => {
      closed = true;
      clearInterval(timer);
      document.removeEventListener("visibilitychange", visibility);
      Object.entries(handlers).forEach(([k, v]) =>
        window.removeEventListener(k, v),
      );
    };
  }, [id, active]);
  return lost;
}
export function IntegrityPanel({ id }: { id: string }) {
  const [message, setMessage] = useState("");
  const result = useQuery({
    queryKey: ["integrity", id],
    queryFn: () =>
      api<{
        limitation: string;
        events: { id: string; event_type: string; received_at: string }[];
        notes: { note: string; created_at: string }[];
      }>(`/evaluations/${id}/integrity`),
  });
  return (
    <section>
      <h3>Integrity observations — human review</h3>
      <p>{result.data?.limitation}</p>
      <details>
        <summary>Latest observations (up to 500)</summary>
        <ul>
          {result.data?.events.map((e) => (
            <li key={e.id}>
              {e.event_type} · {new Date(e.received_at).toLocaleString()}
            </li>
          ))}
        </ul>
      </details>
      {result.data?.notes.map((n, i) => (
        <p key={i}>{n.note}</p>
      ))}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          void post(`/evaluations/${id}/integrity-reviews`, {
            note: data.get("note"),
          })
            .then(() => {
              setMessage("Review recorded without changing scores.");
              void result.refetch();
            })
            .catch((e) => setMessage(e.message));
        }}
      >
        <label>
          Human review note
          <textarea name="note" required maxLength={2000} />
        </label>
        <button>Record review note</button>
      </form>
      <p role="status">{message}</p>
    </section>
  );
}
