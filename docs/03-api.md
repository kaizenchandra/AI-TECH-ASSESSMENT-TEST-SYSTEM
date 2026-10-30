# API contracts

Base `/api/v1`. Same-origin session cookie; obtain `{token,header}` from GET `/csrf` and send that header on every mutation, including login/register/verify. Login uses form URL encoding (`username`, `password`); all other bodies use JSON. Logout rotates/clears session, fetch a fresh CSRF token after login. Runtime OpenAPI is generated at `/v3/api-docs` on the authenticated backend (not publicly proxied). Domain errors use Problem Details; Spring Security currently uses standard 401/403 errors.

|Method / path|Authorization|Request / semantics|Response|
|---|---|---|---|
|GET /csrf|Public|Session-bound CSRF token|200 token/header|
|POST /register|Public + CSRF|email, password (12–128), organization for recruiter or invitation for candidate|200 verification required; 409 duplicate|
|POST /verify|Public + CSRF|token; hash lookup, one-hour expiry, single use|200 verified; 404 invalid/expired|
|POST /login|Public + CSRF|Form username/password|200 authenticated; 401 invalid|
|POST /logout|Session + CSRF|None|204|
|GET /me|Session|None|200 actor id/org/role/verified/email|
|POST /members|Verified organization ADMIN|email, initial password, RECRUITER/EVALUATOR role|200 ID; verification email queued|
|GET /dashboard|Verified staff|Organization inferred from membership|200 counts|
|GET /assessments?page=0|Verified staff|50-row page, descending creation|200 metadata array|
|POST /assessments|Verified ADMIN/RECRUITER|AssessmentContent record; full schema validated|200 id/revision|
|GET /assessments/{id}|Verified staff in owner tenant|UUID|200 author-only content including keys|
|POST /assessments/{id}/publish|Author|If-Match numeric revision; DRAFT only|200 immutable versionId; 409 stale|
|POST /invitations|Author|assessmentId, email, expiresAt ≤ latestStart|200 id/delivery QUEUED; mail outbox committed atomically|
|GET /invitations|Staff|Tenant-scoped most recent 100|200 redacted metadata, no token hashes|
|POST /invitations/{id}/revoke|Author|UUID|200; audit|
|POST /candidate/invitations/claim|Verified intended email + tenant|token|200 invitation id; 410 expired/revoked|
|GET /candidate/assignments|Owner session|Claimed invitations only|200 candidate content allowlist and attempt metadata|
|POST /candidate/invitations/{id}/acknowledgment|Owner|instructions, browser, cookies, network, camera, microphone, optionalAi booleans|200; failed prerequisites 422|
|POST /candidate/invitations/{id}/start|Verified owner|X-Tab-ID UUID; acknowledged prerequisites|200 Attempt view; atomic unique invitation start; 403 ineligible; 409 lease conflict|
|POST /candidate/attempts/{id}/resume|Owner|X-Tab-ID; replace only expired lease|200 authoritative snapshot; 409 active other tab|
|POST /candidate/attempts/{id}/heartbeat|Owner + live tab lease|X-Tab-ID|200 authoritative snapshot, lease extended 30 s|
|PUT /candidate/attempts/{id}/answers|Owner + live tab lease|X-Tab-ID, Idempotency-Key; questionId, expectedRevision, answer {selected,language,code,flagged}|200 acknowledged revision; duplicate identical key returns prior ACK; different payload/snapshot conflict 409|
|POST /candidate/attempts/{id}/submit|Owner + live lease if active|X-Tab-ID|200 final snapshot; repeated submit returns final result|
|POST /candidate/attempts/{id}/run|Owner|None in current build|503 explicitly unavailable; no code executed|
|GET /reports|Staff tenant|Most recent 100|200 deterministic/provisional scores; no confidential question keys|
|GET /candidate/ai-disclosures|Session|None|200 versioned disclosure|
|POST /candidate/attempts/{id}/review-requests|Owner|reason 1–4000 chars|200 review request ID|
|POST /ai-jobs|Verified author|Idempotency-Key UUID; purpose COMPETENCY with source, or QUESTION with approvedMapId|202 ID/statusUrl; 429 quota; 422 unsupported source/purpose|
|GET /ai-jobs[/{id}]|Staff tenant|Scoped ID or last 100|200 job metadata; no raw source|
|POST /ai-jobs/{id}/cancel|Author tenant|UUID|200 cancellation requested; running call is best effort|
|GET /ai-artifacts|Staff tenant|Last 100|200 drafts and provenance; never candidate-accessible|
|POST /ai-artifact-versions/{id}/review-decisions|Different verified staff reviewer|If-Match; decision APPROVED/REJECTED and reason|200; 403 self-review; 409 stale|
|GET /ai-usage|Staff tenant|None|200 reserved/spent application quota units; no monetary claim|

All unknown UUIDs are scoped by owner/tenant before retrieval. Tenant IDs in request bodies are unsupported. API DTOs reject unknown fields. Question correctness and coding language/source are validated server-side. Candidate save limit: 100,000 characters source, ≤12 unique options. Request size is additionally bounded at reverse proxy.

## Not yet implemented contracts
The remaining AI-API-01 specialized routes (jobs/competency draft editing, blueprint persistence/approval, privacy/purpose-consent administration), retry endpoints, question bank, jobs/skills CRUD, full pagination, password reset, and platform administration are required backlog. Generic authoring jobs do not constitute completion of those contracts.

## Coding and reviewed results — implemented continuation
All routes below use the `/api/v1` prefix. Missing/wrong-owner IDs produce 404. Mutations require the session CSRF header. Errors are Problem Details.

|Method and path|Authorization / input|Result and concurrency|
|---|---|---|
|POST /candidate/attempts/{id}/run|Owner, active attempt; X-Tab-ID, Idempotency-Key; `{questionId,expectedRevision}`|202 `{id,state}`; exact acknowledged revision; duplicate revision returns same job; 429 at three outstanding executions|
|GET /candidate/executions/{id}|Owner of SAMPLE job only|200 state, failure code and bounded sample output; hidden GRADE jobs return 404|
|GET /evaluations/{id}|Tenant staff|200 deterministic baseline, optional final summary, revisions and authorized AI artifacts|
|POST /evaluations/{id}/insight-jobs|Tenant author, finalized evaluation and optional consent; Idempotency-Key|202 durable REPORT job; server constructs minimized source; budget/policy failures are explicit|
|POST /coding-submissions/{id}/ai-feedback-jobs|Tenant author; completed GRADE execution ID; Idempotency-Key|202 FEEDBACK job bound to final answer and scoring revision; execution ID serves as the persisted coding submission reference|
|POST /evaluations/{id}/reviews|Tenant ADMIN/EVALUATOR; If-Match; `{feedback,recommendation,adjustments:[{questionId,score,reason}]}`|200; score bounds checked; new immutable evaluation version; old baseline preserved; prior adjustments persist; stale revision 409|
|POST /reports/{id}/release|Tenant ADMIN/EVALUATOR; If-Match, Idempotency-Key|200 release ID; requires current approved evaluation; snapshot and audit committed together; duplicate matching key replays; conflicting key 409|
|GET /candidate/results|Owner|200 latest released summary per attempt; no keys, hidden tests, recruiter recommendation or unreleased artifacts|
|GET /reports/export|Tenant staff|200 text/csv; bounded to 10,000 rows, identifiers and numeric deterministic percentages only|

Feedback artifacts use the existing independent-review endpoint. Consent and scoring revision are checked before dispatch, application, review and inclusion at release. Released snapshots remain historical records; automated withdrawal/deletion is still outstanding. AI proposed rubric scores are not implemented and cannot affect scores. Review APIs currently use tenant-level evaluator permissions, not assignment-scoped permissions; treat that as a production blocker. Lists require full pagination in a later mandatory implementation step.

## Continued question-bank and administration routes

See [question-bank, retake and member-role contracts](07-continuation-workflows.md) for the V13–V15 routes, DTOs, authorization, revision/idempotency rules, status codes and limitations. These extend the existing API; bank content is a confidential staff DTO and must never be substituted for the allowlisted candidate question projection.
