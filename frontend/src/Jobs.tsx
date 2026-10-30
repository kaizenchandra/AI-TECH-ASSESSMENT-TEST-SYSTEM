import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, post } from "./api";
type Skill = {
  skill: string;
  weight: number;
  required: boolean;
  proficiency: string;
};
type Job = {
  id: string;
  title: string;
  department: string;
  description: string;
  experience_level: string;
  min_years: number;
  max_years: number;
  difficulty: string;
  duration_minutes: number;
  passing_percent: number;
  available_from: string;
  latest_start: string;
  completion_deadline: string;
  revision: number;
  state: string;
  skills: Skill[];
};
const local = (s?: string) => {
  const d = s ? new Date(s) : new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
};
export function JobsPanel() {
  const cache = useQueryClient();
  const [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [edit, setEdit] = useState<Job | null>(null),
    [open, setOpen] = useState(false),
    [message, setMessage] = useState("");
  const jobs = useQuery({
      queryKey: ["jobs", search, page],
      queryFn: () =>
        api<Job[]>(`/jobs?page=${page}&search=${encodeURIComponent(search)}`),
    }),
    skills = useQuery({
      queryKey: ["skills"],
      queryFn: () => api<string[]>("/skills"),
    }),
    assessments = useQuery({
      queryKey: ["assessments"],
      queryFn: () =>
        api<{ id: string; title: string; status: string }[]>("/assessments"),
    });
  async function action(work: () => Promise<unknown>) {
    try {
      await work();
      await cache.invalidateQueries({ queryKey: ["jobs"] });
      await cache.invalidateQueries({ queryKey: ["assessments"] });
      setMessage("Saved.");
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Request failed");
    }
  }
  return (
    <>
      <section className="card">
        <h2>Job roles and required skills</h2>
        <p>
          Define job-relevant criteria and explicit proficiency expectations.
          Years of experience are context, not proof of proficiency. Job details
          are pinned when linked assessments are published.
        </p>
        <label>
          Search jobs
          <input
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            maxLength={200}
          />
        </label>
        <button
          onClick={() => {
            setEdit(null);
            setOpen(true);
          }}
        >
          Create job opening
        </button>
      </section>
      {open && (
        <form
          className="card"
          key={edit?.id ?? "new"}
          onSubmit={(e) => {
            e.preventDefault();
            const d = new FormData(e.currentTarget);
            const payload = {
              title: d.get("title"),
              department: d.get("department"),
              description: d.get("description"),
              experienceLevel: d.get("experienceLevel"),
              minYears: Number(d.get("minYears")),
              maxYears: Number(d.get("maxYears")),
              difficulty: d.get("difficulty"),
              durationMinutes: Number(d.get("durationMinutes")),
              passingPercent: Number(d.get("passingPercent")),
              availableFrom: new Date(
                String(d.get("availableFrom")),
              ).toISOString(),
              latestStart: new Date(String(d.get("latestStart"))).toISOString(),
              completionDeadline: new Date(
                String(d.get("completionDeadline")),
              ).toISOString(),
              skills: (skills.data ?? [])
                .filter((s) => d.get(`include-${s}`))
                .map((s) => ({
                  skill: s,
                  weight: Number(d.get(`weight-${s}`)),
                  required: !!d.get(`required-${s}`),
                  proficiency: d.get(`proficiency-${s}`),
                })),
            };
            void action(async () => {
              if (edit)
                await api(`/jobs/${edit.id}`, {
                  method: "PUT",
                  headers: { "If-Match": String(edit.revision) },
                  body: JSON.stringify(payload),
                });
              else await post("/jobs", payload);
              setOpen(false);
            });
          }}
        >
          <h3>{edit ? "Edit job" : "New job"}</h3>
          <label>
            Job title
            <input
              name="title"
              required
              maxLength={200}
              defaultValue={edit?.title}
            />
          </label>
          <label>
            Department
            <input
              name="department"
              maxLength={120}
              defaultValue={edit?.department ?? "Engineering"}
            />
          </label>
          <label>
            Job description
            <textarea
              name="description"
              required
              maxLength={20000}
              defaultValue={edit?.description}
            />
          </label>
          <label>
            Experience level
            <input
              name="experienceLevel"
              required
              maxLength={80}
              defaultValue={edit?.experience_level ?? "Intermediate"}
            />
          </label>
          <label>
            Minimum years
            <input
              name="minYears"
              type="number"
              min={0}
              max={60}
              step={0.1}
              required
              defaultValue={edit?.min_years ?? 0}
            />
          </label>
          <label>
            Maximum years
            <input
              name="maxYears"
              type="number"
              min={0}
              max={60}
              step={0.1}
              required
              defaultValue={edit?.max_years ?? 5}
            />
          </label>
          <label>
            Difficulty
            <select
              name="difficulty"
              defaultValue={edit?.difficulty ?? "INTERMEDIATE"}
            >
              {["FOUNDATION", "INTERMEDIATE", "ADVANCED"].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          <label>
            Preferred duration (minutes)
            <input
              name="durationMinutes"
              type="number"
              min={1}
              max={480}
              required
              defaultValue={edit?.duration_minutes ?? 60}
            />
          </label>
          <label>
            Preferred passing percentage
            <input
              name="passingPercent"
              type="number"
              min={0}
              max={100}
              required
              defaultValue={edit?.passing_percent ?? 60}
            />
          </label>
          <label>
            Job assessment availability (local time)
            <input
              name="availableFrom"
              type="datetime-local"
              required
              defaultValue={local(edit?.available_from)}
            />
          </label>
          <label>
            Job latest start (local time)
            <input
              name="latestStart"
              type="datetime-local"
              required
              defaultValue={edit ? local(edit.latest_start) : undefined}
            />
          </label>
          <label>
            Job completion deadline (local time)
            <input
              name="completionDeadline"
              type="datetime-local"
              required
              defaultValue={edit ? local(edit.completion_deadline) : undefined}
            />
          </label>
          <fieldset>
            <legend>
              Skill requirements · included weights must total 100%
            </legend>
            {skills.data?.map((s) => {
              const saved = edit?.skills.find((v) => v.skill === s);
              return (
                <div className="evidence" key={s}>
                  <label>
                    <input
                      name={`include-${s}`}
                      type="checkbox"
                      defaultChecked={!!saved}
                    />
                    Include {s}
                  </label>
                  <label>
                    {s} weight %
                    <input
                      name={`weight-${s}`}
                      type="number"
                      min={0}
                      max={100}
                      defaultValue={saved?.weight ?? 0}
                    />
                  </label>
                  <label>
                    <input
                      type="checkbox"
                      name={`required-${s}`}
                      defaultChecked={saved?.required ?? true}
                    />
                    {s} is required (otherwise optional)
                  </label>
                  <label>
                    {s} observable proficiency
                    <input
                      name={`proficiency-${s}`}
                      maxLength={120}
                      defaultValue={
                        saved?.proficiency ??
                        "Explain and implement core concepts"
                      }
                    />
                  </label>
                </div>
              );
            })}
          </fieldset>
          <button>Save job</button>
          <button
            type="button"
            className="secondary"
            onClick={() => setOpen(false)}
          >
            Cancel editing
          </button>
        </form>
      )}
      {jobs.isPending && <p>Loading jobs…</p>}
      {jobs.isError && (
        <p role="alert">
          Unable to load jobs.{" "}
          <button onClick={() => void jobs.refetch()}>Retry</button>
        </p>
      )}
      {jobs.data?.length === 0 && <p>No matching jobs.</p>}
      {jobs.data?.map((j) => (
        <section className="card" key={j.id}>
          <h3>
            {j.title} · {j.state}
          </h3>
          <p>
            {j.department} · {j.experience_level} · {j.min_years}–{j.max_years}{" "}
            years
          </p>
          <p>{j.description}</p>
          <ul>
            {j.skills.map((s) => (
              <li key={s.skill}>
                {s.skill}: {s.weight}% · {s.required ? "Required" : "Optional"}{" "}
                · {s.proficiency}
              </li>
            ))}
          </ul>
          {j.state === "OPEN" && (
            <>
              <button
                className="secondary"
                onClick={() => {
                  setEdit(j);
                  setOpen(true);
                }}
              >
                Edit {j.title}
              </button>
              <button
                className="secondary"
                onClick={() =>
                  void action(() =>
                    post(`/jobs/${j.id}/archive`, undefined, {
                      "If-Match": String(j.revision),
                    }),
                  )
                }
              >
                Archive {j.title}
              </button>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const d = new FormData(e.currentTarget);
                  void action(() =>
                    post(`/jobs/${j.id}/assessments`, {
                      assessmentId: d.get("assessment"),
                    }),
                  );
                }}
              >
                <label>
                  Link draft assessment to {j.title}
                  <select name="assessment" required>
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
                <p>
                  Association preserves the assessment’s existing duration,
                  scoring and question configuration. Review those settings
                  before publishing.
                </p>
                <button>Link assessment</button>
              </form>
            </>
          )}
        </section>
      ))}
      <div className="row">
        <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          Previous jobs
        </button>
        <span>Page {page + 1}</span>
        <button
          disabled={!jobs.data || jobs.data.length < 25}
          onClick={() => setPage((p) => p + 1)}
        >
          Next jobs
        </button>
      </div>
      <p role="status">{message}</p>
    </>
  );
}
