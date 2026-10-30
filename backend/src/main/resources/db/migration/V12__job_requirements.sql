CREATE TABLE jobs (
 id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations, author_id uuid NOT NULL REFERENCES users,
 title varchar(200) NOT NULL, department varchar(120) NOT NULL, description text NOT NULL,
 experience_level varchar(80) NOT NULL, min_years numeric(4,1) NOT NULL, max_years numeric(4,1) NOT NULL,
 difficulty text NOT NULL, duration_minutes integer NOT NULL, passing_percent numeric(5,2) NOT NULL,
 available_from timestamptz NOT NULL, latest_start timestamptz NOT NULL, completion_deadline timestamptz NOT NULL,
 state text NOT NULL DEFAULT 'OPEN' CHECK(state IN ('OPEN','ARCHIVED')), revision bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(organization_id,id), CHECK(min_years>=0 AND max_years>=min_years AND max_years<=60),
 CHECK(duration_minutes BETWEEN 1 AND 480), CHECK(passing_percent BETWEEN 0 AND 100), CHECK(available_from<latest_start AND latest_start<completion_deadline)
);
CREATE TABLE job_skills (
 organization_id uuid NOT NULL, job_id uuid NOT NULL, skill text NOT NULL, weight integer NOT NULL CHECK(weight BETWEEN 0 AND 100),
 required boolean NOT NULL, proficiency varchar(120) NOT NULL, PRIMARY KEY(job_id,skill),
 FOREIGN KEY(organization_id,job_id) REFERENCES jobs(organization_id,id)
);
CREATE INDEX jobs_tenant_idx ON jobs(organization_id,state,created_at);
CREATE TABLE assessment_jobs (
 organization_id uuid NOT NULL, assessment_id uuid PRIMARY KEY, job_id uuid NOT NULL,
 FOREIGN KEY(organization_id,assessment_id) REFERENCES assessments(organization_id,id),
 FOREIGN KEY(organization_id,job_id) REFERENCES jobs(organization_id,id)
);
ALTER TABLE assessment_versions ADD COLUMN job_context jsonb NOT NULL DEFAULT '{}';
