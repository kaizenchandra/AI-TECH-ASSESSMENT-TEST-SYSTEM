CREATE TABLE assessment_blueprints (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations, author_id uuid NOT NULL REFERENCES users,
 competency_id uuid NOT NULL, competency_revision bigint NOT NULL, constraints jsonb NOT NULL, result jsonb NOT NULL,
 selected_items jsonb NOT NULL, state text NOT NULL CHECK(state IN ('INFEASIBLE','REVIEW_REQUIRED','APPROVED','REJECTED')),
 revision bigint NOT NULL DEFAULT 0, reviewer_id uuid REFERENCES users, review_reason text,
 created_at timestamptz NOT NULL DEFAULT now(), reviewed_at timestamptz,
 FOREIGN KEY(organization_id,competency_id) REFERENCES ai_artifacts(organization_id,id), UNIQUE(organization_id,id)
);
CREATE INDEX blueprint_tenant_idx ON assessment_blueprints(organization_id,created_at);
ALTER TABLE assessments ADD COLUMN blueprint_id uuid;
ALTER TABLE assessments ADD CONSTRAINT assessment_blueprint_tenant_fk FOREIGN KEY(organization_id,blueprint_id) REFERENCES assessment_blueprints(organization_id,id);
