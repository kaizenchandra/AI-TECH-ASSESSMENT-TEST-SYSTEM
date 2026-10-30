CREATE TABLE member_role_decisions (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL, user_id uuid NOT NULL,
 revision bigint NOT NULL CHECK(revision>0), actor_id uuid NOT NULL REFERENCES users,
 previous_role text NOT NULL, resulting_role text NOT NULL, reason varchar(2000) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(organization_id,user_id) REFERENCES organization_members(organization_id,user_id),
 UNIQUE(organization_id,user_id,revision)
);
CREATE INDEX member_decisions_tenant_idx ON member_role_decisions(organization_id,created_at);
