# Criterion — technical assessment platform

An implemented **partial vertical slice**, not the complete enterprise platform specified in [prompt.md](prompt.md). Java 21 / Spring Boot, PostgreSQL, React/TypeScript/Vite, Tailwind, TanStack Query, React Hook Form/Zod, Radix Dialog and locally bundled Monaco. No existing source was overwritten.

Working source covers verified organization/candidate registration, staff creation, immutable assessment publication, email invitations, candidate onboarding, choice/code answer persistence, revision-safe retry, tab leases, controlled/strict timers, resume, expiry/submission, deterministic scoring, recruiter results, and asynchronous competency/question draft generation/review. The coding queue now integrates with a separate gVisor gateway; **live sandbox isolation has not been verified** and requires a configured cluster. Post-submission feedback includes checked code citations, independent artifact review, human score corrections, versioned report release, candidate results and CSV export. Mandatory administrative/governance workflows remain unfinished. See [verification](docs/verification.md) before evaluating completeness.

## Local start

Prerequisite: Docker with Compose. Java/Node are only needed to develop/test outside containers.

```sh
cp .env.example .env
# Set DATABASE_PASSWORD to a random local password in .env.
docker compose up --build -d
```

Open http://localhost:8080 . Local email inbox: http://localhost:8025 . Mailpit is development-only and bound to loopback. PostgreSQL is on a private Compose network, with a persistent named volume. `.env` is ignored; there are no seeded login credentials. If `.env` already exists, preserve its generated database password rather than copying over it. In this working session a random local password was generated without printing it.

```sh
docker compose logs --tail=100 backend
docker compose stop
# Keep the named volume to retain accounts, assessments and acknowledged answers.
```

The containers do **not** include a coding sandbox. Never grant the backend a Docker socket or run submitted code with a host shell as a workaround.

## Example workflow

1. Create an organization account in the UI; use a synthetic address such as recruiter@example.test and a password of at least 12 characters.
2. Open Mailpit, copy the single-use verification token, choose Verify email, then sign in.
3. Create an assessment. The builder supplies clearly visible sample Java/SQL/coding content you can edit. Remove the coding item for a fully automatically scored choice-only demo. Publishing pins the configuration and questions.
4. In Candidates, invite candidate@example.test to the published assessment with an expiry within seven days. Open the emailed invitation in another browser profile/context.
5. Create the candidate account using the invited email; verify using Mailpit, sign in, then claim the invitation.
6. Read instructions, run system checks, acknowledge, and start. Select answers and wait for Saved. Refresh; acknowledged answers are restored through Resume. A new tab must wait for the old tab's 30-second lease to expire.
7. Submit. Recruiter Results shows deterministic scores. Coding items remain pending until the isolated gateway returns valid evidence. An administrator/evaluator can record human feedback and recommendations, approve the evaluation, then explicitly release the report. Candidates see only released summaries.
8. In AI studio, generate a competency map and have a different authorized reviewer approve it (create an evaluator in Settings). Choose single-choice, multiple-choice, problem-solving coding, or system-design implementation tasks, difficulty, batch size and language; then generate from the approved map. Review options/keys, rationale and coding evidence. Coding drafts must pass the configured isolated runner validation before independent approval. Select approved questions in “Prepare assessment”, configure duration, passing score and deadlines, and create a draft. Open Assessments to preview and publish separately. Fake mode is explicitly labeled and is not live generation. See [AI question workflow](docs/06-ai-question-generation.md).

## Timer and save contract

Heartbeats every 10 seconds, grace 30 seconds counted as active time. Controlled mode grants at most the configured per-interruption, total-duration and interruption-count allowances; excess outage time consumes active time. Overall completion deadline always applies. Strict mode consumes wall time while disconnected. All accounting is server-side and persisted; browser clocks are display only. A scheduled finalizer handles absent candidates.

Every answer mutation carries an expected attempt revision, browser lease and UUID idempotency key. The server commits answer, history, receipt and audit together before ACK. The browser serializes queued mutations and stores pending requests under the user/attempt key to replay the exact same request after a lost response. Local unsynced data is not an acknowledged answer and cannot be accepted after expiry. Pending local data is removed on successful submission; full privacy/retention controls remain outstanding.

## Development and tests

Java 21, Maven 3.9+, Node 22.12+ and Docker required for the complete test suite.

```sh
cd backend
mvn -B -ntp -Dapi.version=1.44 verify
# Domain-only tests, no Docker:
mvn -B -ntp -Dtest=CoreRulesTest test
```

```sh
cd frontend
npm ci
npm run typecheck
npm run build
npm test
npm audit --audit-level=high
npx playwright install chromium
npm run test:e2e
```

`api.version=1.44` makes the Testcontainers Docker client compatible with the Docker daemon observed during verification. The integration suite intentionally fails instead of silently skipping when Docker is unavailable.

For frontend development use `npm run dev`; its API proxy targets a backend started on port 8081 with DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD, COOKIE_SECURE=false and SMTP_HOST/SMTP_PORT configured. Compose does not expose PostgreSQL; use a separate local developer database or an explicit loopback-only override. Production UI assets are served by nginx with same-origin API routing and a CSP.

## AI modes

The Jobs screen now supports job/skill configuration and draft-assessment linking. AI studio includes persisted blueprint validation and independent review. Candidates can track human-review requests and change optional AI consent; staff resolve requests in Results. See [continuation workflows and API contracts](docs/07-continuation-workflows.md) for migrations, behavior and remaining mandatory limits.

The Question bank screen supports validated JSON imports, immutable revisions, independent approval, archive and approved selection into draft assessments. Candidates includes audited one-time retake authorization after a finalized attempt. Administrators can change staff roles and inspect decision history in Settings. These use additive migrations V13–V15; existing candidate attempts are preserved. Live provider/runner verification still requires approved deployment configuration; local fake and policy tests are not live execution evidence.

- `AI_MODE=disabled`: default backend behavior; manual assessment workflows remain usable.
- `AI_MODE=fake`, `APP_ENV=local`: deterministic labeled development outputs; no provider traffic. Fake mode refuses APP_ENV=production.
- `AI_MODE=openai`: additionally requires AI_MODEL, AI_API_KEY and AI_LIVE_APPROVED=true. Model names/prices are not hardcoded. Never commit the key. The server uses the fixed official HTTPS endpoint with a strict output schema, no tools and no redirects. The adapter has not been live-smoke-tested.

Post-submission feedback requires candidate opt-in and a completed deterministic evaluation. Fake feedback is visibly labeled and remains local. External candidate processing additionally requires `AI_CANDIDATE_PROCESSING_APPROVED=true`; leave it false until the documented organizational/privacy gates are met. Set `RUNNER_URL` to an approved private HTTPS execution gateway and `RUNNER_TOKEN` through secret management; blank values keep execution unavailable. See [sandbox deployment](sandbox/README.md). No expected hidden-test outputs are sent to the runner or model.

Only synthetic nonpersonal authoring inputs should be used in the current live adapter. Before enabling any external processing, approve purpose/data/region/contracts/retention and a monetary budget; current ledger is conservative application quota accounting, **not a complete monetary budget implementation**. [Governance and runbooks](docs/04-governance.md) describe production blockers and a bounded smoke-test procedure: one authoring request, ≤12,000 input characters, ≤2,000 output tokens, at most three transient attempts; verify provider billing separately. No provider call has been made in this implementation session.

## Documentation

- [Candidate journey continuation, contracts and verification limits](docs/05-candidate-continuation.md)

- [Numbered functional requirements](docs/01-requirements.md)
- [Architecture, workflows, transactions and data model](docs/02-architecture.md)
- [Implemented API contracts and missing routes](docs/03-api.md)
- [Governance, threats, benchmarks and runbooks](docs/04-governance.md)
- [Requirement-to-code/test traceability](docs/requirements-traceability.md)
- [Executed verification and outstanding mandatory work](docs/verification.md)
- [Execution security boundary](sandbox/README.md)
- [Flyway migrations](backend/src/main/resources/db/migration)

## Deployment limits

This is a local development build. Remaining mandatory work includes live sandbox validation, reference/mutation testing, complete job/question-bank/blueprint workflows, resource-scoped evaluator permissions, full skill-gap/rubric reporting, appeal resolution, password reset, durable distributed sessions, production throttling, per-tenant provider policies, encrypted artifacts, retention/deletion, audit integrity, operational telemetry/alerts, accessibility QA, benchmark approval and full end-to-end acceptance. Session loss after a backend restart requires login; acknowledged answers and timing remain durable. No production-readiness or legal-compliance claim is made.
