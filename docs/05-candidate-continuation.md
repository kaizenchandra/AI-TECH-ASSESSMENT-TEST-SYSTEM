# Candidate journey continuation — 2026-10-30

This change implements an integrated increment of Section 20. It does not complete the full original platform specification. Existing invitation authentication, immutable assessment snapshots, timing, answer revisions and isolated coding boundaries remain in use.

## Gap assessment and implementation

|Requirement|Previous gap|Implemented behavior|
|---|---|---|
|INSTR-01..03|Only title, duration and question count were displayed|Pinned category/skill counts, marking rules, passing threshold, languages, deadline/timezone, pause limits and navigation rules; acknowledgment binds assessment version and candidate-policy-v2|
|CHECK-01..03|Boolean checks, no freshness guard, no preview/input test|Local camera preview, microphone level, explicit playback confirmation, secure context/viewport/session/network checks, 10-minute server acknowledgment expiry and start revalidation|
|CHECK-04|No pre-start accommodation workflow|Candidate request, staff queue, administrator authorization for selected media checks, 24-hour expiry, reason and audit; core security/network prerequisites cannot be waived|
|NAV-02..07|Palette counted unsaved local answers, skip was absent, navigation location was not saved|Acknowledgment-only status colors, explicit skip/clear, persisted visits/current question, atomic answer/navigation revision, count invariants, accessible labels and independent review flags|
|INT-03..06|No observation ingestion or reviewer timeline|Bounded event batches, owner authorization, deduplication, independent rate lock, human timeline/review and candidate explanations; observations never update scores or timers|

SCQ/MCQ green means a nonempty server-acknowledged selection, not a correct response. Coding green means acknowledged nonempty code different from the starter template, not compilation success or a complete solution. Blue means explicitly skipped without an acknowledged answer. Review flags overlap primary states. Clearing an answer resets skip; navigating past an answer preserves it. Current-question outline does not replace status color.

## Contracts and persistence

The existing `PUT /api/v1/candidate/attempts/{id}/answers` additionally accepts optional `nextQuestionId`; the answer accepts optional `skipped`. Existing clients omit these safely. The mutation still requires `X-Tab-ID`, `Idempotency-Key` and `expectedRevision`. Answer, current position, visits, receipt and audit commit together. Stale revisions return 409. Skipping cannot bypass back-navigation restrictions or terminal immutability. Reading an acknowledgment is not permission to mutate again.

`GET /candidate/assignments` returns safe aggregate overview, company, assessment version and current authorized exception names, without question text or keys. `POST /candidate/invitations/{id}/acknowledgment` requires `assessmentVersionId`, `policyVersion=candidate-policy-v2`, instruction consent and validated check fields. The UI submits this when readiness completes, not just when Start is clicked. Start rejects missing, stale or version-mismatched acknowledgments and rechecks required fields/exception expiry. Returning to an active attempt preserves its original clock.

|Route under /api/v1|Contract|
|---|---|
|POST /candidate/invitations/{id}/accommodation-requests|Owner; `{note}` up to 2,000 characters; persisted for staff review|
|GET /readiness-requests|Tenant staff; latest 100 requests and exception expiry|
|POST /invitations/{id}/readiness-exception|Tenant ADMIN; `{checks,reason}`; only camera/microphone/microphoneLevel/playback; expires after 24 hours; audited|
|POST /candidate/attempts/{id}/integrity-events|Owner, active attempt, acknowledged monitoring disclosure; `{events:[{id,type,observedAt}]}`; 1–20 events; no arbitrary metadata|
|GET /evaluations/{id}/integrity|Tenant staff; latest 500 observations, latest 100 notes, uncertainty notice|
|POST /candidate/attempts/{id}/integrity-explanations|Owner; bounded `{note}`; cannot change scores|
|POST /evaluations/{id}/integrity-reviews|Tenant ADMIN/EVALUATOR; bounded `{note}` with audit; cannot change scores|

All mutations use session authentication/CSRF. Event IDs are unique per attempt. Event types are allowlisted. Future observations beyond five minutes and observations older than 24 hours are rejected. The server caps observations at 60/minute and 5,000/attempt. Client observation time never controls the exam clock. The in-memory frontend buffer holds at most 40 events, sends at most 20 every 15 seconds and reports loss of coverage; failed batches are dropped to avoid unbounded retries. No raw media, clipboard contents, browsing history or keystrokes are sent. Event loss is not an exam failure.

V6 adds visits, acknowledgment version references, expiring media exceptions, integrity events and reviews. Existing answer/current-question rows backfill visits; existing answers and attempt deadlines are preserved. Unstarted legacy invitations must acknowledge the new policy. Existing active attempts resume unchanged and are not monitored without the new acknowledgment. V7 adds readiness-request intake. Applied historical migrations were not modified.

## Privacy and operational limits

Local media checks stop tracks on component exit or Stop preview. They do not establish identity and remain spoofable client observations. Normal assessment mode does not maintain a webcam recording, perform biometric inference or impose clipboard/fullscreen lockdown. The UI records focus, visibility and connectivity; the endpoint also reserves MEDIA_LOST/FULLSCREEN_EXIT types, but there is no ongoing media/fullscreen monitoring in this increment.

Observation and integrity-note retention is currently a fixed, disclosed 30 days, enforced hourly in primary storage. Tenant-configurable retention, backup erasure/tombstones, retention for accommodation records and organization-wide privacy administration remain mandatory work. Human review is tenant-role scoped; resource-assigned evaluator authorization is still outstanding. Intake/note APIs need generalized per-user throttling and complete pagination before production deployment.

## Verification and remaining acceptance

Executed automated tests include stale readiness with unchanged active-attempt recovery, skipped/cleared state persistence, stale mutation rejection, integrity deduplication/ownership without revision changes, permitted/expired media exceptions, palette status rules, acknowledgment callback timing, simulated denied webcam permissions and track cleanup. The real local browser journey covers invitation registration/verification, category overview, pre-readiness start denial, skip → blue → reload → resume at next question → answer → green → clear → unanswered, submission and approved result release.

Real physical webcam/microphone/audio verification, complete screen-reader/zoom audits, real browser power-loss/backend-restart/offline-duration tests, randomized form reconstruction, full tenant policy configuration, live sandbox/provider verification and the complete original/AI acceptance matrices remain unverified or incomplete. Browser tests use synthetic users; component media tests use explicit test doubles. No claim of zero cheating or production readiness is made.

Run backend checks with Java 21, Docker and `mvn -B -ntp -Dapi.version=1.44 verify` from `backend`. Run `npm run build`, `npm test` and `npm run test:e2e` from `frontend`; browser tests require Compose at localhost:8080 and Mailpit at localhost:8025. On this machine Testcontainers used `DOCKER_HOST=unix:///Users/mwgbh/.orbstack/run/docker.sock`; use your own Docker context/socket elsewhere. Preserve `.env` and database volumes.
