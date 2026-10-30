-- One exceptional, human-authorized retake; each invitation still permits one attempt.
CREATE TABLE invitation_retakes (
 organization_id uuid NOT NULL, source_id uuid PRIMARY KEY, invitation_id uuid NOT NULL UNIQUE,
 authorized_by uuid NOT NULL REFERENCES users, reason varchar(2000) NOT NULL,
 idempotency_key uuid NOT NULL, expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(organization_id,source_id) REFERENCES invitations(organization_id,id),
 FOREIGN KEY(organization_id,invitation_id) REFERENCES invitations(organization_id,id),
 UNIQUE(organization_id,idempotency_key), CHECK(source_id<>invitation_id)
);
CREATE INDEX retake_tenant_idx ON invitation_retakes(organization_id,created_at);
