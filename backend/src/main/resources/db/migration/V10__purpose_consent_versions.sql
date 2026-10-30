ALTER TABLE candidate_acknowledgments ADD COLUMN consent_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE ai_job_evaluations ADD COLUMN consent_revision bigint NOT NULL DEFAULT 0;
UPDATE ai_job_evaluations j SET consent_revision=k.consent_revision FROM assessment_attempts a JOIN candidate_acknowledgments k ON k.invitation_id=a.invitation_id WHERE j.attempt_id=a.id;
CREATE TABLE purpose_consent_events (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, invitation_id uuid NOT NULL,
 candidate_id uuid NOT NULL REFERENCES users, revision bigint NOT NULL, granted boolean NOT NULL,
 disclosure_version text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(invitation_id,revision), FOREIGN KEY(organization_id,invitation_id) REFERENCES invitations(organization_id,id)
);
