CREATE TABLE code_execution_jobs (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, attempt_id uuid NOT NULL,
 question_id uuid NOT NULL, answer_revision bigint NOT NULL, purpose text NOT NULL CHECK (purpose IN ('SAMPLE','GRADE')),
 idempotency_key uuid NOT NULL, state text NOT NULL DEFAULT 'QUEUED' CHECK(state IN ('QUEUED','RUNNING','RETRY_WAIT','SUCCEEDED','FAILED')),
 payload jsonb NOT NULL, result jsonb, attempts integer NOT NULL DEFAULT 0,
 lease_token uuid, lease_until timestamptz, available_at timestamptz NOT NULL DEFAULT now(),
 failure_code text, created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
 FOREIGN KEY(organization_id,attempt_id) REFERENCES assessment_attempts(organization_id,id),
 UNIQUE(attempt_id,question_id,answer_revision,purpose), UNIQUE(attempt_id,idempotency_key)
);
CREATE INDEX code_execution_dispatch ON code_execution_jobs(state,available_at);
CREATE TABLE evaluation_reviews (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations,
 attempt_id uuid NOT NULL REFERENCES evaluation_results(attempt_id), reviewer_id uuid NOT NULL REFERENCES users,
 expected_revision bigint NOT NULL, feedback text NOT NULL, recommendation text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE evaluation_results ADD COLUMN revision bigint NOT NULL DEFAULT 0;
ALTER TABLE evaluation_results ADD COLUMN release_state text NOT NULL DEFAULT 'DRAFT' CHECK(release_state IN ('DRAFT','APPROVED','RELEASED'));
ALTER TABLE evaluation_results ADD COLUMN approved_by uuid REFERENCES users;
ALTER TABLE evaluation_results ADD COLUMN released_at timestamptz;
