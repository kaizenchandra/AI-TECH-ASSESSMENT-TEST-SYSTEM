# Verification and remaining scope

## Question bank, retakes and staff permissions — 2026-10-30

This is the newest verification entry. Source contracts and explicit limitations are in [continued workflows](07-continuation-workflows.md). Older entries below are historical.

| Check / command | Result | Evidence and limits |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home DOCKER_HOST=unix:///Users/mwgbh/.orbstack/run/docker.sock PATH=/usr/local/bin:/opt/homebrew/bin:$PATH /opt/homebrew/bin/mvn -f backend/pom.xml -B -ntp -Dapi.version=1.44 verify` | Executed / PASS, 53 tests | 23 PostgreSQL integration, 24 domain, 6 execution/schema tests. V1–V15 applied on fresh PostgreSQL. Bank source approval/archive/immutability; independent review; retake idempotency, limits, original submission preservation; role revisions and tenant denials; duplicate integrity observations at quota. |
| `cd frontend && npm run build` (also executed in Compose image build) | Executed / PASS | TypeScript/Vite; existing Monaco size warning remains. |
| `cd frontend && npm test` | Executed / PASS, 11 tests | Save queue, progress and simulated media readiness. |
| `cd frontend && npm run test:e2e` | Initial run: 3 PASS, 1 FAIL | Lifecycle timed out selecting the retake dropdown with an implicit-label locator. Replaced it with its accessible combobox role. Auth tests and question-bank journey passed. |
| `cd frontend && npm run test:e2e -- tests/lifecycle.spec.ts` | Executed / PASS | Full choice journey, offline recovery, result/review/consent and retake UI; original score preserved. |
| `cd frontend && npm run test:e2e -- tests/ai-authoring.spec.ts` | Executed / PASS | Fake AI generation/approval/blueprint plus real bank UI import, independent review, add-to-assessment, revision/archive; administrator role change and nonadmin member-list denial. |
| `node --test sandbox/gateway/policy.test.mjs` | Executed / PASS, 2 tests | Sandbox policy only; no submitted program executed. |
| `docker compose up --build -d` | Executed / PASS | Rebuilt local app and applied additive migrations without deleting volumes. |
| Live AI provider and hardened sandbox execution | BLOCKED / NOT RUN | Local fake mode, live approval false, no configured model/key or runner. No paid request or submitted-code execution attempted. |
| Complete original/AI acceptance matrix, physical media, formal accessibility, expert fairness/drift benchmarks | NOT RUN as a complete matrix | Targeted checks above do not establish AI-AC-01..13 or original 25-step completion. |

Remaining mandatory work includes rich question editing/collections/import idempotency, manual coding reference validation, configurable retake policies and invitation resend/replacement, resource-scoped evaluator permissions, candidate profiles/account recovery, full blueprint constraints, tenant provider/budget/retention administration, encrypted/tamper-evident governance, dependency deletion and backup handling, calibrated benchmark gates and live-provider/sandbox journeys. Role changes and review history do not complete these governance requirements.

## Latest integrated continuation — 2026-10-30

Fresh verification for readiness race recovery, human-review assignment/resolution, versioned optional consent, persisted blueprints and jobs/skills is recorded here. Earlier sections below are historical; their gap descriptions must be read with [current workflows](07-continuation-workflows.md) and the latest traceability appendix.

|Check and exact command|Result|Coverage / limits|
|---|---|---|
|`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home DOCKER_HOST=unix:///Users/mwgbh/.orbstack/run/docker.sock PATH=/usr/local/bin:/opt/homebrew/bin:$PATH /opt/homebrew/bin/mvn -f backend/pom.xml -B -ntp -Dapi.version=1.44 verify`|Executed / PASS: 49 tests|24 domain, 19 PostgreSQL integration, 3 execution evidence, 3 generated-question schema tests. All 12 Flyway migrations applied to fresh PostgreSQL. Use your local Java/Docker paths elsewhere.|
|`cd frontend && npm run build`|Executed / PASS|TypeScript and Vite. Existing large Monaco chunk warning remains.|
|`cd frontend && npm test`|Executed / PASS: 11 tests|Includes generation-scoped media callback and changed-device invalidation; media APIs are simulated.|
|`cd frontend && npm run test:e2e`|Executed / PASS: 4 journeys|Real local API/PostgreSQL/Mailpit; job creation/skill weights/linking and pinned candidate instructions, offline answer visibly unsynced then acknowledged, refresh/resume, score/report release, reviewer assignment/resolution, candidate consent changes; AI independent review and blueprint preparation with labeled fake provider.|
|`node --test sandbox/gateway/policy.test.mjs`|Executed / PASS: 2 tests|Policy validation only; no untrusted code executed.|
|`docker compose up --build -d`|Executed / PASS|Local services rebuilt; additive migrations applied without resetting volumes.|
|Live model, live hardened runner, physical camera/microphone, expert benchmark|NOT RUN|These are not established by fake-provider, policy or simulated-device tests.|

Failures observed and corrected in this increment: published-job context initially used an update rejected by the immutable-version trigger; it now belongs to the original insert. A blueprint browser locator waited for implicit label text; the test now uses the accessible combobox role. Later runs passed. The current source additionally guards nullable JDBC count results; this guard was covered by the final backend rerun.

The candidate journey is improved, **not fully complete**. The full original definition of done and AI-DONE-01..03 remain unfulfilled. Required remaining work includes question-bank CRUD/import/versioning, candidate profiles and account recovery, retakes, complete blueprint difficulty/exposure/reuse/comparability constraints, fine-grained resource permissions, tenant AI policies/real monetary budgets, comprehensive deletion/retention, immutable editable competency history, full fairness/security/operational gates and live coding/provider journeys. New workflow creation endpoints still need generalized transport idempotency. These remain mandatory scope.

## Candidate continuation — 2026-10-30

Backend verification now passes **40 tests** (24 domain, 13 PostgreSQL integration, 3 execution/evidence), with all seven Flyway migrations applied to a fresh PostgreSQL instance. The updated four-test Playwright suite passes, including blue skip → reload/resume → green saved answer → clear, safe category overview, direct start denial before readiness, submission and release. Frontend production build passes. See [candidate continuation](05-candidate-continuation.md) for routes, exact semantics, coverage and remaining acceptance gaps. Physical media-device and live sandbox/provider verification are not implied. Earlier dated counts below describe earlier increments.

Frontend unit/component verification passes **10 tests**: five durable-save tests (including no saved-state notification before acknowledgment), two progress-state tests, and three simulated media permission/cleanup tests (including late permission after unmount). The initial media tests failed because the DOM environment was omitted; adding the explicit jsdom annotation fixed that test configuration. The initial database run was blocked by a stopped Docker daemon; after starting OrbStack, integration tests passed. Local Compose services were rebuilt without removing database volumes.

Executed 2026-10-29. This is a partial implementation, **not** the full requested MVP or production-ready platform. The original 25-step definition of done and AI-DONE-01..03 are not complete.

## Executed checks
|Check|Result|Evidence / environment|
|---|---|---|
|Backend compilation and packaged JAR|PASS|Maven 3.9.11, Temurin Java 21.0.13, Spring Boot 3.5.7; `mvn -DskipTests package`|
|Domain tests|PASS, 24 tests|CoreRulesTest: strict/controlled timers, grace, repeated reconciliation, persisted timer state, pause count/total exhaustion, deadline, clock regression, scoring, candidate key projection, AI evidence/key validation, blueprint constraints|
|PostgreSQL migrations and integration|PASS, 9 tests|PostgreSQL 17.11 in Testcontainers; all five Flyway migrations applied; concurrent start, durable answer/retry/stale conflict, duplicate submit, owner/tab isolation, invitation expiry, finalizer and snapshot immutability, coding success/infrastructure failure, reviewed-score preservation and release idempotency|
|Frontend type checks / production build|PASS|Node 22.21.1, TypeScript 5.9; Vite 7.3.7. Monaco bundle is large; lazy workspace loading added|
|Frontend unit tests|PASS, 4 tests|Vitest 5.0.5; serialized revisions, restored pending requests, no overwrite on conflict, pending-data cleanup guard|
|Frontend dependency audit|PASS|Zero reported vulnerabilities after upgrading vulnerable Vitest tooling; this is not a complete security audit|
|Compose images/services|PASS after fix|Backend/frontend built; PostgreSQL healthy; mail and UI loopback ports verified. Dedicated web network added because internal-only networking did not publish ports on this runtime|
|Browser smoke + choice-only lifecycle|PASS, 3 tests|Playwright: accessible sign-in validation; anonymous candidate API denied; synthetic recruiter registration/email verification/publication/invitation → candidate verification/onboarding → SCQ/MCQ save → refresh/resume → submit → deterministic 100% score → candidate denied staff reports; human review → idempotent release → candidate allowlisted results|
|AI authoring browser/API workflow|PASS, 1 test|Fake-only test covers independent review, idempotent generation, stale review, injection fixture and concurrent quota reservations; live provider and full AI acceptance are not implied|
|Editor diagnostics|PASS at check time|No errors reported for backend/src and frontend/src|
|Live provider|NOT RUN|No credentials or live-provider authorization supplied; adapter compiled and official request format inspected, not live-verified|
|Coding application contract|PASS|ExecutionEvidenceTest (3 tests) plus PostgreSQL integration; weighted output, invalid evidence rejection, exact owned code citations, hidden canary exclusion, consent/tenant checks, pending infrastructure failures|
|Sandbox policy and syntax|PASS|2 Node policy tests; gateway/runtime syntax checks. No candidate code executed on the host|
|Live gVisor execution|NOT RUN|Gateway, restricted manifests and runtime implemented; requires approved image, private HTTPS gateway and verified gVisor/CNI/node policies|

Initial failures were addressed: Docker client API 1.32 rejected by daemon (use 1.44); Vitest erroneously collected Playwright tests (separate config); vulnerable test dependencies upgraded; Compose internal-only network prevented port bindings (dedicated web network). Earlier failed runs do not supersede the later passing checks.

## AI acceptance matrix
A partial supporting test is not a full acceptance pass.
|Criterion|Status|Evidence and remaining checks|
|---|---|---|
|AI-AC-01 Preservation|PARTIAL|Core save/timer path and browser resume pass. Full enabled/disabled/delayed/malformed/outage matrix and coding journey not run|
|AI-AC-02 Mapping|PARTIAL|Canonical IDs, exact source quotes and 100% weight validation tested; editing/version/stale dependency publication not complete|
|AI-AC-03 Questions|PARTIAL|Invalid SCQ keys and duplicate choices rejected in unit tests; coding reference/mutation validation absent|
|AI-AC-04 Blueprints|PARTIAL|Pure selector tests for approval/exclusion/time/seed; no full persisted randomized-form comparability/approval pipeline|
|AI-AC-05 Scoring|PARTIAL|Deterministic choice scoring and no invented coding score tested; exact code evidence references tested; bounded AI rubric judgments not implemented|
|AI-AC-06 Review/versioning|PARTIAL|Browser/API test verified self-review rejection, independent approval and stale revision rejection; immutable artifact revision/regeneration/release lineage incomplete|
|AI-AC-07 Injection/isolation|PARTIAL|Unknown skill/forged evidence rejected; generated text has no execution/approval capability; full worker/API/export/log canary suite outstanding|
|AI-AC-08 Reliability|PARTIAL|Durable leases, bounded retry and cancellation code exists; exhaustive timeout/429/crash/cancel/deletion race tests outstanding|
|AI-AC-09 Budgets|PARTIAL|Concurrent browser/API requests verified one permitted reservation and one 429 at the limit; no monetary costs/price versions/timeout reconciliation/periodic limits|
|AI-AC-10 Privacy|NOT COMPLETE|Candidate DTO allowlist exists; redaction, consent withdrawal and automated deletion/provider/backup tests absent|
|AI-AC-11 Fairness/accessibility|NOT RUN|Basic semantic UI only; no expert benchmark, assistive-technology or demographic-invariance suite|
|AI-AC-12 Release gate|BLOCKED|Numeric quality thresholds, expert labels, sample sizes, organization policy approval and security suite not complete|
|AI-AC-13 Drift/rollback|NOT RUN|No validated model/prompt/rubric promotion and rollback harness|

## Mandatory work still outstanding
- Live validation of isolated runner, reference/mutation validation, language parity, real sample/hidden execution and sandbox escape/resource/network tests. Queue, gateway and deterministic grading contracts are implemented.
- Complete jobs/skills/question-bank CRUD/import/versioning; versioned approved competency maps; persisted constrained blueprints and assigned forms; configurable skill/difficulty weighting, randomized options, retakes and full lifecycle states.
- Complete candidate profiles, verification resend/password recovery, accommodation/manual system-check route, media input level/audio playback, complete history and release-policy controls. Released summary UI is implemented.
- Resource-scoped evaluator assignments, criterion-level rubric overrides, comparability analytics and complete report policy controls. Question score corrections, human recommendations, basic CSV exports and explicit report release are implemented.
- Full AI skill-gap/interview reporting, bounded proposed rubric scores and candidate appeal resolution UI. Opt-in post-submission feedback, exact code citations, independent feedback approval and release UI are implemented.
- Tenant-specific provider/purpose/region policies, secret references, encryption, actual monetary accounting, redaction, retention/deletion and consent revocation workers, immutable artifact version lineage and dependency invalidation.
- Platform administration, distributed rate limits/session storage, structured telemetry/alerts, tamper-evident audit, safe-file pipeline, Kubernetes/cloud manifests, backup/deletion restore procedure and security/load/accessibility benchmarks.
- All original and AI definition-of-done journeys, including backend restart/browser restart/offline interval in real browsers, concurrent security attacks, coding completion and provider-outage isolation.

The gap list is required scope, not a reclassification of mandatory work as optional. Optional adaptive interviews/predictive analytics remain deferred as requested.

## Continuation verification
The latest backend run executes 36 tests (24 domain, 9 PostgreSQL integration, 3 execution/evidence). All four browser tests pass against rebuilt local containers, including report approval and release. Frontend persistence tests: 4 passing. Live providers, live sandbox execution, reference mutation tests, security penetration tests and expert fairness/release benchmarks were not run. Runtime policy tests do not establish sandbox isolation.

Final validation note for V13–V15: the last full Maven verification again passed 53 tests. After adding explicit non-null assertions to the two new bank/retake tests, `mvn -f backend/pom.xml -B -ntp -Dapi.version=1.44 '-Dtest=PersistenceIntegrationTest#retakeIsAuditedIdempotentBoundedAndPreservesTheFinalizedAttempt+bankVersionsRequireIndependentReviewAndArchiveBlocksPublication' test` passed both tests (with the same Java/Docker environment above). The running Compose application was rebuilt from final application source. Editor nullability diagnostics in older integration-test methods remain; Maven compilation and executed tests pass.

## Dark frontend and numbered question palette — 2026-10-30

Shared `frontend/src/theme.css` tokens now cover authentication, recruiter navigation/dashboards, forms, tables, question bank/reports, candidate onboarding and exam surfaces. Violet accents, layered dark panels, visible focus, reduced-motion and forced-colors rules are included. `QuestionTile` displays only its number and small state/review icons; state text stays in accessible names. Green remains based on acknowledged nonempty answers, blue on acknowledged skips, and current/review states are independent. Removed duplicate palette legend and reduced narrow-screen progress spacing.

Verification: VS Code **Frontend checks** task (`npm run build && npm test`) passed, including 16 tests (five new numbered-tile/status/navigation tests). Compose rebuild passed. `cd frontend && npm run test:e2e` passed all four existing journeys with added assertions that saved/skipped tiles contain only their number. Desktop/narrow candidate screenshots were visually inspected; formal accessibility certification was not performed. Existing Monaco bundle-size warning remains. No backend or database behavior changed for this design task.

## Instructions and readiness presentation — 2026-10-30

`AssessmentOverview.tsx` replaces the unstructured briefing with an assessment hero, preparation steps, fact cards, category counts, skill chips, deadline/timer cards, rules and privacy sections. Existing pinned overview data and separate mandatory/optional acknowledgments remain authoritative. `Readiness.tsx` now renders labeled status cards with icons, passed/failed/pending/checking/not-required/exception treatments, required-check totals, specific remediation and retry actions, and styled local media controls. Start/back actions share a responsive footer with readiness guidance; accommodation actions are separated from starting.

Executed: **Frontend checks** task (`npm run build && npm test`) passed 16 tests; denied-camera component coverage now checks accessible Failed/Not required cards and a retry action. A test-library-only type error introduced by using Playwright's `exact` option was corrected before final verification. **Start local assessment platform** task rebuilt successfully. `cd frontend && npm run test:e2e -- tests/lifecycle.spec.ts` passed the full candidate journey, including viewport failure → disabled Start → desktop resize → retry → Passed → authorized start. Instructions and passed/failed readiness screenshots were visually inspected at desktop and narrow widths. Physical camera/microphone verification and a formal accessibility audit were not performed. Backend timing, authorization, saves and migrations were unchanged.

## Complete pre-submission answer review — 2026-10-30

The Finish assessment dialog now lists every assigned question in numbered cards, including all choice options with selected answers highlighted and coding drafts with their selected language and preserved formatting. Saved, unanswered, skipped and unsaved-local states are distinguished; editing follows existing navigation restrictions. The scrollable list keeps counts, authoritative countdown and final submission actions visible. Existing save acknowledgments and finalization APIs remain unchanged; no keys or correctness metadata are added.

Executed: **Frontend checks** (`npm run build && npm test`) passed all 18 tests. Two new component tests cover all 20 fixture questions, multiple selections, exact code rendering, untrusted text, unsaved edits and restricted editing. Compose rebuild passed. `cd frontend && npm run test:e2e -- tests/lifecycle.spec.ts` passed the real candidate journey with review contents, Edit return and final submission assertions. The initial browser run failed due to an undefined screenshot helper in the new test; corrected to `test.info()` and rerun successfully. Desktop and 740px review screenshots were visually inspected. Twenty-question/coding coverage is component-level; the executed browser journey contains two choice questions. No new live sandbox/provider verification or formal accessibility audit was performed.
