CREATE TABLE readiness_requests (
 id uuid PRIMARY KEY, invitation_id uuid NOT NULL REFERENCES invitations,
 candidate_id uuid NOT NULL REFERENCES users, reason varchar(2000) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
