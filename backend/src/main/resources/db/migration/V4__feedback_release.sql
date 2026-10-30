CREATE TABLE ai_job_evaluations (
 job_id uuid PRIMARY KEY REFERENCES ai_jobs, organization_id uuid NOT NULL,
 attempt_id uuid NOT NULL, final_revision bigint NOT NULL, evaluation_revision bigint NOT NULL,
 FOREIGN KEY(organization_id,attempt_id) REFERENCES assessment_attempts(organization_id,id)
);
CREATE TABLE report_releases (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations,
 attempt_id uuid NOT NULL REFERENCES evaluation_results(attempt_id), evaluation_revision bigint NOT NULL,
 released_by uuid NOT NULL REFERENCES users, snapshot jsonb NOT NULL,
 idempotency_key uuid NOT NULL, released_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(organization_id,idempotency_key), UNIQUE(attempt_id,evaluation_revision)
);
