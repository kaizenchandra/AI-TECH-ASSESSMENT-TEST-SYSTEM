CREATE TABLE generated_question_validations (
 artifact_id uuid PRIMARY KEY REFERENCES ai_artifacts, state text NOT NULL DEFAULT 'QUEUED',
 content_hash text NOT NULL, requested_by uuid NOT NULL REFERENCES users, lease_id uuid, lease_until timestamptz,
 attempts integer NOT NULL DEFAULT 0, finding text, completed_at timestamptz
);
CREATE TABLE assessment_ai_sources (
 organization_id uuid NOT NULL, assessment_id uuid NOT NULL, artifact_id uuid NOT NULL, artifact_revision bigint NOT NULL,
 question_index integer NOT NULL, question_id uuid NOT NULL,
 PRIMARY KEY(assessment_id,question_id),
 FOREIGN KEY(organization_id,assessment_id) REFERENCES assessments(organization_id,id),
 FOREIGN KEY(organization_id,artifact_id) REFERENCES ai_artifacts(organization_id,id)
);
