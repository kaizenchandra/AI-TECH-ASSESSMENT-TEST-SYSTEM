# Recruiter AI question generation

The existing AI studio now supports single-choice and multiple-choice batches (1–3 items), and single coding problems for problem solving or system-design component implementation. Coding languages are Java, Python and JavaScript. Difficulty is an uncalibrated estimate. System-design coding is a bounded executable component task, not an essay interview or a complete architectural assessment.

## Workflow

1. Draft a competency map from role requirements; another authorized tenant reviewer approves it.
2. Choose generation settings and request questions from that approved map. Jobs use the existing durable queue, reservation controls and provider adapter. Candidate clients never contact providers.
3. Inspect the generated description, options, correct indices and rationale. Coding previews also show starter/reference code, public samples and confidential hidden tests to authorized staff.
4. Request isolated reference validation for coding. The worker sends code and test inputs to the existing private sandbox gateway, compares results internally, and tests an empty-solution mutation. This limited mutation check is not comprehensive test coverage or a proof of correctness. Runner outages block coding approval, not manual choice authoring or candidate saves.
5. A separate reviewer approves the draft. Select approved questions, title, duration, passing percentage and time window to create a draft assessment. Each question receives ten points; choice scoring is exact-match without negative marks. The default controlled pause policy and back navigation are shown in the form. Publication remains a separate explicit action.

## API additions

- `POST /api/v1/ai-jobs`: QUESTION requests accept `generation: {kind, difficulty, count, language}`. Allowed kinds: SINGLE, MULTIPLE, PROBLEM_SOLVING, SYSTEM_DESIGN. Default for older clients: SINGLE/INTERMEDIATE/1/java. Requires approvedMapId, author permission and existing Idempotency-Key. Returns 202; malformed settings 422, budget denial 429, unauthorized source 404.
- `POST /api/v1/ai-artifacts/{id}/coding-validation`: tenant staff, coding artifact in REVIEW_REQUIRED; returns 202. Repeated requests reuse the validation row; UNAVAILABLE may be retried up to three dispatches. Failed correctness requires a new draft. Expired worker leases recover with bounded attempts.
- `GET /api/v1/ai-artifacts/{id}/coding-validation`: tenant staff; returns state, finding and attempts. NOT_RUN when no validation exists. No candidate access.
- `POST /api/v1/assessments/from-ai`: author only. Body: title, durationMinutes, passingPercent, latestStart, completionDeadline and items `{artifactId, revision, questionIndex}`. Transactionally checks approved versions and coding attestation, copies selected questions into a DRAFT and records lineage. Returns id/revision/status. Invalid selection 422, stale/unapproved/untested content 409, cross-tenant reference 404. The UI prevents double-click submission; this draft-creation endpoint does not currently offer transport-retry idempotency.
- Existing review endpoint additionally rejects coding approval without matching successful validation. Publication rechecks source artifact state and revision; published snapshots remain immutable.

Migration V8 adds validation leases/hash attestations and tenant-bound assessment-to-artifact lineage. Existing assessments and attempts are unchanged. Worker provider calls and sandbox execution occur outside answer/timer transactions. Reference solutions remain in staff AI artifacts, never in candidate question DTOs.

## Configuration and verification limits

Use the existing environment template. Local fake mode requires no credentials and labels every draft. Live OpenAI mode requires an organization-approved model, server-side key and AI_LIVE_APPROVED; no live call has been made for this change. Coding validation requires RUNNER_URL/RUNNER_TOKEN and the hardened sandbox deployment. Never run generated solutions in the application process or host shell.

Implemented checks cover schema validation, immutable approved selection, stale versions, tenant access and candidate DTO confidentiality. The database test uses synthetic attestation rows to exercise approval gates; it does not establish live sandbox correctness. Browser automation covers generation, independent review and preparing a draft through the UI. Full benchmark approval, calibrated difficulty, stronger mutation coverage, free-form system-design interviews and production provider/privacy gates remain outstanding.

## Executed verification (2026-10-30)

| Check | Result |
|---|---|
| `mvn -f backend/pom.xml -B -ntp -Dapi.version=1.44 verify` (Java 21, OrbStack Docker host) | PASS: 44 tests; PostgreSQL Testcontainers applies all eight migrations |
| `cd frontend && npm run build` | PASS: TypeScript and Vite; existing Monaco bundle-size warning remains |
| `cd frontend && npm test` | PASS: 10 tests |
| `cd frontend && npm run test:e2e` against rebuilt Compose app | PASS: four browser tests, including approved AI question selection and draft preparation |
| Live provider generation | NOT RUN: local fake only |
| Hardened runner reference execution | NOT RUN: isolated gateway not configured locally |

The executable checks do not establish completion of every requirement in the full platform specification.
