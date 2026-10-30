import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";
type Member = {
  user_id: string;
  email: string;
  role: string;
  verified: boolean;
  revision: number;
};
export function MemberRoles({ userId }: { userId: string }) {
  const [page, setPage] = useState(0),
    [message, setMessage] = useState("");
  const cache = useQueryClient();
  const members = useQuery({
    queryKey: ["members", page],
    queryFn: () => api<Member[]>(`/members?page=${page}`),
  });
  return (
    <section className="card">
      <h2>Staff permissions</h2>
      <p>
        Role changes take effect on subsequent API requests. Another
        administrator must change your own role. Existing assessment answers and
        review history are preserved.
      </p>
      {message && <p role="status">{message}</p>}
      {members.isPending && <p>Loading staff…</p>}
      {members.error && <p role="alert">{members.error.message}</p>}
      {members.data?.map((m) => (
        <MemberRole
          key={`${m.user_id}:${m.revision}`}
          member={m}
          self={m.user_id === userId}
          done={async (message) => {
            setMessage(message);
            await cache.invalidateQueries({ queryKey: ["members"] });
          }}
        />
      ))}
      <button disabled={page === 0} onClick={() => setPage(page - 1)}>
        Previous staff page
      </button>
      <span>Page {page + 1}</span>
      <button
        disabled={members.data?.length !== 25}
        onClick={() => setPage(page + 1)}
      >
        Next staff page
      </button>
    </section>
  );
}
function MemberRole({
  member,
  self,
  done,
}: {
  member: Member;
  self: boolean;
  done: (message: string) => Promise<void>;
}) {
  const [role, setRole] = useState(member.role),
    [reason, setReason] = useState(""),
    [busy, setBusy] = useState(false),
    [history, setHistory] = useState<
      | {
          revision: number;
          previous_role: string;
          resulting_role: string;
          reason: string;
        }[]
      | null
    >(null);
  return (
    <form
      className="panel"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        try {
          await post(
            `/members/${member.user_id}/role-decisions`,
            { role, reason },
            { "If-Match": String(member.revision) },
          );
          await done("Staff role updated and audited.");
        } catch (error) {
          await done(error instanceof Error ? error.message : "Request failed");
        } finally {
          setBusy(false);
        }
      }}
    >
      <h3>{member.email}</h3>
      <p>
        {member.verified ? "Verified" : "Email verification pending"} · Current
        role: {member.role}
      </p>
      <label>
        Staff role for {member.email}
        <select
          value={role}
          disabled={self || busy}
          onChange={(e) => setRole(e.target.value)}
        >
          <option>ADMIN</option>
          <option>RECRUITER</option>
          <option>EVALUATOR</option>
        </select>
      </label>
      <label>
        Reason for changing {member.email}
        <input
          required
          maxLength={2000}
          value={reason}
          disabled={self || busy}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>
      <button disabled={self || busy || !reason.trim() || role === member.role}>
        Apply role change
      </button>
      <button
        type="button"
        onClick={() =>
          void api<typeof history>(`/members/${member.user_id}/role-decisions`)
            .then(setHistory)
            .catch((error) => done(error.message))
        }
      >
        Show latest 25 role decisions
      </button>
      {history && (
        <ul>
          {history.length === 0 ? (
            <li>No role changes recorded.</li>
          ) : (
            history.map((h) => (
              <li key={h.revision}>
                {h.previous_role} → {h.resulting_role}: {h.reason}
              </li>
            ))
          )}
        </ul>
      )}
    </form>
  );
}
