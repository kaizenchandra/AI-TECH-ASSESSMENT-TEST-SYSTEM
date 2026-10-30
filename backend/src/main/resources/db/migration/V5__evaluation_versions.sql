ALTER TABLE evaluation_results ADD COLUMN scoring_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE evaluation_results ADD COLUMN approved_feedback text;
ALTER TABLE evaluation_results ADD COLUMN final_summary jsonb;
CREATE TABLE evaluation_versions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations,
 attempt_id uuid NOT NULL REFERENCES evaluation_results(attempt_id), version bigint NOT NULL,
 reviewer_id uuid NOT NULL REFERENCES users, summary jsonb NOT NULL, adjustments jsonb NOT NULL,
 feedback text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(attempt_id,version)
);
