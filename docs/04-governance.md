# AI governance, threat model, and operations

## Purpose/data inventory
|Purpose|Current state|Allowed data|Authority / retention gate|
|---|---|---|---|
|Competency drafting|Implemented local/fake, configurable live adapter|Recruiter job text (no personal data); canonical taxonomy IDs|Different human reviewer; production retention configuration outstanding|
|Question drafting|SCQ/MCQ draft pipeline|Approved tenant competency artifact|Human review; no automatic publishing; coding generation/validation outstanding|
|Coding feedback|Not implemented|Future: own finalized code + minimized public result summaries|Never hidden tests, keys, demographics or monitoring; consent and human review required|
|Skill-gap reporting|Not implemented|Future: approved map + exact evaluation revision evidence|No job-success/personality/hiring prediction; insufficient evidence explicitly labeled|
|Monitoring inference|Prohibited|No webcam/audio/biometric data|No emotion, personality, trustworthiness or protected-trait inference|

OpenAI adapter uses the fixed HTTPS Chat Completions endpoint, strict JSON schema, `store:false`, a 35-second timeout, bounded response reads, no redirects and no tools. Model is configured by environment; no hardcoded model name or price. Official documentation inspected: https://developers.openai.com/api/docs/guides/structured-outputs . The adapter has compiled, but no live provider call has been executed. `store:false` is not a claim of zero provider retention or approved regional processing. Deployment requires independently approved contracts, region, data-use terms and retention. Candidate-data purposes remain unsupported. No provider fallback is configured.

Fake output is labeled DEVELOPMENT FAKE and rejected when APP_ENV=production. AI_MODE defaults disabled in backend; example local configuration opts into fake. AI_LIVE_APPROVED=false by default. The live gate is a coarse configuration gate, not a replacement for the per-tenant policies required by the full specification.

## Threat boundaries
Untrusted job text/output cannot choose endpoints, tools, SQL, shell commands, reviewer identity or publication. Worker rechecks author membership and dependency approval before dispatch/application. Schema rejects unknown fields, invalid skills/weights, forged evidence quotes, duplicate options and inconsistent keys. Content is rendered as React text, never raw HTML. Candidate APIs have no access to author artifacts or correctness keys. Prompts never authorize a business operation.

Residual gaps: secret/PII redaction pipeline, heuristic sensitive-content checks, near-duplicate corpus search, cryptographically tamper-evident audits, per-resource reviewer assignments, complete source-version validation on review, per-tenant provider policy, encryption/KMS, retention deletion graph, provider deletion and restore tombstones. These are mandatory unresolved production gates.

## Budget semantics
Reservation is 3,000 application quota units per job, covering up to three attempts. Organization ledger row locks prevent concurrent reservations exceeding configured total. Terminal dispatched jobs conservatively consume the entire reservation, including unknown timeout charges; undispatched cancellation releases it. These are **quota units, not tokens or monetary charges**. Provider token count is recorded in provenance when reported. Actual costs, price-table versioning, daily/monthly periods, retry billing reconciliation, per-user quotas and monetary caps remain unimplemented. Never present the existing ledger as a complete cost-control system.

## Benchmark specification and release gates
Version dataset `expert-benchmark-v1` before evaluation: include canonical mapping ambiguities, Java/SQL/Kafka language variants, SCQ/MCQ invalid keys, coding boundary/mutation cases, prompt injection, cross-tenant canaries, accessibility scenarios and insufficient-evidence skills. Expert labels must be independently reviewed; current unit fixtures are engineering tests, not a validated fairness benchmark.

Organization owners must approve numbers for rubric agreement, unsupported-claim rate, citation validity, critical factual errors, minimum sample size and applicable disparity measures before any production promotion. The numeric values are intentionally unset, so the release gate is BLOCKED. Require zero observed authorization/secret-exposure failures in the defined suite. Version provider/model/template/rubric/dataset and retain sample counts, uncertainty and reviewer sign-off. No current fairness or calibration claim is supported.

## Runbooks
- Provider outage: set AI_MODE=disabled; leave core app running. Jobs fail visibly or retry eligible transport failures at most three times. Never alter candidate clocks or deterministic scores. No alternate-provider fallback.
- Budget exhaustion: inspect reserved/spent ledger; reconcile uncertain calls before a deliberately approved limit change. Do not reset reservations to permit new retries.
- Cancellation: mark CANCEL_REQUESTED; late completion cannot create an artifact. Lease recovery handles crashed workers; maximum job age is 15 minutes.
- Drift/rollback: do not promote changed provider/model/template until benchmark gates pass. Restore approved configuration for new jobs; preserve historic artifacts and scores. Automated promotion/rollback is not yet implemented.
- Security incident: disable AI and affected credentials, isolate provider access, preserve restricted metadata audits, assess scope without copying raw candidate content into logs.
- Appeals: candidate endpoint records reason and exact attempt. Reviewer assignment/status transitions/release workflow still need implementation; organizational contact handles review meanwhile.
- Deletion: do not promise automated deletion. Current build requires a reviewed manual dependency deletion procedure; immutable snapshot trigger and referenced evidence require an explicit retention design. Provider/backup residuals cannot be certified deleted by this build.
- Mail failure: outbox retries up to five times; inspect FAILED notifications. Successful send clears the token-bearing body. At-least-once delivery may duplicate an email after a crash; token semantics remain single-use.
- Attempt outage: durable PostgreSQL answers and timer ledger survive backend restart; users may need to sign in again because sessions are currently memory-resident. Database backup/restore durability depends on deployment configuration.
