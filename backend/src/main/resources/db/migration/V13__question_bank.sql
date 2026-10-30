-- Additive: existing assessments and their published snapshots are unchanged.
CREATE TABLE question_bank (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations,
 archived boolean NOT NULL DEFAULT false, revision bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(organization_id,id)
);
CREATE TABLE question_bank_versions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, question_id uuid NOT NULL,
 version integer NOT NULL CHECK(version>0), author_id uuid NOT NULL REFERENCES users,
 content jsonb NOT NULL, state text NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','APPROVED','REJECTED')),
 reviewer_id uuid REFERENCES users, reason varchar(2000), reviewed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(question_id,version), UNIQUE(organization_id,id),
 FOREIGN KEY(organization_id,question_id) REFERENCES question_bank(organization_id,id)
);
CREATE INDEX question_bank_tenant_idx ON question_bank(organization_id,created_at);
CREATE INDEX question_bank_version_idx ON question_bank_versions(organization_id,question_id,version DESC);
CREATE TABLE assessment_bank_sources (
 organization_id uuid NOT NULL, assessment_id uuid NOT NULL, question_id uuid NOT NULL, version_id uuid NOT NULL,
 PRIMARY KEY(assessment_id,question_id),
 FOREIGN KEY(organization_id,assessment_id) REFERENCES assessments(organization_id,id),
 FOREIGN KEY(organization_id,version_id) REFERENCES question_bank_versions(organization_id,id)
);
CREATE FUNCTION guard_bank_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.content IS DISTINCT FROM OLD.content OR NEW.author_id IS DISTINCT FROM OLD.author_id
 OR NEW.question_id IS DISTINCT FROM OLD.question_id OR NEW.organization_id IS DISTINCT FROM OLD.organization_id
 OR NEW.version IS DISTINCT FROM OLD.version OR OLD.state <> 'DRAFT' THEN
 RAISE EXCEPTION 'Question versions are immutable; create a revision';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_bank_version BEFORE UPDATE ON question_bank_versions FOR EACH ROW EXECUTE FUNCTION guard_bank_version();
