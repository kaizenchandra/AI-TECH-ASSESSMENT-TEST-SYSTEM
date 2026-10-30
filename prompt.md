# Implementation prompt: continue the complete assessment platform

Continue implementing this repository's full specification in both frontend and backend. Inspect the current source, repository instructions, migrations, tests, and verification backlog before changing code. Existing documentation and earlier test results are context, not proof that a feature currently works.

Deliver working, integrated code, migrations, automated tests, and execution documentation. Do not stop at a plan, UI mockup, API outline, or rewritten specification. Preserve existing working features and unrelated edits. Close mandatory gaps throughout this document, including the AI MVP; the candidate journey in Section 20 is the next integrated implementation priority, not a reduction of scope.

When this document is explicitly supplied for prompt editing, edit the prompt only. When supplied as an implementation task, execute the implementation instructions. Make routine implementation decisions independently; ask only for information or authorization genuinely needed to proceed. Never invent credentials, live infrastructure, test outcomes, or guarantees of cheating prevention.

Use the role, context, requirements, constraints, examples, and acceptance checks below as a single specification. Existing requirement IDs remain unchanged. Section 20 adds uniquely numbered requirements and clarifies candidate navigation and integrity behavior. Explain material conflicts before resolving them; preserve approved versions, privacy, accessibility, server-authoritative timing, and human decision boundaries.

## 1. Role and Objective

Act as a **Principal Software Architect, Senior Full-Stack Engineer, Product Architect, and QA Automation Lead** with extensive experience building enterprise-grade SaaS applications, online examination platforms, coding assessment systems, and distributed backend services.

Your task is to **design and implement a complete, modern, secure, scalable Online Technical Assessment Platform**, supporting the entire recruitment assessment lifecycle.

The platform must allow HR professionals and recruiters to create assessments, define required skills and experience levels, invite candidates, administer timed technical examinations, evaluate results, and review candidate performance.

Candidates must be able to register, verify their identity, perform system compatibility checks, complete assessments, and resume interrupted tests without losing previously saved answers or permitted remaining time.

**This is an end-to-end implementation task, not merely an architecture proposal.** Produce functional requirements, technical architecture, frontend/backend implementation, database migrations, tests, infrastructure configuration, and execution instructions.

Follow existing repository instructions and conventions when working in an established codebase. Do not overwrite unrelated changes.

---

### AI product scope and precedence

Extend this platform with implementable AI assistance for competency mapping, assessment authoring, coding review, and evidence-based reporting. AI is an assistive subsystem; deterministic business rules, tenant authorization, approved scoring rubrics, and human accountability remain authoritative.

All original requirements remain mandatory. These additions extend their corresponding sections without replacing the recruiter/candidate workflows, single-choice/multiple-choice/coding formats, timer policies, durable saves, resume behavior, or secure execution boundary. Where AI suggestions conflict with an approved assessment version, the approved version prevails. Requirements labeled AI-* are uniquely identified additions; existing identifiers remain unchanged.

Mandatory AI MVP: approved competency mapping, draft question generation and validation, constrained blueprint assembly, post-submission coding feedback, recruiter skill-gap reports, evidence/provenance, human approval, tenant-safe provider integration, budget controls, and AI safety tests. Advanced adaptive testing, conversational interviews, and predictive analytics are optional later capabilities requiring separately approved specifications and validation. Do not introduce live adaptive questions into immutable active attempts.

AI must never autonomously reject, shortlist, hire, publish questions, modify active tests, change a candidate's time allowance, or make final employment decisions. Do not infer personality, emotion, health, disability, protected characteristics, or candidate trustworthiness from answers, voice, webcam, or appearance. Monitoring events must not be treated as proof of cheating.

---

## 2. Preferred Technology Stack

### Frontend
- React with TypeScript
- Next.js or Vite, selecting the most appropriate option
- Tailwind CSS
- A suitable accessible UI component library
- TanStack Query for server-state management
- React Hook Form and Zod for forms and validation
- Monaco Editor for coding assessments
- Responsive design for recruiter dashboards
- Candidate assessment UI optimized for desktop browsers

### Backend
- Java 21
- Spring Boot 3.x or an appropriate currently supported compatible version
- Spring Security with role-based access control
- Spring Data JPA / Hibernate
- PostgreSQL as the primary database
- Redis where justified for short-lived distributed state, caching, or rate limiting
- Kafka only where asynchronous volume or integration requirements justify it
- REST APIs documented with OpenAPI
- Flyway for schema migrations
- Background jobs or workers for email and grading
- Secure isolated code execution service

### Infrastructure
- Docker and Docker Compose for local development
- Kubernetes-compatible deployment configuration where justified
- AWS or GCP compatible infrastructure
- Object storage for optional artifacts and reports
- OpenTelemetry-compatible observability
- CI/CD with automated quality checks

**Architecture preference:** Begin with a well-structured modular monolith for core business capabilities. Introduce separately deployable services only when independently justified. A sandboxed coding execution worker may require a separate security boundary from the primary application.

---

### AI integration stack

AI-ARCH-01: Add an application-owned AI orchestration module with provider-neutral interfaces and server-side adapters for organization-approved managed providers or self-hosted models. Keep orchestration in the modular monolith initially; run durable asynchronous tasks in bounded workers. Candidate clients must never call model providers directly or receive provider credentials.

AI-ARCH-02: Use PostgreSQL for durable AI job state, artifact versions, validation findings, review decisions, policy versions, and usage ledgers. Use an outbox for reliable job dispatch. Redis may support rate limits and short-lived coordination but is not the sole source of truth. Introduce a vector store only if a justified retrieval requirement cannot be met with simpler indexed search; enforce tenant authorization before retrieval and do not share embeddings across tenants.

AI-ARCH-03: Supply configurable adapters, a deterministic local fake for development/tests, and at least one working production adapter. Fake outputs must be visibly labeled and disabled in production. Document supported capabilities, structured-output behavior, provider data handling, deployment region, and configuration without hardcoding volatile model names or prices.

---

## 3. User Roles and Permissions

Implement the following roles.

### 3.1 Platform Administrator
- Manage organizations and tenants.
- Manage recruiter accounts and organization access.
- Configure platform settings, policies, and subscription limits if applicable.
- Review platform-level operational and audit information.
- Manage assessment integrity and security policies.

### 3.2 HR / Recruiter
- Register and authenticate.
- Create and manage job openings.
- Configure skills and experience requirements.
- Create and manage assessments.
- Add, import, and register candidates.
- Invite candidates through email.
- Track candidate invitations and progress.
- Review assessment scores and reports.
- Shortlist or reject candidates.
- Reinvite candidates or authorize retakes.
- Export results.

### 3.3 Evaluator / Hiring Manager
- View assigned candidate submissions.
- Review coding solutions.
- Review automated scoring and evaluation details.
- Add feedback and manual scores.
- Approve final evaluation results.

### 3.4 Candidate
- Sign up using a valid assessment invitation.
- Verify email ownership.
- Log in securely.
- Review assigned assessments.
- Read and acknowledge instructions.
- Perform browser, webcam, microphone, and network checks.
- Start an authorized assessment.
- Answer single-choice, multiple-choice, and coding questions.
- Navigate questions according to assessment rules.
- Review answered and unanswered questions.
- Resume an interrupted assessment when permitted.
- Submit the assessment.
- View submission confirmation and permitted results.

Enforce organization-level isolation and resource-level authorization on the backend.

---

### AI permissions and separation of duties

AI-RBAC-01: Define resource-scoped permissions for competency drafting/approval, question generation/review/publication, blueprint approval, coding feedback review, report release, provider administration, AI audit access, and retention administration. Enforce permissions and tenant membership on every API, worker, artifact lookup, and export.

AI-RBAC-02: Recruiters may request drafts and approve content only where granted authoring rights. Evaluators may inspect evidence, accept/edit/reject AI feedback, and record rubric-based overrides. Candidates may access disclosures, authorized released feedback, correction/appeal requests, and their own privacy controls; they cannot access confidential prompts, answer keys, hidden tests, other candidates, or unreleased reports.

AI-RBAC-03: Organization administrators configure allowed AI purposes, providers, budgets, retention, and reviewer roles. For high-stakes publication, default to a different authorized reviewer from the draft author; any organization-permitted exception requires a reason and audit record. AI service identities cannot grant permissions or approve their own artifacts.

---

# 4. Complete End-to-End Business Workflow

## Workflow A: Recruiter Onboarding

1. Recruiter registers or receives an administrator-created account.
2. Recruiter verifies email and signs in.
3. Recruiter creates an organization profile when permitted.
4. Recruiter configures organization assessment preferences.
5. Recruiter accesses the dashboard.

The dashboard must display:
- Total assessments
- Active assessments
- Invited candidates
- Registered candidates
- In-progress tests
- Completed tests
- Expired invitations
- Pending evaluations
- Candidate score summaries

## Workflow B: Job Role and Skill Configuration

Recruiter creates a job opening with:
- Job title
- Department
- Job description
- Experience level
- Minimum and maximum years of experience
- Required skills
- Optional skills
- Skill proficiency requirements
- Assessment difficulty
- Assessment duration
- Minimum passing score
- Assessment availability window

Example:

Job Role: Senior Java Backend Engineer

Experience: 7–10 years

Skills:
- Core Java: 20%
- Spring Boot: 20%
- Microservices: 15%
- Kafka: 15%
- PostgreSQL: 10%
- System Design: 10%
- Coding / DSA: 10%

The assessment engine must support configurable weighting by skill, difficulty, and question type.

## Workflow C: Assessment Creation

Recruiter creates an assessment manually or from an existing template.

Required assessment properties:
- Assessment name
- Job association
- Description
- Instructions
- Skills covered
- Experience level
- Difficulty level
- Total questions
- Question-type distribution
- Duration in minutes
- Maximum attempts
- Passing criteria
- Start availability
- Invitation expiration
- Latest permitted start
- Assessment completion policy
- Negative marking configuration
- Question randomization
- Answer-option randomization
- Back-navigation rules
- Interruption/resume policy
- Webcam/microphone prerequisites
- Evaluation settings

Assessment question types:

**A. Single-choice questions**
- Exactly one correct option.
- Candidate selects only one option.
- Changing the selection replaces the previous answer.

**B. Multiple-choice questions**
- One or more correct options.
- Candidate selects multiple options.
- Partial credit and penalties are configurable.

**C. Coding questions**
- Problem description
- Constraints
- Input/output examples
- Supported programming languages
- Starter code
- Public sample tests
- Hidden evaluation tests
- Execution timeout
- Memory limit
- Weighted scoring
- Optional evaluator review

Allow recruiters to preview and publish an assessment.

Published assessments must use immutable question/rubric versions for assigned attempts, preventing later edits from silently modifying an ongoing test.

## Workflow D: Question Bank Management

Implement a reusable question bank.

Question metadata:
- Question ID
- Question type
- Title
- Description
- Skill
- Topic
- Subtopic
- Difficulty
- Experience-level suitability
- Answer options
- Correct answer(s)
- Explanation
- Score
- Estimated solving time
- Tags
- Active/inactive status
- Version

Recruiters must be able to:
- Create questions.
- Edit draft questions.
- Archive questions.
- Search and filter questions.
- Preview questions.
- Organize questions into collections.
- Import questions using a validated supported format.
- Select questions manually or through blueprint-based random selection.

Ensure candidates never receive correct answers, hidden tests, or confidential grading metadata before results are authorized for release.

## Workflow E: Candidate Registration and Invitations

Support two registration approaches.

### E1. Recruiter-initiated registration
1. Recruiter enters candidate details:
   - Full name
   - Email address
   - Phone number (optional)
   - Experience
   - Applied position
   - Relevant skills
2. Recruiter selects assessment.
3. Recruiter specifies the validity window.
4. System creates an invitation.
5. System sends an email with a unique secure assessment link.

### E2. Candidate self-registration
1. Candidate opens a valid recruiter-issued invitation link.
2. System validates invitation eligibility.
3. Candidate registers or signs in.
4. Candidate verifies email ownership using an OTP or verification link.
5. Candidate completes required profile information.
6. System associates the verified account with the invitation.
7. Candidate proceeds to assessment onboarding.

Optionally support recruiter-configured public registration links with eligibility checks, invitation quotas, and anti-abuse controls.

### Invitation email

Email must contain:
- Candidate name
- Company name
- Job role
- Assessment title
- Assessment duration
- Assessment availability window
- Expiration date and timezone
- High-level system requirements
- Secure start link
- Support/contact instructions

Invitation links must be securely generated, expiring, revocable, and scoped to the intended candidate and assessment.

Do not place reusable authentication credentials inside invitation URLs.

---

## Workflow F: AI-Assisted Competency and Assessment Authoring

AI-COMP-01: Accept a recruiter-provided job description, explicit required/optional skills, experience band, and role context. Treat uploaded files and job text as untrusted source data. Extract a draft mapping to a versioned skills taxonomy: canonical skill IDs, aliases, proficiency descriptors, observable competencies, proposed weights, and source text references.

AI-COMP-02: For each mapping, display supporting job-description spans, assumptions, missing information, duplicate skills, and uncertain matches. Distinguish explicitly requested skills from inferred suggestions. Do not use years of experience alone as proof of proficiency. Ask the recruiter to resolve ambiguous or conflicting requirements.

AI-COMP-03: Let recruiters edit, accept, or reject mappings and weights. Validate required coverage, proficiency scales, and a total weight of 100% before approval. Persist a new competency-map version after substantive changes; dependent drafts become stale and require revalidation. Approval pins the version used by a blueprint.

AI-QGEN-01: Generate question drafts from approved skills, competency level, difficulty, language/locale, question type, target solving time, and permitted source material. Allow batch size and generation budget limits. Record source references and content rights declarations where supplied; never assume provider output is accurate or free of duplication.

AI-QGEN-02: A single-choice draft must contain exactly one keyed correct option and a rationale explaining each distractor. A multiple-choice draft must contain the declared number of correct options, explicit selection instructions, and an executable scoring rule. A coding draft must contain constraints, examples, approved languages, starter code, a reference solution, public/hidden tests, resource limits, and rubric dimensions with weights.

AI-QVAL-01: Validate structured schemas, skill references, answer-key consistency, option uniqueness, unambiguous wording, numerical limits, accessibility, source attribution, and prohibited/sensitive content. Identify exact and near duplicates within the authorized tenant corpus and detect substantially overlapping variants. Distinguish objective failures from heuristic/model warnings. Model self-review is supplementary and cannot alone certify correctness.

AI-QVAL-02: Compile and execute coding reference solutions in the hardened sandbox across supported languages and all tests; verify examples and expected results, boundary cases, determinism, timeout feasibility, and language parity. Include deliberately incorrect/mutated solutions to detect weak tests. Report coverage limitations; do not represent hidden-test passage as a formal proof. Failed correctness checks block approval.

AI-QVAL-03: Reviewers see the full question, key, explanations, tests, validation findings, source/provenance, and proposed difficulty/time estimates. Difficulty remains a labeled estimate until calibrated with sufficient approved evidence. AI drafts follow DRAFT → VALIDATING → REVIEW_REQUIRED → APPROVED or REJECTED; technical failures use VALIDATION_FAILED. Approval is separate from assessment publication. Editing content creates a new version and invalidates prior validation/approval.

AI-BLUE-01: Assemble a proposed blueprint from approved question versions using explicit constraints: competency coverage and weights, question counts and types, difficulty distribution, estimated total time including review allowance, language availability, accessibility, exclusions, prior exposure, and item reuse limits. Use a deterministic constraint validator/selector; AI may propose explanations or candidate sets but cannot bypass constraints.

AI-BLUE-02: Show requested versus achieved coverage and every unmet constraint. If the bank is insufficient, return a diagnosable infeasible result with draft-generation/manual-selection options; never silently relax requirements or include unapproved drafts. Recruiter changes to constraints require explicit approval. Pin item, rubric, competency map, blueprint, selection algorithm, and randomization seed versions before publication.

AI-BLUE-03: For randomized forms, validate each assigned form against blueprint constraints and comparability tolerances established by the organization. Do not claim equivalence from matching counts alone. Persist the actual assigned form for exact reconstruction. Human approval must precede publication and candidate assignment.

---

# 5. Candidate Assessment Workflow

## Stage 1: Invitation Validation

When a candidate opens the assessment link:

1. Verify invitation validity.
2. Check whether it is expired or revoked.
3. Verify candidate identity.
4. Check attempt eligibility.
5. Check assessment availability.
6. Resolve whether the candidate should register, sign in, start, resume, or view completion status.

Handle invalid and expired links with meaningful error messages.

## Stage 2: Signup and Authentication

Candidate signs up or logs in.

Requirements:
- Secure account creation.
- Email ownership verification.
- Appropriate password or passwordless authentication.
- Duplicate account detection.
- Secure session management.
- Authorized invitation association.

## Stage 3: Instructions Screen

Display:
- Assessment title
- Total duration
- Number of questions
- Question types
- Marking rules
- Navigation rules
- Coding language support
- Resume policy
- Assessment deadline
- Submission rules
- Browser/system requirements
- Privacy and monitoring disclosures

Candidate must explicitly accept the applicable instructions and assessment rules before proceeding.

## Stage 4: System Compatibility Checks

Implement an interactive pre-assessment screen.

Check:
- Browser support
- JavaScript support
- Cookies/session requirements
- Screen/viewport compatibility
- Network connectivity
- Webcam availability
- Webcam permissions
- Microphone availability
- Microphone permissions
- Microphone input level
- Audio playback where required

Display individual pass/fail statuses and remediation guidance.

Camera and microphone access must be permission-based. Do not secretly capture, record, or stream media.

If proctoring is enabled, obtain explicit consent, disclose retained data, and apply organization privacy policies. Support authorized accommodations and a manual review path for unsupported devices.

## Stage 5: Assessment Start

When the candidate clicks **Start Assessment**:

1. Backend verifies eligibility and prerequisites.
2. Backend atomically creates or activates the attempt.
3. Backend records authoritative start time and time budget.
4. Candidate receives only authorized question data.
5. Frontend opens the assessment workspace.
6. Timer starts according to the server-side time policy.

Starting the same attempt concurrently must not create duplicate active attempts.

---

## Stage 6: AI Transparency and Candidate Controls

AI-CAND-01: Before the test, explain which AI purposes apply, which data is processed externally, whether AI contributes feedback or proposed rubric judgments, who reviews it, and how candidates request human review or accommodations. Show organization contact details and retention information in plain language. Version disclosures and record acknowledgment; record separate explicit consent for optional purposes where required by the organization's applicable policy.

AI-CAND-02: Keep core test access independent of optional AI features. Provide an authorized human-review alternative when optional AI processing is declined or an accommodation requires it. Do not silently fall back to another provider with different data-use terms. Required processing must have a documented organizational basis and an escalation route before the candidate starts.

AI-CAND-03: Default assessment mode prohibits answer assistance, AI hints, generated solutions, or live coaching. If an organization offers a separate AI-assisted assessment mode, disclose and pin permitted assistance, tool access, grading criteria, and assistance logs in the assessment version. Compare results only within compatible modes. No AI grading call may block start, autosave, resume, or final submission.

---

# 6. Assessment Workspace — Frontend Functional Requirements

Implement a professional assessment interface.

## FR-FE-01: Assessment Header
- Assessment title
- Candidate progress
- Remaining time
- Connection status
- Save status
- Finish assessment action

## FR-FE-02: Question Navigation
- Previous/next question
- Question palette
- Jump to question when allowed
- Mark for review
- Answered indicator
- Unanswered indicator
- Flagged indicator
- Current question indicator

## FR-FE-03: Single-Choice Questions
- Radio-button selection
- Immediate local UI update
- Reliable server-side persistence
- Restore selection after reload or reconnection

## FR-FE-04: Multiple-Choice Questions
- Checkbox selection
- Multiple selections supported
- Consistent saving and restoration
- Validation according to the question rules

## FR-FE-05: Coding Questions
- Monaco code editor
- Language selection
- Starter code
- Syntax highlighting
- Code formatting where supported
- Run code against allowed sample tests
- Display execution results
- Display compile/runtime errors
- Save code automatically
- Restore saved code after interruption
- Submit final code for evaluation

## FR-FE-06: Automatic Answer Saving

Persist:
- Selected answer option(s)
- Coding language
- Coding source code
- Current question
- Mark-for-review state
- Question completion state
- Last acknowledged save version

Show clear statuses:
- Saving
- Saved
- Offline / unsynced
- Save failed

Use retries with bounded backoff, but ensure retries cannot overwrite newer answers with stale versions.

## FR-FE-07: Timer

Display remaining time accurately.

Requirements:
- Server-authoritative timekeeping
- Visible countdown
- Warning thresholds
- Correct behavior across page refresh
- Correct behavior after reconnection
- Correct behavior across browser sleep/backgrounding
- Auto-submit at the configured time limit
- Explicit finalization behavior

Never trust a browser-only timer for assessment enforcement.

## FR-FE-08: Assessment Summary
- Total questions
- Answered
- Unanswered
- Marked for review
- Remaining time
- Unsaved answer warnings

## FR-FE-09: Submission
- Submit confirmation
- Warn about unanswered questions
- Handle unsaved changes
- Prevent duplicate submissions
- Show confirmation after backend acknowledgment

## FR-FE-10: Candidate Experience
- Responsive, clean interface
- Accessible form controls and keyboard navigation
- Readable typography
- Clear error messages
- Loading and empty states
- Reconnection feedback
- Browser refresh recovery
- Session-expiration handling

---

## FR-FE-11: AI Authoring and Review Experiences
- Editable competency-map view with evidence, weights, version comparison, and approval status.
- Question-generation form with constraints, estimated usage, asynchronous progress, cancel/retry, and partial-batch results.
- Validation findings grouped as blocking errors, warnings, and reviewer decisions.
- Side-by-side draft/revision review with accessible source links and clear AI-generated labels.
- Blueprint coverage matrix, infeasibility explanations, and explicit approval/publication controls.
- Hide unauthorized keys and hidden tests in candidate previews and candidate API payloads.

## FR-FE-12: AI Results and Accessibility
- Separate verified test scores, proposed AI judgments, and human-approved feedback visually and semantically.
- Show pending/unavailable AI states without hiding deterministic scores or submission confirmation.
- Provide evidence links, rubric criteria, limitations, and human-review actions in authorized reports.
- Support keyboard-only operation, screen-reader labels, live-status announcements without repeated interruption, adequate contrast, zoom/reflow, and accessible alternatives to visual charts.
- Render generated text as sanitized content; never execute provider-generated scripts, markup, commands, or links as trusted instructions.

---

# 7. Critical Requirement: Interruption, Auto-Save, and Resume

This is one of the platform's highest-priority requirements.

The candidate may experience:
- Internet disconnection
- Browser crash
- Accidental tab closure
- Computer restart
- Page refresh
- Temporary power failure
- Authentication session expiry

Previously acknowledged answers must never disappear because of these events.

## Resume behavior

Example:

- Assessment duration: 60 minutes
- Candidate answers 15 questions
- Candidate consumes 25 active assessment minutes
- Network disconnects
- Candidate returns within the permitted interruption window
- Candidate resumes from the last saved question
- Previously saved selections and code are restored
- Remaining time: 35 minutes, subject to the configured pause policy

## Timer Policy

Implement an explicit, configurable interruption policy.

**Policy A: Strict elapsed-time mode**
- Countdown continues during disconnection.
- Candidate may resume only before expiration.

**Policy B: Controlled pause-and-resume mode**
- Authoritative consumed active time is persisted.
- Eligible interruptions may pause the timer.
- The pause is bounded by a configurable maximum duration and count.
- The platform maintains a maximum overall completion deadline.
- Exhausted pause allowance follows a documented expiration rule.

Use Policy B as the initial product default to support the requested continuation behavior, while preventing unlimited attempts to extend exam duration by intentionally disconnecting.

The system must carefully distinguish last acknowledged activity, heartbeat loss, verified reconnection, and granted pause time. Clearly define any detection grace period and how it counts toward the timer.

Do not permit clients to declare arbitrary pause durations.

## Backend Requirements

Persist at minimum:
- Attempt ID
- Attempt status
- Candidate ID
- Assessment version
- Started timestamp
- Last activity timestamp
- Consumed active time
- Remaining time or authoritative timing ledger
- Pause start/end
- Granted pause duration
- Pause count
- Maximum completion deadline
- Current question ID
- Answer revision/version
- Last save timestamp
- Submission timestamp

Implement:
- Idempotent answer-saving APIs
- Optimistic concurrency or revision checks
- Atomic lifecycle transitions
- Server-side deadline enforcement
- Heartbeat/reconnection handling
- Resume authorization
- Multi-tab and concurrent-session protection
- Recovery after backend restart
- Automatic finalization when the attempt expires

A background worker or equivalent reliable mechanism should finalize expired attempts even if the candidate never reconnects.

Do not assume an answer was persisted unless the backend acknowledged it.

Provide specific automated tests for:
- Refresh after answer selection
- Browser restart
- Reconnection
- Stale answer-save request
- Duplicate submit
- Multiple active tabs
- Timer expiry
- Pause limit exhaustion
- Backend restart
- Concurrent candidate actions

---

## AI isolation from timing and persistence

AI-TIME-01: AI service latency, retries, cancellation, provider outages, and budget exhaustion must not change authoritative attempt time, consume a candidate pause allowance, or block durable answer acknowledgments. Keep AI jobs off the synchronous save/heartbeat/submit transaction path.

AI-TIME-02: Post-submission AI jobs reference the immutable final submission revision. Late output from an older submission/evaluation version is stored as superseded and cannot replace current results. AI failures leave attempts submitted with separate enrichment/review status; never reopen answer mutation or auto-expire a submitted attempt because enrichment failed.

---

# 8. Backend Functional Requirements

## FR-BE-01: Identity and Access
- Recruiter authentication
- Candidate authentication
- Email verification
- Role-based authorization
- Organization isolation
- Secure token/session handling
- Authentication audit records

## FR-BE-02: Recruiter Management
- Recruiter CRUD where authorized
- Organization membership
- Dashboard aggregates
- Recruiter-level access restrictions

## FR-BE-03: Candidate Management
- Candidate registration
- Profile updates
- Candidate deduplication
- Invitation association
- Assessment history
- Status tracking

## FR-BE-04: Skills and Jobs
- Skills catalog
- Role definitions
- Experience requirements
- Difficulty and proficiency levels
- Skill weighting
- Job-to-assessment mapping

## FR-BE-05: Question Bank
- Question CRUD
- Question versioning
- Skill categorization
- Difficulty mapping
- Correct answer storage
- Coding test-case management
- Question randomization
- Search, filtering, and import

## FR-BE-06: Assessment Management
- Assessment draft creation
- Question selection
- Assessment publishing
- Immutable published versions
- Assessment scheduling
- Assessment duplication
- Assessment deactivation/archive
- Passing thresholds
- Retake policy

## FR-BE-07: Invitation Management
- Secure invitation generation
- Email delivery
- Email delivery status
- Invitation resend
- Invitation expiry
- Invitation revocation
- Invitation claim validation

## FR-BE-08: Attempt Management
- Eligibility validation
- Atomic attempt creation
- Attempt lifecycle
- Timer tracking
- Pause/resume management
- Current-question tracking
- Session conflict handling
- Automatic expiry/finalization

## FR-BE-09: Answer Management
- Save single-choice answers
- Save multiple-choice answers
- Save coding answers
- Restore saved answers
- Track save revisions
- Reject changes after finalization
- Handle duplicate and out-of-order requests

## FR-BE-10: Coding Execution

Design a separate secure code execution boundary.

Capabilities:
- Compile and run supported languages
- Execute allowed sample tests
- Execute hidden grading tests
- Enforce CPU limits
- Enforce memory limits
- Enforce runtime limits
- Bound output size
- Capture execution errors
- Report execution status
- Queue executions when necessary
- Handle infrastructure failures and retries

**Security is mandatory.** Untrusted candidate code must never execute directly inside the Spring Boot application process or on unrestricted application infrastructure.

Use appropriately hardened isolated execution environments with restricted filesystem access, blocked network egress by default, no application credentials, and strict resource limits.

## FR-BE-11: Evaluation Engine
- Automatic single-choice evaluation
- Multiple-choice exact and partial-credit evaluation
- Coding evaluation against hidden tests
- Per-question scores
- Per-skill scores
- Weighted totals
- Configurable negative marking
- Passing score determination
- Pending manual-review status
- Evaluator override with audit history

## FR-BE-12: Reports
- Candidate assessment summary
- Per-skill performance
- Question-level analysis
- Coding results
- Total marks
- Percentage score
- Passing status
- Completion duration
- Assessment timeline
- Evaluation status
- Recruiter notes
- CSV export
- Optional PDF export

## FR-BE-13: Notifications
- Invitation email
- Verification email
- Invitation reminder
- Assessment started
- Assessment submitted
- Assessment expired
- Evaluation completed
- Recruiter notification

Ensure notification failures do not roll back successfully committed business transactions.

## FR-BE-14: Audit and Integrity
- Invitation creation/revocation
- Assessment publication
- Attempt start
- Answer-save acknowledgments
- Pause/resume events
- Submission
- Grading
- Manual score changes
- Relevant session/security events

Preserve timestamps, actor identity, and traceability.

---

## FR-BE-15: AI Job Orchestration

AI-JOB-01: Persist purpose, tenant, authorized requester, source/version references, policy snapshot, idempotency key, payload hash, estimated budget reservation, deadline, attempts, status, and result references before dispatch. Use lifecycle QUEUED → RUNNING → SUCCEEDED, with RETRY_WAIT, FAILED, CANCEL_REQUESTED, CANCELLED, and EXPIRED paths. Define worker lease recovery, duplicate delivery, retry exhaustion, and cancellation races. Completion can store a draft only; it cannot imply human approval.

AI-JOB-02: Use purpose-specific typed request/output schemas, finite output sizes, schema validation, and semantic business validation. Unknown fields, invalid skill IDs, missing evidence, or malformed scores fail closed into review/failure states. Retain safe partial results as individual draft artifacts; clearly report which batch items failed. Never silently repair consequential output into approval.

AI-JOB-03: Revalidate permissions and policy before provider dispatch and before applying results. Workers must not rely on a client-supplied tenant ID. Define cancellation, deletion, and revoked-access handling for in-flight work. Provider calls happen outside database locks; use transactional outbox and conditional result writes to prevent orphaned or stale updates.

## FR-BE-16: Coding Evaluation and Feedback

AI-CODE-01: Compute execution correctness, test-case weights, compilation failures, and resource-limit results deterministically from sandbox evidence. Distinguish infrastructure failure from incorrect candidate code; retry infrastructure failures under policy without assigning a false zero. AI feedback must not overwrite these facts.

AI-CODE-02: After submission, produce draft feedback on correctness evidence, edge cases, maintainability, readability, and complexity against an approved rubric. Each finding cites authorized code line ranges and relevant test/rubric IDs. Complexity claims must identify assumptions; unsupported claims are labeled uncertain. Provide concise actionable feedback appropriate to the configured release policy.

AI-CODE-03: Optional rubric dimensions needing judgment may receive bounded proposed scores, but require authorized evaluator approval before inclusion in final totals. Validate score ranges, missing criteria, rubric weights, and evidence references server-side. Preserve baseline deterministic score, proposed judgment, human decision/reason, and resulting final score separately.

AI-CODE-04: Never send hidden-test source, confidential expected outputs, secrets, or another candidate's code to a model by default. Provide minimized test summaries and candidate-owned code through an approved processing policy. Hidden-test diagnostics remain confidential in candidate feedback. Run proposed reference code or test snippets only through the sandbox after validation, never as provider-directed shell commands.

## FR-BE-17: Recruiter Insights, Skill Gaps, and Interview Support

AI-REPORT-01: Produce a draft scorecard based only on the candidate's authorized assessment evidence and approved competency map. For each skill show target proficiency, observed criterion-level evidence, score denominator/coverage, gaps, strengths, and uncertainty. Mark skills with insufficient evidence as not assessed; never equate absence of evidence with failure.

AI-REPORT-02: Distinguish measured task performance from estimated proficiency. Do not assert job success, personality, cheating, or hiring suitability from an unvalidated model prediction. Draft interview follow-ups must reference observed gaps and job-relevant criteria, avoiding private attributes and protected-class proxies.

AI-REPORT-03: Candidate comparisons require compatible assessment/form, rubric, assistance mode, and accommodation policies. Display comparability limitations and do not generate cross-candidate rankings from noncomparable results. Exclude names, photographs, contact details, and irrelevant demographics from model inputs wherever possible.

AI-REPORT-04: Recruiters/evaluators can edit, accept, reject, or request regeneration with a reason. Released feedback requires the configured approval. Offer candidate correction/appeal intake, reviewer assignment, status, and a documented response process. Score corrections create new evaluation versions and retain a restricted audit history. Never expose confidential question-bank material through exported reports.

## FR-BE-18: Explainability and Governance

AI-EXP-01: Each AI artifact stores purpose, input reference/version and hash, approved source citations, prompt-template version/hash, provider/model identifier and available revision, inference settings, policy version, validator versions/results, generation time, usage/cost, review decisions, and resulting artifact version. Record provider request IDs when available without logging secrets.

AI-EXP-02: Present concise evidence-based reasons and known limitations, not hidden chain-of-thought. Validate citations against authorized immutable sources. A model's self-reported confidence is not calibrated certainty; use evidence coverage and benchmark-derived reliability where available, otherwise display uncertainty explicitly.

AI-EXP-03: Reconstruct decision lineage through retained artifacts and versioned inputs. External model output may not be bitwise reproducible; do not promise deterministic regeneration. Record manual edits, overrides, policy changes, failed jobs, provider routing, approvals, releases, and deletion events in tamper-evident, access-controlled audit records. Retention constraints apply to audit payloads too.

---

# 9. Assessment Lifecycle and State Machine

Define explicit states with controlled transitions.

### Invitation
DRAFT → SENT → OPENED → CLAIMED

Alternative terminal states: EXPIRED, REVOKED.

### Assessment
DRAFT → PUBLISHED → ACTIVE → CLOSED → ARCHIVED

### Candidate Attempt
NOT_STARTED → INSTRUCTIONS → SYSTEM_CHECK → READY → IN_PROGRESS

From IN_PROGRESS:
- PAUSED → IN_PROGRESS
- SUBMITTED
- EXPIRED

After submission:
SUBMITTED → EVALUATING → EVALUATED

Support EVALUATION_REVIEW_REQUIRED where applicable.

Define legal transitions, permissions, failure handling, retry behavior, and concurrency constraints.

Submission and expiration must be terminal with respect to answer mutation.

---

### Independent AI artifact and review state

AI-STATE-01: Keep job, validation, artifact approval, report release, and assessment attempt state machines separate. A completed generation job is not an approved question or evaluated candidate. Define approval guards requiring passing blocking checks, current dependency versions, permitted reviewer, and expected revision.

AI-STATE-02: Reports progress DRAFT → REVIEW_REQUIRED → APPROVED → RELEASED, with REJECTED and SUPERSEDED paths. Revocation of an approved question stops future publication/assignment; preserve ongoing attempt snapshots and route affected completed evaluations through an explicit impact-review/regrade process. Never silently rewrite historical results. Regeneration creates a sibling/new draft, preserving earlier reviewer decisions and lineage.

---

# 10. Database Design

Design a normalized PostgreSQL schema covering the necessary entities.

Expected entities include:
- organizations
- users
- roles
- organization_members
- candidates
- jobs
- skills
- job_skills
- question_bank
- question_versions
- question_options
- coding_test_cases
- assessments
- assessment_versions
- assessment_questions
- invitations
- candidate_assessment_assignments
- assessment_attempts
- attempt_timer_events
- candidate_answers
- answer_revisions
- coding_submissions
- code_execution_jobs
- code_execution_results
- evaluation_results
- skill_scores
- notifications
- audit_events

For every table, specify:
- Primary keys
- Foreign keys
- Relevant unique constraints
- Indexes
- Data types
- Lifecycle timestamps
- Versioning/concurrency fields
- Retention requirements where necessary

Prevent duplicate active attempts and duplicate final submissions using appropriate transactional guarantees and database constraints.

Use Flyway migrations and demonstrate transaction boundaries.

---

### Additional AI persistence model

AI-DB-01: Extend migrations with normalized entities (or justified equivalents):
- skill_taxonomy_versions, competency_maps, competency_map_versions, competency_items, competency_evidence
- assessment_blueprints, blueprint_versions, blueprint_constraints, assigned_forms
- ai_policies, ai_policy_versions, ai_provider_configs, ai_prompt_templates, ai_prompt_template_versions
- ai_jobs, ai_job_attempts, ai_artifacts, ai_artifact_versions, ai_artifact_dependencies
- ai_validation_runs, ai_validation_findings, ai_review_decisions, ai_evidence_references
- ai_usage_ledger, ai_budget_reservations, ai_release_decisions
- ai_feedback, evaluation_versions, rubric_versions, rubric_criteria, candidate_review_requests
- disclosure_versions, candidate_acknowledgments, purpose_consents, retention_policies, deletion_requests

AI-DB-02: Carry tenant ownership on tenant resources and enforce tenant-safe foreign-key relationships and query scopes. Use immutable version rows for approved content; optimistic revision guards for drafts; unique scoped idempotency keys and provider-response deduplication; foreign keys binding feedback to the exact final answer/evaluation version. Model consent revocation, soft deletion where justified, and scheduled hard deletion separately.

AI-DB-03: Store approved secret references instead of plaintext provider keys. Separate confidential keys/hidden tests from generated candidate feedback. Encrypt sensitive payloads/artifacts and apply purpose-specific retention. Prefer source references and hashes over duplicated PII; hashes are not a substitute for deletion where personal data remains linkable. Audit deletion without retaining the deleted content. Define which bounded aggregate usage records remain after deletion.

---

# 11. API Design

Design versioned REST endpoints for:

- Authentication
- Recruiter management
- Candidate management
- Jobs and skills
- Question bank
- Assessment management
- Assessment publishing
- Invitations
- Candidate onboarding
- System-check acknowledgment
- Assessment starting
- Question retrieval
- Answer saving
- Coding execution
- Heartbeats
- Pause/resume
- Progress retrieval
- Assessment submission
- Evaluation
- Reporting

For each API provide:
- HTTP method and endpoint
- Required authorization
- Request DTO
- Response DTO
- Validation rules
- Success/error statuses
- Pagination when relevant
- Idempotency behavior
- Concurrency rules

Use consistent error responses, preferably Problem Details.

Never expose grading secrets through candidate-facing APIs.

---

### AI API contracts

AI-API-01: Implement these versioned routes under /api/v1 with the same DTO, authorization, error, idempotency, and concurrency documentation required above:
- POST /jobs/{jobId}/competency-map-generations; GET/PATCH /competency-maps/{id}/draft; POST /competency-map-versions/{id}/review-decisions
- POST /question-generations; POST /question-versions/{id}/validations; GET /question-versions/{id}/validation-findings; POST /question-versions/{id}/review-decisions
- POST /assessments/{id}/blueprint-generations; GET/PATCH /blueprints/{id}/draft; POST /blueprint-versions/{id}/validations; POST /blueprint-versions/{id}/review-decisions
- GET /ai-jobs/{id}; POST /ai-jobs/{id}/cancel; POST /ai-jobs/{id}/retry
- POST /coding-submissions/{id}/ai-feedback-jobs; POST /evaluations/{id}/insight-jobs
- GET /ai-artifacts/{id}/versions; GET /ai-artifact-versions/{id}/evidence; POST /ai-artifact-versions/{id}/review-decisions
- POST /reports/{id}/release; POST /evaluations/{id}/review-requests
- GET/PATCH /organizations/{id}/ai-policy; GET /organizations/{id}/ai-usage
- GET /candidate/ai-disclosures; POST /candidate/purpose-consents; POST /candidate/privacy-requests

AI-API-02: Asynchronous creation returns 202 with durable job ID and status URL. Support idempotency keys on generation/retry/review/release operations and expected revision/If-Match on mutations. Return explicit statuses for invalid schema, stale versions, rejected approvals, rate/budget limits, unsupported purpose/provider, and policy restrictions. Use 404 or policy-consistent access denial without leaking cross-tenant existence.

AI-API-03: Reject arbitrary client-supplied system prompts, provider URLs, tool definitions, model credentials, and source IDs outside authorization. Candidate-facing responses use purpose-built DTOs with allowlisted fields; never serialize internal AI payloads directly. Provide paginated audit/usage retrieval only to permitted roles, with content redaction.

---

# 12. Recruiter Dashboard — Frontend Pages

Implement the following screens:

1. Recruiter login/signup
2. Recruiter dashboard
3. Candidate management
4. Register/import candidate
5. Job creation and editing
6. Skills configuration
7. Question bank management
8. Question creation/editing
9. Assessment builder
10. Assessment preview
11. Assessment publishing and scheduling
12. Invite candidates
13. Invitation tracking
14. Live assessment status dashboard
15. Candidate submissions
16. Coding evaluation view
17. Candidate scorecard
18. Skill-level analytics
19. Candidate comparison
20. Reports and exports
21. Recruiter/organization settings

The dashboard should provide filters, pagination, search, sort, validation, empty states, loading states, and permission-aware actions.

---

### Additional recruiter AI screens

AI-UI-01: Implement competency mapping/review, generation batch monitor, question validation/review, blueprint coverage/approval, coding evidence/rubric review, skill-gap scorecards, interview follow-up review, report release, appeals queue, AI policy/provider configuration, budget/usage, and artifact lineage views. Reuse existing builder and result screens where practical. Clearly distinguish draft, approved, stale, failed, rejected, and released content; never make a hidden approval implicit in a save button.

---

# 13. Candidate Portal — Frontend Pages

Implement:

1. Invitation landing page
2. Candidate signup
3. Email verification
4. Candidate login
5. Candidate profile
6. Assigned assessment overview
7. Instructions and consent
8. Camera/microphone/network checks
9. Assessment readiness confirmation
10. Assessment workspace
11. Coding editor
12. Question navigation palette
13. Interruption/reconnection screen
14. Assessment resume screen
15. Review and final submission
16. Submission confirmation
17. Assessment history and permitted results

Candidate views should be clean, distraction-free, and accessible.

---

### Additional candidate transparency screens

AI-UI-02: Add plain-language AI disclosures and purpose preferences, accessible human-review/accommodation request flow, released feedback with evidence and limitations, and correction/appeal status. Keep feedback unavailable until release policy permits it; do not reveal reusable questions, solutions, hidden tests, or another candidate's artifacts through citations or downloads.

---

# 14. Non-Functional Requirements

### Security
- HTTPS everywhere outside local development
- Secure authentication/session handling
- RBAC and object-level authorization
- Multi-tenant isolation
- Rate limiting
- Input validation
- Secure invitation tokens
- Sensitive-data protection
- Proper secret management
- Safe file handling
- Hardened coding sandbox
- Auditability

### Reliability
- Durable answer persistence
- Idempotent requests
- Transactionally safe assessment lifecycle
- Correct behavior under retries
- Timer recovery after application restart
- Reconnection support
- Reliable asynchronous processing
- Graceful degradation when email or code execution infrastructure is unavailable

### Performance
- Efficient pagination
- Indexed assessment queries
- Bounded code execution concurrency
- Reasonable autosave frequency
- Responsive candidate UI
- Avoid excessive polling and database writes

### Privacy
- Minimize collected personal data.
- Obtain explicit consent where required.
- Disclose webcam/microphone usage.
- Define retention/deletion policies.
- Restrict recordings and monitoring artifacts to authorized users.
- Never expose one candidate's answers or information to another candidate.

### Observability
- Structured logs
- Correlation IDs
- Metrics
- Distributed tracing where applicable
- Assessment lifecycle monitoring
- Email delivery monitoring
- Coding execution monitoring
- Timer/recovery failure metrics
- Operational alerts

---

### AI threat model and prompt-injection defense

AI-SEC-01: Treat job descriptions, imported questions, candidate code/comments, documents, retrieval snippets, model output, and tool responses as untrusted data. Separate system/developer instructions from bounded source payloads; delimiters help organization but do not establish a security boundary. Test attacks such as “ignore prior instructions,” forged reviewer messages, data-exfiltration URLs, malicious code comments, and requests for hidden tests.

AI-SEC-02: Models receive no direct database, browser, shell, email, publishing, scoring-write, or unrestricted network tools. Where an optional narrowly scoped read tool is justified, enforce server-side tenant authorization, resource allowlists, bounded results, and explicit capability checks independently of model instructions. Never derive access permissions or approval decisions from generated text.

AI-SEC-03: Retrieve only authorized tenant/purpose-scoped sources before prompt construction. Enforce context/output limits, sanitize rendered output, validate all references/URLs, block provider-driven URL fetching and SSRF, and scan attachments through the existing safe-file pipeline. Redact secrets and unnecessary PII before dispatch. Refuse attempts to extract prompts, answer keys, credentials, or cross-tenant data. Injection detectors are defense in depth; authorization and restricted capabilities remain mandatory.

### AI provider reliability and cost

AI-OPS-01: Configure provider/model allowlists per purpose and tenant, key rotation, approved endpoint/region, token limits, concurrency, request deadlines, daily/monthly budgets, and per-user/job quotas. Support capability negotiation and schema compatibility. Record price-table version, estimated charge, actual reported usage, and reconciled charge; label unknown charges explicitly.

AI-OPS-02: Reserve estimated maximum cost atomically before dispatch to prevent concurrent overspend. Reconcile/release reservations after outcomes; treat timeout charges as uncertain until reconciled. Enforce input/output limits and bounded batch sizes. Expose budget warnings and operational kill switches. Never grant more generation capacity merely because a retry uses a new transport request ID.

AI-OPS-03: Use timeouts, bounded exponential backoff with jitter, retry-after handling, circuit breakers, bounded queues, and dead-letter/review states. Retry only eligible transient failures; do not endlessly retry rejected schemas or policy violations. Account for duplicate billable provider calls even if application outputs are deduplicated. Cancellation is best effort externally and prevents further publication/application internally.

AI-OPS-04: Fallback only to approved providers/models for the same purpose, data region, privacy terms, and schema capability; record routing provenance. If no permitted fallback exists, return pending/unavailable and preserve manual workflows. Provider failure cannot alter deterministic test scores or candidate time. Cache only tenant-scoped approved reusable content keyed by input/prompt/model/policy versions; do not cross-cache candidate submissions or retain deleted data in caches.

AI-OPS-05: Track queue age, provider latency, retries, malformed outputs, validation failures, unsupported citations, approval/override rates, drift signals, costs, budget denials, redaction failures, and deletion SLA failures. Logs/traces exclude raw prompts, candidate code, personal data, secrets, keys, and hidden tests by default. Authorized diagnostic capture is explicitly scoped, encrypted, time-limited, and audited.

### Fairness and accessibility

AI-FAIR-01: Use job-relevant approved rubrics consistently. Avoid demographic attributes and unjustified proxies in generation/evaluation; do not infer protected traits. Blind evaluator-facing inputs where feasible. Assess wording, language level, cultural assumptions, assistive-technology compatibility, and coding-language parity before publication.

AI-FAIR-02: Evaluate model/rubric versions on a curated expert-labeled benchmark spanning proficiency levels, languages, coding styles, and accessibility scenarios. Where lawful and appropriately consented, assess aggregate group differences with restricted demographic data separated from model inputs; minimum sample thresholds and statistical uncertainty must prevent false fairness claims. Human subject-matter reviewers investigate disparities and approve remediation before deployment.

AI-FAIR-03: Accommodations, device limitations, resume events, typing speed, accent, and camera refusal must not become AI scoring features or negative personality/integrity inferences. Approved accommodations may adjust timing/access through existing policy without changing skill criteria arbitrarily. Offer human review of disputed feedback and track resolution consistently.

### Privacy, consent, and data retention

AI-PRIV-01: Maintain a purpose-to-data inventory for competency generation, authoring, evaluation, feedback, analytics, and optional monitoring. Define organizational processing basis, required notices/consent, provider recipients, approved regions, subprocessor terms, and retention before activation. Do not claim universal legal compliance; document jurisdiction-specific decisions for qualified organizational review.

AI-PRIV-02: Default to no use of candidate data for provider training or platform model training. Verify provider contract/configuration before enabling a purpose; disable unsupported purposes when controls cannot be met. Minimize/pseudonymize candidate payloads and keep identity mapping internally. Do not send webcam/audio/biometric data for AI inference in the MVP.

AI-PRIV-03: Configure explicit retention durations for submissions, prompts/responses, artifacts, evidence, audits, caches, embeddings, exports, provider logs, and backups. Missing retention configuration blocks production activation. A scheduled deletion worker must traverse dependencies and request provider deletion where supported, recording completion/failure and documented provider limitations. Expire backup copies on the defined cycle and reapply deletion tombstones after restore.

AI-PRIV-04: Support access, correction, deletion, and consent-withdrawal requests under organizational policy and applicable requirements. Withdrawal stops future optional processing and triggers authorized deletion; it does not silently rewrite a historical score. Restrict and time-bound approved legal holds, disclose relevant limits, and ensure audit records do not preserve deleted raw personal content.

---

# 15. Expected Architecture and Implementation Quality

Apply pragmatic DDD, SOLID, and Clean/Hexagonal Architecture principles.

Separate:
- Domain model
- Application use cases
- Persistence adapters
- HTTP controllers
- Security
- Notification integrations
- Coding execution integration
- Evaluation logic
- Timer/lifecycle management

Ensure:
- Clear ownership of assessment and attempt state
- No distributed transaction assumptions
- Idempotent background processing
- Bounded retries
- Optimistic/pessimistic locking where justified
- Correct transaction boundaries
- Consistent error mapping
- No hardcoded credentials
- No unnecessary microservices

Explain meaningful design trade-offs, especially around timer fairness, distributed concurrency, coding isolation, and assessment versioning.

---

### AI architecture boundaries and evaluation authority

AI-DESIGN-01: Separate provider adapters, prompt-template registry, policy enforcement/redaction, retrieval, schema/business validation, durable orchestration, usage accounting, artifact review, and report release. AI code cannot directly mutate attempts, answer revisions, timing ledgers, approved questions, or final scores.

AI-DESIGN-02: Define versioned domain events such as AiJobRequested, AiArtifactValidated, AiReviewRecorded, AiArtifactSuperseded, and ReportReleased. Deliver idempotently with an outbox; document failure compensation and stale dependency handling. All event payloads use minimal data references rather than broad personal-data copies.

AI-DESIGN-03: Deliver sequence diagrams for authoring → validation → approval → publication and submission → deterministic grading → AI feedback → human review → release, including timeout, duplicate delivery, and deletion races. Record tradeoffs for grounding, prompt-injection isolation, score authority, provider fallback, and uncertain model reproducibility.

---

# 16. Testing Requirements

Implement appropriate tests rather than only describing them.

### Backend
- Unit tests for scoring and timer rules
- State transition tests
- Invitation expiry and reuse tests
- Authorization and tenant-isolation tests
- Concurrent attempt tests
- Answer revision/conflict tests
- Idempotent submission tests
- Expiration/finalization tests
- Database integration tests with Testcontainers
- Coding execution integration contract tests

### Frontend
- Component tests
- Form validation tests
- Answer selection persistence tests
- Timer rendering tests
- Navigation tests
- Offline/reconnection UI tests
- Coding editor integration tests

### End-to-End
Use a suitable browser automation framework such as Playwright.

Cover complete user journeys:
1. Recruiter registers → creates assessment → invites candidate → candidate completes → recruiter views results.
2. Candidate registers via invitation → verifies email → performs system checks → starts test.
3. Candidate selects answers → refreshes browser → resumes with answers intact.
4. Candidate disconnects → reconnects → resumes with the correct remaining time.
5. Candidate writes code → executes tests → submits → receives evaluation.
6. Assessment expires → backend finalizes the attempt.
7. Unauthorized users cannot access another organization's data or another candidate's answers.

Use deterministic clocks in timer tests.

---

### AI verification and acceptance criteria

Implement deterministic adapter tests and a curated evaluation harness; do not write fragile tests requiring identical prose. CI uses safe fixtures/fakes and no production candidate data. Run separately authorized live-provider smoke/evaluation checks under a fixed budget. Record provider/model/prompt/rubric versions, dataset version, metrics, sample sizes, thresholds, and results; do not claim live integration was verified when only a fake ran.

AI-AC-01 — Preservation: All original end-to-end journeys, timer modes, resume, autosave, tenant isolation, coding sandbox, and grading tests pass with AI enabled, disabled, delayed, malformed, and unavailable. Use an outage test proving answer acknowledgment and authoritative time are independent of the AI queue.

AI-AC-02 — Mapping: A fixture job description maps to approved canonical skills with valid evidence spans; ambiguous skills are marked for review; weights must total 100%; unapproved/stale maps cannot publish dependent blueprints.

AI-AC-03 — Questions: Reject single-choice drafts with multiple keyed answers, invalid multi-choice scoring, duplicate options, missing evidence references, or invalid skill IDs. A deliberately failing reference solution/test mismatch blocks coding-question approval. Generated content cannot become candidate-visible before authorized approval and publication.

AI-AC-04 — Blueprints: A feasible fixture satisfies every declared constraint; an insufficient question bank returns explicit infeasibility without relaxation. Every assigned randomized form passes validation and can be reconstructed from pinned item versions and selection records.

AI-AC-05 — Scoring: Identical finalized submissions yield identical deterministic scores regardless of provider output. AI proposed scores remain bounded and unapproved until a permitted reviewer records a decision. Unsupported code-line/test citations are rejected or routed to review, never presented as verified evidence.

AI-AC-06 — Review/versioning: Editing an approved artifact creates a new draft; stale approvals and concurrent stale writes fail. Regeneration does not overwrite released content. Reviewer identity, reason, source versions, provenance, and release history are retrievable by authorized auditors.

AI-AC-07 — Injection/isolation: Adversarial job text, code comments, retrieved documents, and model responses cannot publish, alter scores, access network/tools, reveal hidden tests, or retrieve another tenant's data. Test both direct API attacks and worker execution; scan candidate DTOs/exports/logs for seeded secret canaries.

AI-AC-08 — Reliability: Simulate timeout, 429, transient server errors, schema failures, worker restart, duplicate queue messages, cancellation races, and provider unavailability. Verify bounded retries, deduplicated applied artifacts, visible failure states, permitted-only fallback, preserved manual workflow, and recoverable queue state.

AI-AC-09 — Budgets: Concurrent requests cannot exceed configured reservation limits. Token caps, quotas, retry charges, timeout reconciliation, and kill switches are exercised. Record separately what was estimated, charged, reconciled, and unknown.

AI-AC-10 — Privacy: Verify redaction and field allowlists, purpose restrictions, consent/disclosure versions, optional-processing withdrawal, and authorized access/deletion. A deletion test removes fixture data from primary storage, caches, artifacts, and retrieval indexes; provider/backup residuals follow recorded policy and scheduled expiry. Deleted material cannot be regenerated from retained diagnostic payloads.

AI-AC-11 — Fairness/accessibility: Run expert-reviewed fixtures across relevant language/style/accessibility cases. Test keyboard and screen-reader journeys for AI authoring and feedback. Confirm demographic/monitoring/accommodation metadata is absent from scoring inputs. Unassessed skills show insufficient evidence and human review remains accessible.

AI-AC-12 — Release gate: Before production use, organization owners set and approve numeric quality thresholds on the versioned benchmark: rubric agreement with expert reviewers, unsupported-claim rate, valid citation rate, critical factual errors, and assessed disparity measures with sample-size limits. Require zero observed critical authorization/secret-exposure failures in the defined security suite. Block rollout on unmet gates; passing finite tests does not prove absence of all risk.

AI-AC-13 — Drift and rollback: A model/prompt/rubric/provider change runs regression evaluation before promotion. A controlled fixture regression demonstrates rollback/disable behavior; new jobs use the restored approved configuration while existing submissions/results retain their historical versions. Label missing statistical evidence instead of reporting an unsupported pass.

---

# 17. Delivery and Implementation Instructions

Follow these execution stages.

## Phase 1: Requirements and Product Design

Produce:
- Complete functional requirements
- Frontend requirements
- Backend requirements
- User stories
- Role-permission matrix
- Business rules
- Assessment state machines
- Important edge cases
- Acceptance criteria
- MVP versus later-feature prioritization

Clearly distinguish mandatory capabilities from optional enhancements.

## Phase 2: Solution Architecture

Produce:
- High-level architecture
- Component diagram
- Domain/module boundaries
- Candidate assessment sequence diagram
- Interruption/resume sequence diagram
- Coding execution architecture
- PostgreSQL schema
- REST API contracts
- Security architecture
- Timer consistency approach
- Operational deployment architecture

Use Mermaid diagrams where helpful.

## Phase 3: Backend Implementation

Implement:
- Database migrations
- Domain entities and enums
- Repository interfaces/adapters
- Application services
- Controllers and DTOs
- Authentication and authorization
- Recruiter workflows
- Candidate workflows
- Assessment engine
- Answer persistence
- Timer and resume logic
- Grading
- Reporting
- Email notifications
- Coding execution integration
- Error handling

## Phase 4: Frontend Implementation

Implement:
- Routing and layouts
- Recruiter dashboard
- Candidate portal
- Forms and validations
- Assessment builder
- Question bank UI
- Candidate examination workspace
- Auto-save behavior
- Timer
- Coding editor
- Resume flow
- Results dashboard

Use real backend API integrations, not permanent mocked responses.

## Phase 5: Verification

Run available:
- Backend build and tests
- Frontend type checks and lint
- Frontend tests
- Database migration checks
- Integration tests
- End-to-end tests
- Relevant security and concurrency checks

Fix failures within the authorized scope.

## Phase 6: Local Deployment and Documentation

Deliver:
- Working source code
- README
- Environment-variable template
- Docker Compose configuration
- Database migrations
- API documentation
- Seed data
- Example recruiter and candidate workflow
- Local startup commands
- Test commands
- Deployment notes
- Remaining limitations and risks

---

## AI deliverables integrated into the six phases

AI-DEL-01 — Phase 1: Add numbered AI requirements, permission matrix, purpose/data inventory, human decision boundaries, consent/review alternatives, threat model, fairness plan, benchmark specification, measurable release gates, and explicit retention/budget configuration decisions. Map each AI-* requirement to implementation and acceptance checks.

AI-DEL-02 — Phase 2: Extend module diagrams, schema, API contracts, and sequences with durable AI orchestration, trust boundaries, provider integration, validation/approval, report release, budget reservations, cancellation/deletion, and failure isolation from candidate timing/saves.

AI-DEL-03 — Phase 3: Implement adapters, policy enforcement, migrations, job/outbox workers, template registry, validators, authoring pipelines, deterministic blueprint checks, feedback/report services, review APIs, provenance, budget ledger, retention/deletion workers, and audit access controls. Add one real configurable adapter with a documented minimal smoke test; do not label a placeholder as live integration.

AI-DEL-04 — Phase 4: Implement the recruiter and candidate AI screens defined above using real APIs. Preserve existing exam navigation, save, timer, and resume experiences. Show asynchronous status, human decisions, evidence, uncertainty, and accessible failure/manual-review paths.

AI-DEL-05 — Phase 5: Run original checks plus AI-AC-01 through AI-AC-13. Deliver a verification matrix distinguishing executed/pass, executed/fail, blocked, and not run, with evidence. Benchmark threshold approval and organization policy decisions are explicit production gates, not reasons to abandon the integrated local MVP.

AI-DEL-06 — Phase 6: Supply prompt templates and schemas, safe seed questions/maps, benchmark fixtures, fake/live mode setup, provider environment template, purpose/retention/budget configuration examples, operational dashboards, runbooks for provider outage, drift, budget exhaustion, rollback, security incident, appeals, and deletion. State unresolved provider/legal/policy prerequisites and do not claim production readiness until mandatory gates pass.

---

# 18. Definition of Done

The implementation is functionally complete only when the following scenario succeeds:

1. An HR/recruiter account logs in.
2. Recruiter creates a Java Backend Developer assessment.
3. Recruiter selects Java, Spring Boot, Kafka, SQL, and coding skills.
4. Recruiter configures difficulty, duration, scoring, and invitation deadline.
5. Recruiter adds a candidate's email address.
6. Platform sends a valid assessment invitation email.
7. Candidate opens the invitation.
8. Candidate registers and verifies email.
9. Candidate reads and accepts instructions.
10. Candidate successfully completes required browser/camera/microphone checks.
11. Candidate starts the assessment.
12. Candidate answers single-choice questions.
13. Candidate answers multiple-choice questions.
14. Candidate writes and runs a coding solution.
15. Platform saves selections and code reliably.
16. Candidate's browser closes unexpectedly.
17. Candidate returns through the authorized resume flow.
18. Previously acknowledged selections and code are restored.
19. Candidate continues from the saved question with the correct remaining time under the configured pause policy.
20. Candidate submits the assessment.
21. Platform evaluates automatically gradable questions and coding results.
22. Recruiter opens the candidate's completed assessment.
23. Recruiter views total score, skill scores, coding results, and evaluation status.
24. Recruiter records feedback and a hiring recommendation.
25. Final results and audit records are durably stored.

Also verify negative scenarios such as expired invitations, unauthorized access, duplicate submissions, timer exhaustion, and concurrent updates.

---

### Additional AI definition of done

AI-DONE-01: Demonstrate the original 25-step scenario with the recruiter first approving an AI-suggested competency map, requesting and reviewing generated single-choice/multiple-choice/coding questions, passing validation, approving a feasible blueprint, and publishing pinned versions. Complete the same candidate interruption/resume journey without changed timing semantics.

AI-DONE-02: After submission, show deterministic scores, evidence-linked draft coding feedback and skill gaps, evaluator review/override with reasons, approved report release, and optional interview follow-ups. Candidate sees only permitted feedback and can request human review. An unassessed skill is explicitly labeled.

AI-DONE-03: Demonstrate a provider outage and a prompt-injection fixture while saves, resume, final submission, deterministic grading, and manual review remain functional. Demonstrate rejection of unapproved/stale artifacts, cross-tenant access, budget overspend, and confidential feedback leakage. Complete the applicable AI acceptance matrix and record remaining production gates.

---

# 19. Rules for the AI Implementation Agent

1. Begin by inspecting the repository and following its documented architecture, versions, and coding conventions.
2. If the repository is empty, establish a maintainable project structure using the preferred technologies.
3. Do not stop after writing requirements or architecture. Proceed to implementation.
4. Make reasonable routine decisions without unnecessary clarification.
5. Identify and explicitly state any architectural assumptions that materially affect behavior.
6. Implement the MVP as an integrated vertical slice before adding optional features.
7. Do not introduce unnecessary dependencies, abstraction layers, or services.
8. Prefer actual executable implementations over pseudocode.
9. Avoid placeholder functionality that appears complete but is not.
10. Never claim functionality has been tested unless the checks were actually executed.
11. Preserve unrelated repository work.
12. Include meaningful failure handling and tests.
13. Prioritize correctness, security, concurrency safety, reliability, and data integrity.
14. Do not use insecure code execution shortcuts.
15. At the end of each implementation stage, report completed changes, verification results, and unresolved issues.

**Start by producing a detailed, uniquely numbered Functional Requirements Specification for frontend and backend, followed by the workflow diagrams, architecture, data model, API design, phased implementation plan, and actual implementation.**

### Additional rules for implementing AI features

AI-AGENT-01: Implement these AI features as part of the integrated application; this document is a future implementation instruction, not a request to execute candidate code or contact providers while merely refining the prompt.

AI-AGENT-02: Inspect existing configuration and official provider documentation when implementing integrations. Keep provider models/pricing/configuration replaceable and report actual tested versions. Never expose or commit credentials, use real candidate data in demonstrations, or enable provider training by default.

AI-AGENT-03: Treat all imported content and generated output as untrusted data. Implement approval and policy boundaries in application code, not solely in prompts. Never let a model approve its own work or make employment decisions.

AI-AGENT-04: Deliver the core assessment vertical slice and mandatory AI MVP through the existing phases. Track deferred optional features explicitly, keep manual workflows usable, and do not silently remove original requirements to accommodate AI. Include AI requirement-to-code/test traceability and honest verification results at delivery.

---

## 20. Mandatory continuation: invitation-to-assessment experience

### 20.1 Implementation objective and boundaries

CONT-01: Implement the complete journey on the existing React/TypeScript frontend and Java/Spring backend using real API integrations and PostgreSQL persistence. Extend existing modules and routes where suitable; avoid a parallel demo application or permanent mocked responses. Test fakes remain permitted only in clearly identified test/development integrations.

CONT-02: Begin with a requirement-to-code/test gap assessment, then implement and verify the gaps. Do not repeatedly recreate requirements already documented here. Complete each integrated increment across UI, API, authorization, persistence, and automated tests before claiming the feature works. Continue through remaining mandatory scope after this candidate journey.

CONT-03: Integrity controls must prevent actions the server can reliably prohibit and record relevant disclosed observations for authorized human review. A browser application cannot guarantee that no cheating occurs, detect every external device, or establish dishonesty from focus/media events. Do not claim otherwise or automatically penalize, reject, or invalidate a candidate based solely on monitoring events.

### 20.2 Secure invitation entry and onboarding

INV-FLOW-01: Clicking the emailed invitation opens the candidate assessment entry page directly. Resolve invitation validity, intended verified identity, availability window, revocation, attempt limit, and existing attempt state on the server. Show meaningful next actions for registration, verification, sign-in, onboarding, resume, or completed status. Preserve the invitation context across authentication without exposing its token to logs, analytics, referrers, or third parties.

INV-FLOW-02: An invitation link is not sufficient authentication. Bind a claimed invitation to the intended verified email/account. Reject another account's claim, token replay after revocation/expiry, and cross-tenant or cross-candidate access. Returning through a valid link resumes the existing eligible attempt rather than creating an additional active attempt.

INV-FLOW-03: Do not start the exam timer when the link is opened, the user signs in, instructions load, or device checks run. Start only when the candidate explicitly selects Start assessment and the backend atomically authorizes the transition. Once an attempt has started, reopening onboarding must not pause or reset its timer.

### 20.3 Instructions and test overview

INSTR-01: Before starting, display the company, job/assessment title, instructions, total duration, total questions, passing and marking rules, negative/partial marking, supported coding languages, back-navigation/skip rules, attempt limit, deadline with timezone, interruption/pause policy, submission behavior, support contact, accommodations route, and privacy/AI disclosures.

INSTR-02: Show a readable breakdown by question category (single-choice, multiple-choice, coding) and targeted technical skill (for example Java, Spring Boot, Kafka, SQL, and coding). Include per-category counts, per-skill coverage and approved weights where configured. Derive totals from the pinned assigned form, not hardcoded sample values. If questions cover multiple skills, label overlapping skill counts so they are not mistaken for additive totals. Do not disclose question text, answer keys, hidden tests, reference solutions, or confidential rubric material before authorized start.

INSTR-03: Require explicit acknowledgment of the applicable instruction, disclosure, and policy versions; persist actor, assignment, version IDs, and acknowledgment time. Keep optional AI consent separate from mandatory instructions. Revisit instructions without losing readiness or creating an attempt. Do not silently accept new policy versions on a candidate's behalf.

### 20.4 Mandatory system readiness checks

CHECK-01: Implement an interactive checklist for supported browser capabilities, JavaScript, secure-context requirements, authenticated session/cookies, desktop viewport, network connectivity/latency, and any assessment-required webcam, microphone, microphone input-level, and audio-playback checks. Use explicit statuses: Not checked, Checking, Passed, Failed, Not required, and Authorized exception. Give a specific remediation action and Retry control for failures.

CHECK-02: Use permission-based browser media APIs. Show a local webcam preview, microphone input meter, and user-triggered playback test where required. Detect denied permission, absent/in-use devices, unsupported APIs, and ended media tracks. Explain that a local check does not upload or record media. Stop tracks when checks end or the candidate exits, unless separately disclosed and consented ongoing monitoring requires them. Never record or transmit secretly.

CHECK-03: Persist a bounded, schema-validated readiness result with server receipt time and pinned requirements. Configure check freshness, recheck expired/changed device conditions, and require all mandatory checks or an authorized accommodation before start. Client-reported checks are observations, not tamper-proof attestations; do not represent them as verified identity or proof of integrity. The start API must reject missing acknowledgment, stale readiness, unresolved mandatory failures, or unauthorized exceptions even when called directly.

CHECK-04: Provide an accessible manual-review/accommodation path when required devices or permissions are unavailable. Record the authorized exception, reason, actor and policy version. Do not infer misconduct from device limitations or camera refusal. Define the approved response to mid-test media loss without clearing answers, silently granting time, or inventing a cheating verdict.

### 20.5 Assessment workspace and status semantics

NAV-01: Implement a distraction-free test workspace with title, authoritative countdown, connectivity, save state, progress counts, question content, navigation palette, Previous/Next where permitted, Skip, Mark for review, and Finish assessment. Include accessible legends and confirmation/summary dialogs. Support SCQ radio selection, MCQ checkbox selection, and Monaco coding answers with sample execution.

NAV-02: Apply the following status model consistently in the question palette, summary, persisted progress, refresh, and resume views:

|Primary status|Visual treatment|Definition|
|---|---|---|
|Not visited|Neutral gray + text/icon|Never opened and no acknowledged answer|
|Viewed, unanswered|Neutral outlined + text/icon|Opened, but neither explicitly skipped nor acknowledged as answered|
|Skipped|Blue + Skip label/icon|Explicitly skipped with no valid acknowledged answer|
|Answered|Green + checkmark and Saved label|A valid nonempty answer has been acknowledged by the backend; this does not imply correctness|

Current-question focus and Mark for review are independent overlays, not replacements for these primary statuses. Use text, accessible names and icons as well as color; maintain sufficient contrast. A screen-reader label should describe, for example, “Question 4, skipped, marked for review.”

NAV-03: Skip an unanswered question by durably recording the skip and navigation update, then advance to the next permitted question. If it is the final question, open the assessment summary. Show pending/offline status until acknowledgment. Skipping must not submit the overall assessment. Allow return to skipped questions when back-navigation is enabled; if disabled, clearly explain the restriction before leaving and enforce it in the API.

NAV-04: Skip must not erase an existing answer. An already answered question remains green when navigating past it. Provide a separate explicit Clear answer action if permitted; persist the cleared answer before representing it as unanswered. Answering a previously skipped question changes blue to green only after successful acknowledgment. “Save answer” concerns one question; “Submit assessment” finalizes the whole attempt. Never conflate these operations.

NAV-05: An immediate local choice change may display Selected / Saving, but must not imply durable save. While an acknowledged answer is being edited, show the pending edit distinctly, even if the prior acknowledged answer remains counted as answered. On failure/conflict, retain local work, show an actionable error, and do not paint an unacknowledged answer green. An empty selection, cleared selection, empty code or untouched starter template does not automatically count as completed; define coding completion separately from merely persisting a draft.

NAV-06: Persist navigation/completion state, skipped/visited status, review flag, current question, response/code/language and revisions on the backend. Use existing attempt ownership, tab lease, expected revision and idempotency controls. Atomic saves must not lose a concurrent answer or turn answered questions blue because of a delayed skip. Restore all acknowledged state after refresh, browser restart, sign-in and permitted resume.

NAV-07: Show total, answered, skipped, other unanswered, and marked-for-review counts. Answered + skipped + other unanswered must equal the assigned total; review flags are overlapping and must be labeled accordingly. At final submission, identify unanswered/skipped questions and pending local changes. Confirm finalization only after the server acknowledgment; expiry uses persisted answers and cannot accept late changes contrary to policy.

### 20.6 Assessment integrity and privacy

INT-01: Enforce ownership, tenant isolation, invitation eligibility, immutable assigned questions, server-authoritative time, allowed navigation, revision conflicts, a single active tab/session lease, attempt limits, and terminal answer immutability on every relevant backend route. Never depend on disabled buttons or hidden UI controls for authorization.

INT-02: Pin any question/option randomization to the assigned form and approved seed; preserve the same order on resume and map answers to stable IDs. Keep keys and hidden tests out of candidate payloads, HTML, bundles, client storage, source maps, execution responses, and exports. Maintain the separate hardened coding execution boundary and the prohibition on live AI answer assistance in standard assessment mode.

INT-03: Under a disclosed versioned policy, record bounded event types such as page hidden/visible, window blur/focus, fullscreen exit when fullscreen is requested, connectivity changes, session conflicts, and required media-track loss. Store event ID, attempt, allowed event type, bounded metadata, client observation time, server receipt time, and source. Deduplicate and rate-limit events; client timestamps never control exam time. Avoid collecting keystrokes, clipboard contents, browsing history, unrelated windows, or continuous media by default.

INT-04: Treat browser observations as incomplete and potentially spoofed. Do not claim browser fullscreen or copy/paste restrictions create a secure lockdown. If clipboard restrictions are configured, disclose them and preserve accessible editing and approved accommodations; do not log clipboard contents. Higher-assurance managed-device or supervised modes require a separate approved design and deployment, not a hidden prerequisite for the web MVP.

INT-05: Provide candidate-visible policy reminders and a recruiter/evaluator integrity timeline with evidence, uncertainty, candidate explanations, and audited human review. Keep integrity events separate from deterministic skill scores and AI scoring inputs. No automatic cheating verdict, hiring decision, score deduction, or forced submission solely from focus, camera, microphone, network, or fullscreen events.

INT-06: Collect the minimum data needed for the approved purpose, with explicit retention, deletion, access and consent rules. Media monitoring beyond local compatibility checks requires separate consent/disclosure, secure storage and an implemented retention policy. Do not introduce facial recognition, emotion/personality analysis, gaze-based dishonesty judgments or biometric identification as a shortcut.

### 20.7 Backend contracts and migrations

CONT-BE-01: Implement or extend versioned APIs for invitation resolution, safe assessment overview, instruction acknowledgment, readiness checks, start/resume, question retrieval, answer/progress saves, skip/clear/review navigation, heartbeat, integrity-event ingestion, submission, and authorized integrity review. Reuse existing endpoints when they can express these contracts without ambiguity. Document request/response DTOs, permissions, validation, error codes, idempotency and concurrency for each.

CONT-BE-02: Persist required additions through additive Flyway migrations with tenant-safe foreign keys, indexes, uniqueness and revision guards. Include policy versions, readiness timestamps, progress state and bounded integrity events. Preserve existing attempts and acknowledged answers during migration; document backfill behavior. Do not edit previously applied migrations to simulate a clean deployment.

CONT-BE-03: Keep integrity ingestion and optional AI off the synchronous answer-save transaction path. Monitoring outages must not discard acknowledged answers, extend timers, block final submission, or assign a false grade. Bound event buffering/retries, disclose lost observation coverage, and ensure recovery does not overwrite newer progress.

### 20.8 Required executable acceptance tests

CONT-AC-01: Valid invitation → intended candidate registration/sign-in and email verification → correctly counted category/skill overview → acknowledged instructions → required checks → explicit Start → authorized questions. Verify the clock does not start during onboarding.

CONT-AC-02: Invalid, expired, revoked, wrong-account and cross-tenant invitation requests fail safely. Concurrent start requests produce one active attempt; reopening the link resolves to resume or completion as appropriate.

CONT-AC-03: Mandatory camera/microphone denial, missing devices, stale readiness and direct start-API bypass fail with useful remediation. Authorized exceptions work and are audited. Unit/component tests can simulate device APIs; clearly distinguish them from real-device verification.

CONT-AC-04: Skip unanswered question → blue after acknowledgment → next permitted question. Reload/resume retains blue. Return, select SCQ/MCQ answer, receive save acknowledgment → green. Clear answer → no longer green after acknowledgment. Navigating past an answered question does not delete it. Skip on the last item opens summary.

CONT-AC-05: Offline selection is visibly unsynced, not falsely saved. Reconnect retries safely; stale skip/save requests cannot overwrite newer answers. Server-acknowledged selections, code, review flags, question location and colors survive interruption. Assert progress-count invariants and accessible labels.

CONT-AC-06: Test back-navigation restrictions through UI and direct API calls, concurrent tabs, duplicate saves/submission, server expiry, controlled pause exhaustion, and backend restart. AI and monitoring outages do not change timing or prevent durable answer acknowledgment.

CONT-AC-07: Candidate requests and exports contain no seeded answer-key/hidden-test canaries. Forged event fields, oversized batches, invalid types, other-candidate IDs and cross-tenant review access are rejected. Duplicate events are deduplicated; focus/media events do not change scores or trigger automatic cheating decisions.

CONT-AC-08: Verify keyboard-only navigation, visible focus, color-independent status, screen-reader labels, usable zoom, permission-denial recovery, and no repeated intrusive live announcements. Verify media tracks stop as required and no media upload occurs during local-only checks.

CONT-AC-09: Run the original definition-of-done journeys and mandatory AI acceptance matrix in addition to these tests. Record executed/pass, executed/fail, blocked and not-run distinctly, with exact commands and evidence. Fakes do not establish live provider or sandbox verification.

### 20.9 Execution and delivery contract

CONT-DEL-01: Work in this order: inspect and map gaps; update only necessary design/API decisions; implement migrations/domain rules/backend endpoints; integrate frontend; add failure/concurrency/security/accessibility tests; run checks and fix failures; update startup/configuration/runbooks and requirement traceability. Preserve manual workflows while external integrations are unavailable.

CONT-DEL-02: Deliver actual source changes with a concise report of implemented behavior, changed files, migrations, API contracts, verification results, startup instructions and remaining blockers. Each claimed feature needs both server and client behavior where applicable. Do not mark the full specification complete while mandatory requirements or acceptance journeys remain missing. A production policy/provider blocker does not excuse unfinished local implementation that can proceed safely.

CONT-DEL-03: Begin implementation immediately when this prompt is invoked for implementation. Provide brief progress updates with completed changes, verification and unresolved dependencies. Do not ask whether to continue work already authorized by the specification.

