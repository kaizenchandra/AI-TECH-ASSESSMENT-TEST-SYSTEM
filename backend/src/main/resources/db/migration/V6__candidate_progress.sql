CREATE TABLE question_visits (
 attempt_id uuid NOT NULL REFERENCES assessment_attempts, question_id uuid NOT NULL,
 visited_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(attempt_id,question_id)
);
INSERT INTO question_visits(attempt_id,question_id) SELECT attempt_id,question_id FROM candidate_answers;
INSERT INTO question_visits(attempt_id,question_id) SELECT id,current_question FROM assessment_attempts WHERE current_question IS NOT NULL ON CONFLICT DO NOTHING;
ALTER TABLE candidate_acknowledgments ADD COLUMN assessment_version_id uuid REFERENCES assessment_versions;
ALTER TABLE candidate_acknowledgments ADD COLUMN policy_version text;
-- Existing answers remain valid. Unstarted invitations must acknowledge the new policy.
CREATE TABLE readiness_exceptions (
 invitation_id uuid PRIMARY KEY REFERENCES invitations, approved_by uuid NOT NULL REFERENCES users,
 reason varchar(2000) NOT NULL, checks jsonb NOT NULL, expires_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE integrity_events (
 attempt_id uuid NOT NULL, organization_id uuid NOT NULL, id uuid NOT NULL,
 event_type text NOT NULL, observed_at timestamptz NOT NULL, received_at timestamptz NOT NULL DEFAULT now(),
 source text NOT NULL DEFAULT 'UNVERIFIED_BROWSER', PRIMARY KEY(attempt_id,id),
 FOREIGN KEY(organization_id,attempt_id) REFERENCES assessment_attempts(organization_id,id)
);
CREATE INDEX integrity_retention_idx ON integrity_events(received_at);
CREATE TABLE integrity_reviews (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, attempt_id uuid NOT NULL,
 actor_id uuid NOT NULL REFERENCES users, note varchar(2000) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(organization_id,attempt_id) REFERENCES assessment_attempts(organization_id,id)
);
