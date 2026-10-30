# Functional requirements specification

Status: implementation contract. The supplied ../prompt.md remains authoritative; optional items do not displace mandatory requirements. Each requirement below includes its acceptance condition. Implementation coverage is recorded separately in verification.md; specification is not evidence of completion.

## Actors and permissions
|Actor|Scope|Allowed actions|
|---|---|---|
|Platform administrator|Explicit platform grant|Tenant provisioning, operational policy; no implicit access to candidate answers|
|Organization administrator|Membership tenant|Membership, provider/purpose/budget/retention configuration|
|Recruiter|Membership tenant and authoring grant|Jobs, candidates, invitations, drafts, publication of approved content|
|Evaluator|Assigned tenant resources|Evidence review, reasoned rubric overrides and report approval|
|Candidate|Own claimed invitations/attempts|Onboarding, saved answers, released feedback, appeals|
|AI worker|Persisted job tenant and revalidated requester|Produce drafts only; cannot approve/publish/change scores or timing|

## Frontend
- FRS-FE-001 Identity: signup, verification, login, logout and session-expiry recovery; errors never disclose other tenants.
- FRS-FE-002 Recruiter dashboard: paginated/searchable assessment, invitation, progress and evaluation aggregates.
- FRS-FE-003 Job editor: description, experience band, skills, proficiency and weights; weights total 100 before approval.
- FRS-FE-004 Question bank: create/version/archive/import/search/preview SCQ, MCQ and coding; keys visible only to authorized authors.
- FRS-FE-005 Assessment builder: immutable published content, schedule, duration, scoring, navigation, randomization, retakes and prerequisites.
- FRS-FE-006 Invitation management: candidate identity/profile, expiring scoped links, resend/revoke and delivery status.
- FRS-FE-007 Onboarding: invitation eligibility, verified email, versioned instructions, privacy/AI disclosures and prerequisite acknowledgment.
- FRS-FE-008 Compatibility: browser, session, viewport, network, camera and microphone permission checks with remediation; never record without consent.
- FRS-FE-009 Workspace: palette, radio/checkbox selections, Monaco language/source editor, review flags and current position.
- FRS-FE-010 Persistence: saving/saved/offline/error states; only server ACK means saved; refresh restores acknowledged values.
- FRS-FE-011 Timer: server-authoritative remaining time anchored to response receipt; refresh/sleep/reconnect resynchronizes.
- FRS-FE-012 Submission: summary, unanswered/unsaved warnings, confirmation, retry-safe final acknowledgment.
- FRS-FE-013 Results: deterministic score separated from proposed judgments; permitted feedback only after release.
- FRS-FE-014 Authoring AI: competency evidence/edit/review, batch progress, validation errors, blueprint infeasibility and explicit approval.
- FRS-FE-015 Governance AI: evidence/lineage, provider/policy/budget management, releases and appeals.
- FRS-FE-016 Accessibility: keyboard controls, semantic labels, status announcements, contrast, zoom and nonvisual score information.

## Backend
- FRS-BE-001 Identity: BCrypt passwords, server sessions, CSRF, verified email and authorization on every resource.
- FRS-BE-002 Tenancy: derive tenant from authenticated membership; query tenant and owner together; reject unauthorized IDs without existence leaks.
- FRS-BE-003 Invitations: cryptographic opaque tokens stored as hashes, intended verified email binding, expiry/revocation/claim transaction.
- FRS-BE-004 Content: immutable published question/rubric/assessment versions; no candidate keys/hidden tests in DTOs.
- FRS-BE-005 Start: lock invitation, check verified identity, availability, latest start, prerequisites and attempt limits; concurrent starts return one attempt.
- FRS-BE-006 Save: lock attempt, enforce lease/time/state, expected revision and idempotency hash; durable answer history and ACK commit atomically.
- FRS-BE-007 Resume: owner-only acquisition of expiring browser lease, persisted timer reconciliation; active competing tabs receive 409.
- FRS-BE-008 Strict timer: elapsed wall time always consumes allowance; overall deadline caps all modes.
- FRS-BE-009 Controlled timer: heartbeat grace consumes active time; after grace eligible interruption grants bounded pause, then elapsed time consumes allowance again. Each detected interruption consumes one pause. No client duration accepted.
- FRS-BE-010 Finalization: submit/expiry closes answer mutation exactly once; scheduled finalizer recovers after process restarts.
- FRS-BE-011 Scoring: SCQ, MCQ exact/partial with configured penalty; coding scores from isolated execution evidence, infrastructure errors remain pending.
- FRS-BE-012 Execution: separate hardened runner, no application credentials, egress denied, resource/output/time bounds and retryable infrastructure failure.
- FRS-BE-013 Evaluation: per-question/per-skill denominator and weighted total; human overrides preserve baseline and reason.
- FRS-BE-014 Notifications: transactional outbox, bounded retry and delivery tracking independent of business commits.
- FRS-BE-015 Audit: actor, tenant, immutable source/version references and lifecycle timestamps; sensitive payload minimization.
- FRS-BE-016 AI orchestration: durable jobs/leases/outbox, purpose schemas, bounded retries, cancellation and idempotency.
- FRS-BE-017 AI policy: approved purpose/provider/region/retention, per-tenant atomic reservations, quotas, kill switch and no silent fallback.
- FRS-BE-018 AI authority: drafts require semantic validation and distinct human reviewer; approval never implies publication.
- FRS-BE-019 AI evidence: source hashes, template/model/policy/validator versions, citations, uncertainty and review lineage.
- FRS-BE-020 AI isolation: no tools; untrusted text cannot alter authority; AI never on save/timer/submission critical path.
- FRS-BE-021 AI privacy: minimized input, no media/demographics, optional consent withdrawal, dependency-aware deletion and backup tombstones.
- FRS-BE-022 AI release: final submission references, stale result suppression, human approval, candidate allowlists and correction requests.

## User stories and edge cases
- US-001 Recruiter authors, validates, approves and publishes a Java assessment; a candidate completes it and an evaluator reviews immutable evidence.
- US-002 Candidate loses network or browser, returns to acknowledged answers and policy-correct time; grace, pause budget and overall deadline are independently tested.
- US-003 Concurrent tabs/stale retries cannot overwrite a newer answer; a lost response is safely retried using the same key and content.
- US-004 Provider outages/malformed responses leave exams and deterministic scores usable; failed enrichment remains visible.
- US-005 A different tenant cannot read an ID, infer its existence or retrieve its sources through a worker/export.
- US-006 A reviewer rejects a misleading generated item; it cannot enter a published form. Editing creates a new draft and invalidates dependent approval.
- US-007 A candidate declines optional AI and requests human review without losing core access or time.

## State machines and business rules
Invitation DRAFT → SENT → OPENED → CLAIMED, with EXPIRED/REVOKED alternatives. Assessment DRAFT → PUBLISHED → ACTIVE → CLOSED → ARCHIVED. Attempt onboarding NOT_STARTED → INSTRUCTIONS → SYSTEM_CHECK → READY, then IN_PROGRESS ↔ PAUSED; SUBMITTED/EXPIRED terminate answer changes; evaluation is independently pending/review-required/evaluated. Jobs QUEUED → RUNNING → SUCCEEDED, RETRY_WAIT/FAILED/CANCEL_REQUESTED/CANCELLED/EXPIRED. Artifact DRAFT → VALIDATING → REVIEW_REQUIRED → APPROVED/REJECTED; technical failure VALIDATION_FAILED. Reports DRAFT → REVIEW_REQUIRED → APPROVED → RELEASED; regeneration yields sibling versions and old results can be SUPERSEDED.

MVP includes all mandatory workflows and AI additions in prompt.md. Optional later scope: public registration, PDF reports, live adaptive testing, conversational interviews and predictive analytics. No autonomous employment decisions, integrity inference or protected-trait inference is permitted.

## Production acceptance gates
The original 25-step journey and AI-DONE-01..03 must execute. Each AI-AC-01..13 needs recorded results. Organization owners approve numerical benchmark thresholds, minimum samples, provider contracts/region/retention and processing basis. Zero observed authorization/secret leakage failures required in the defined suite; finite tests do not prove universal security. Unrun checks block a production-ready claim.
