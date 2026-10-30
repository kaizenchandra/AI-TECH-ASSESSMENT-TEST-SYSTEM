# Continued implementation: readiness, human review, consent and blueprints

This increment follows a fresh source/test gap review. It does not replace the full specification or declare mandatory backlog optional. Earlier verification entries are historical.

## Inspected gaps and changes

| Requirements | Source gap | Change |
|---|---|---|
| CHECK-01..03, CONT-AC-03/08 | Old media callbacks could overwrite newer readiness; playback confirmation could be overwritten; changed devices did not invalidate checks | Generation-scoped track callbacks, latest-state microphone updates, device-change invalidation, explicit 10-minute client freshness expiry and network round-trip display. Existing server freshness/exception guards remain authoritative. |
| AI-REPORT-04, AI-UI-02 | Review-request intake had no reviewer assignment, resolution or candidate status | Tenant-bound paginated queue, self-assignment by admin/evaluator, revision-checked resolution, audited decisions and candidate-visible response/status. No automatic score mutation. |
| AI-CAND-02, AI-PRIV-04, AI-TIME-02 | Consent existed only during readiness; withdrawal/re-grant could revive an older job | Candidate purpose preference API/UI; versioned consent history; exact consent revision bound to feedback jobs and checked before dispatch/application/review/release. Changing consent never touches attempt timing, answers or deterministic scores. |
| AI-BLUE-01..03, AI-STATE-01 | Selector existed only as a pure function; no stored review or assessment handoff | Persisted constraints, approved competency revision, selected item versions, solving-time estimates, seed/algorithm and independent approval. Revalidation before preparation and publication. Infeasibility is visible and cannot be approved. |
| FR-BE-04, Workflow B, INSTR-01/02 | No job records or pinned job context in candidate onboarding | Jobs create/edit/archive/search, canonical skills, required/optional proficiency requirements and total-weight validation, draft association, immutable job snapshots at publication and safe candidate role/target display. |

## Contracts

All routes use existing session authentication, CSRF and Problem Details. `If-Match` is an integer revision. Foreign resources return 404; forbidden reviewer roles return 403; malformed input 422; stale revisions/non-reviewable states 409. Collection pages contain at most 25 records (consent list is bounded at 100 assignments).

| Route under `/api/v1` | Authorization and contract |
|---|---|
| GET `/candidate/review-requests?page=0` | Owner; candidate-safe reason, state, response, dates and revision. No reviewer identity, answer keys or internal AI payload. |
| GET `/review-requests?page=0` | Tenant staff; review queue. |
| POST `/review-requests/{id}/decisions` | Tenant ADMIN/EVALUATOR, If-Match; `{action:ASSIGN_TO_ME}` or `{action:RESOLVE,response}`. Must be assigned to resolve. Admin can take over another assignment. Response 1–4000 characters and candidate-visible. Resolution is terminal; further correction requests create new records. |
| GET `/candidate/purpose-consents` | Owner; acknowledged assignment, consent revision, choice and disclosure version. |
| POST `/candidate/purpose-consents` | Verified owner, If-Match; `{invitationId,purpose:POST_SUBMISSION_FEEDBACK,disclosureVersion:disclosure-v1,granted}`. Same choice is a no-op at the current revision. Changed choice records a new revision/event. |
| POST `/blueprints` | Author; approved competencyId/competencyRevision, requirements `{skill,type,count}`, explicit pool `{artifactId,revision,questionIndex,minutes}`, maxMinutes, reviewMinutes, seed, language. Returns stored result/id. Pool ≤200, form ≤100, budget ≤480 minutes. |
| GET `/blueprints?page=0` | Tenant staff; current proposal/review states, immutable constraints and selection records. |
| POST `/blueprint-versions/{id}/review-decisions` | Different authorized staff reviewer, If-Match; `{decision:APPROVED or REJECTED,reason}`. Approval reruns deterministic validation against current approved sources. |
| POST `/blueprints/{id}/assessments` | Author, If-Match; assessment title, durationMinutes, passingPercent, latestStart, completionDeadline. Duration must equal approved blueprint budget; client item selections are ignored in favor of the pinned selection. Creates a draft, not publication. |
| GET `/skills` | Tenant staff; canonical skill IDs currently supported by authoring. |
| GET `/jobs?page=0&search=` | Tenant staff; 25 records, title search ≤200 characters. Skills included. |
| POST `/jobs` | Author; title, department, description, experienceLevel, minYears/maxYears, difficulty, durationMinutes, passingPercent, availableFrom/latestStart/completionDeadline, skills `{skill,weight,required,proficiency}`. Skills unique and weights total 100. Returns id/revision. |
| PUT `/jobs/{id}` | Author, If-Match, same full details; only OPEN jobs. Replaces skill rows in the same transaction. |
| POST `/jobs/{id}/archive` | Author, If-Match; prevents future linking/publication through this job. Existing published snapshots and active attempts remain unchanged. |
| POST `/jobs/{id}/assessments` | Author; `{assessmentId}`; both resources same tenant, assessment DRAFT, job OPEN. Updates association and assessment revision. It does not silently replace the assessment's configuration with job preferences. |

Blueprint skill weights are enforced as exact item-count proportions because this preparation path gives every item ten points. A non-integral distribution returns explicit infeasibility. Solving-time estimates are supplied by the author and reviewed with the proposal; they are not calibrated measurements. Language is enforced for coding items. Exclusions are explicit omissions from the pool. This increment does **not** yet enforce calibrated difficulty distributions, prior-candidate exposure, organization reuse limits or statistical form comparability. It does not silently claim those constraints were checked. Each prepared assessment uses the existing immutable publication snapshot; candidate forms are not adaptively changed.

## Migrations and concurrency

- V9 backfills review tenant ownership from attempts, adds tenant-safe reviewer/attempt references, revisions, resolution timestamps and queue indexes. Existing requests remain OPEN and unassigned.
- V10 adds consent revisions (existing choices start at zero), binds historical jobs to the current consent revision, and stores new preference events. A withdrawal followed by re-grant never restores an old job's authorization. Existing released historical snapshots are retained; deletion is a separate organizational process.

  Readiness captures an initial choice only. Repeated readiness acknowledgments preserve the existing preference and consent revision, preventing a stale onboarding page from undoing a withdrawal. Subsequent changes use the preferences API. Candidate overview is refreshed after preference changes.
- V11 stores blueprint proposals/reviews and tenant-safe assessment references. Existing assessments have no blueprint reference and remain unchanged.
- V12 adds normalized jobs/job_skills and tenant-safe draft associations. Existing assessment versions backfill empty job context. New publication inserts a pinned job title, department, experience label, requirements and source revision in the initial immutable version insert. Editing a job cannot rewrite existing candidate instructions. Candidate instructions distinguish role target weights from actual test coverage.

Review writes lock only their review row. Consent writes lock the acknowledgment row. Blueprint approval checks sources again; assessment preparation reuses existing transactionally safe approved-question copying. None of these calls run on the answer-save or heartbeat path.

New proposal and assessment-creation endpoints do not yet provide transport idempotency keys; buttons prevent simultaneous clicks, but lost-response retries can produce additional drafts. Review/consent writes reject stale mutations. This remains mandatory reliability work, not a claim of complete AI-API-02 compliance.

## Operational limits

Run the existing Compose startup and frontend/backend commands in README. No new secrets are required. No model-provider call or untrusted code execution was performed during these changes. Physical-device validation remains separate from simulated media tests. Staff responses are plain text; reviewers must exclude confidential bank material. Generalized throttling, full review audit history views, deletion/retention across backups/providers, jurisdiction-specific policy approval, resource-assigned evaluation access and expert benchmark gates remain mandatory backlog.

Review-request intake reuses the current open request for an identical retry, rejects a different concurrent open request, and caps intake at ten per attempt per 24 hours under the attempt row lock. Broader distributed rate limiting remains outstanding.

Fresh verification passed 49 backend tests, 11 frontend tests, four browser journeys and two sandbox policy tests. Exact commands, corrected failures and unrun live checks are recorded in [verification](verification.md). Start locally with `docker compose up --build -d`, then open http://localhost:8080 (mail inbox http://localhost:8025). Preserve the existing `.env` and database volume.

## Question bank, retakes, staff administration and integrity continuation (2026-10-30)

Fresh inspection found no reusable manual question bank, explicit retake authorization, or role-change workflow. Integrity ingestion charged duplicate observations against rate limits. These gaps now have implementation and regression coverage; broader specification gaps below remain mandatory.

### Question bank

The recruiter **Question bank** screen uses real APIs for title search/pagination, JSON creation/import, full confidential reviewer preview, new revisions, independent approval/rejection, archive and selection into an existing manual draft. JSON uses `AssessmentContent.Question`, with canonical skills and zero-based option indexes; imports accept 1–50 items atomically. This is a structured JSON editor, not yet a full visual question editor. All staff may read/review tenant questions; ADMIN/RECRUITER may author. Candidates cannot use these routes.

| API under `/api/v1` | Contract |
|---|---|
| GET `/question-bank?search=&page=0` | Staff; latest version, state, revision and full reviewer content; 25 rows/page. |
| POST `/question-bank/import` | Author; `{questions:[Question]}`; validates every item before inserting; returns question/version IDs. New items are DRAFT. |
| POST `/question-bank/{id}/versions` | Author, If-Match; complete Question DTO, creates a new immutable draft version. |
| POST `/question-bank/{id}/review` | Different verified staff reviewer, If-Match; `{approve:boolean,reason}`; validates current draft and records reviewer/reason/time. |
| POST `/question-bank/{id}/archive` | Author, If-Match; blocks further revisions, selection and new publication/assignment; existing attempts remain pinned. |
| POST `/question-bank/assessments/{id}/questions` | Author, assessment If-Match; `{versionId}`; approved nonarchived item only, draft assessment only; cannot change an approved blueprint. Adds snapshot and source reference atomically. |

V13 adds tenant-safe bank/version/source tables and a database trigger rejecting content mutation of version rows. No prior migration was modified. Existing assessments are unchanged. Publication locks and rechecks source rows. New revisions do not silently revoke old approved versions. Archive explicitly stops future use of all versions. Manual coding review checks bounded schema/test configuration; **it is not reference-solution execution certification**. The existing AI coding approval path still requires its sandbox evidence. Reference-solution certification for manual bank coding items, collections/tags, duplicate detection, rich metadata and transport idempotency on draft/import remain incomplete.

### Retakes

**Candidates → Authorize a retake** calls POST `/invitations/{id}/retakes`, author only, with `Idempotency-Key` UUID and `{expiresAt,reason}` (reason 1–2000 characters). It requires a submitted/expired original attempt, a nonrevoked original invitation and an available published assessment. One exceptional retake per original invitation is allowed; retake invitations cannot authorize another retake. Availability/deadlines are not extended. The new invitation pins the original assessment version, still requires verified claim/onboarding, and permits one new attempt. Existing answers, scores, acknowledgments and timing remain unchanged.

V14 persists authorization reason/actor, original/new invitation, expiry and scoped idempotency key with tenant-safe foreign keys. An advisory transaction lock serializes invitation creation for tenant/assessment/email. Repeated key/payload returns the same ID; conflicting keys or duplicate retakes return 409. Ordinary invitation creation now rejects an already invited email for the same assessment, preventing bypass through repeated initial invites. Email delivery uses the existing transactional notification outbox. A configurable per-assessment retake count, invitation resend/replacement for never-started expired invitations, and expanded scheduling policy remain incomplete; do not use the retake endpoint to replace those workflows.

### Staff permissions and governance

ADMIN-only GET `/members?page=0` returns staff identity, role, verification and decision revision (25/page). POST `/members/{id}/role-decisions` accepts If-Match and `{role,reason}`, supporting ADMIN/RECRUITER/EVALUATOR. It serializes on the organization, rechecks the acting administrator, rejects self-role changes, candidate role changes and cross-tenant resources, and rejects stale revisions. Another current administrator must change an administrator's role, preserving an administrator. GET `/members/{id}/role-decisions?page=0` exposes paginated audit reasons only to tenant administrators. V15 stores tenant-scoped immutable decision history. The Settings screen includes role editing and recent history; staff creation controls are hidden for nonadmins.

Authorization reads current membership on later API calls; the existing AI worker rechecks requester role before dispatch/application. This is role administration, **not completion of resource-scoped permissions**. Evaluator assignment restrictions, granular purpose/provider/release permissions, suspension/session revocation, provider policy administration, purpose retention and automated dependency deletion still need implementation. Role history retention is not yet configurable; production governance activation remains blocked.

### Integrity reliability

`IntegrityApi.ingest` deduplicates IDs within and across batches before applying quotas. Identical retries at quota are accepted without extra rows; conflicting reuse of an ID returns 409. Timestamps normalize to PostgreSQL microseconds. Ownership, disclosure and bounded type/batch checks still apply. These observations never change scores, attempt revisions or timing. The separate integrity transaction and 30-day retention remain unchanged.

### Live prerequisites and operation

Inspected local configuration is `AI_MODE=fake`, `APP_ENV=local`, `AI_LIVE_APPROVED=false`; no live model/key or runner configuration was present. No provider request or candidate-code execution was attempted. Configure approved provider model/key/processing policy through deployment secrets and supply the separately hardened runner described in `../sandbox/README.md` before live checks. Do not paste credentials into chat or run `sandbox/runtime/execute.mjs` on the application host. Provider regions/contractual retention, numeric benchmark approvals, monetary price reconciliation, and cluster isolation verification remain production gates.

Local startup: from repository root run `docker compose up --build -d`, then open `http://localhost:8080` and Mailpit at `http://localhost:8025`. Flyway applies V13–V15 additively. Create two verified staff accounts to exercise independent question review. Published-bank sources that are archived deliberately block new invitations/publication; existing candidate attempts continue.
