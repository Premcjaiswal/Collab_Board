-- CollabBoard initial schema (MySQL 8, InnoDB, utf8mb4 defaults).
-- Kept to portable SQL so the same migration also runs on H2 (MySQL mode) in tests.
-- There is intentionally no department table: a class is a teaching instance of a subject.

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(190) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'TEACHER'))
);

CREATE TABLE student_profiles (
    user_id         BIGINT       NOT NULL,
    roll_number     VARCHAR(40)  NOT NULL,
    branch          VARCHAR(100),
    year_of_study   INT,
    semester        INT,
    CONSTRAINT pk_student_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_student_year CHECK (year_of_study IS NULL OR (year_of_study BETWEEN 1 AND 6)),
    CONSTRAINT ck_student_semester CHECK (semester IS NULL OR (semester BETWEEN 1 AND 12))
);

CREATE TABLE teacher_profiles (
    user_id         BIGINT       NOT NULL,
    college_name    VARCHAR(150),
    designation     VARCHAR(100),
    CONSTRAINT pk_teacher_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_teacher_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- One-time codes for email verification and password reset. Only an HMAC of the code is stored.
CREATE TABLE verification_codes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    purpose         VARCHAR(30)  NOT NULL,
    code_hash       VARCHAR(100) NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    consumed_at     DATETIME(6),
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_verification_codes PRIMARY KEY (id),
    CONSTRAINT fk_verification_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_verification_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);
CREATE INDEX ix_verification_codes_user_purpose ON verification_codes (user_id, purpose, created_at);

CREATE TABLE classrooms (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    teacher_id      BIGINT       NOT NULL,
    name            VARCHAR(150) NOT NULL,
    subject_name    VARCHAR(150) NOT NULL,
    semester        VARCHAR(40)  NOT NULL,
    division        VARCHAR(40),
    description     VARCHAR(1000),
    join_code       VARCHAR(12)  NOT NULL,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_classrooms PRIMARY KEY (id),
    CONSTRAINT uq_classrooms_join_code UNIQUE (join_code),
    CONSTRAINT fk_classrooms_teacher FOREIGN KEY (teacher_id) REFERENCES users (id)
);
CREATE INDEX ix_classrooms_teacher ON classrooms (teacher_id);

CREATE TABLE enrollments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    classroom_id    BIGINT       NOT NULL,
    student_id      BIGINT       NOT NULL,
    joined_at       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_enrollments PRIMARY KEY (id),
    CONSTRAINT uq_enrollments_class_student UNIQUE (classroom_id, student_id),
    CONSTRAINT fk_enrollments_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES users (id)
);
CREATE INDEX ix_enrollments_student ON enrollments (student_id);

CREATE TABLE assignments (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    classroom_id        BIGINT        NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT          NOT NULL,
    category            VARCHAR(30)   NOT NULL,
    deadline            DATETIME(6)   NOT NULL,
    max_marks           INT           NOT NULL,
    allowed_extensions  VARCHAR(255)  NOT NULL,
    max_file_size_mb    INT           NOT NULL,
    allow_resubmission  BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_late          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by          BIGINT        NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_assignments PRIMARY KEY (id),
    CONSTRAINT fk_assignments_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_assignments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_assignments_category CHECK (category IN ('ASSIGNMENT', 'LAB_REPORT', 'PROJECT_REPORT')),
    CONSTRAINT ck_assignments_marks CHECK (max_marks > 0),
    CONSTRAINT ck_assignments_size CHECK (max_file_size_mb > 0)
);
CREATE INDEX ix_assignments_classroom_deadline ON assignments (classroom_id, deadline);

CREATE TABLE projects (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    classroom_id        BIGINT        NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT          NOT NULL,
    deadline            DATETIME(6)   NOT NULL,
    max_marks           INT           NOT NULL,
    participation_mode  VARCHAR(20)   NOT NULL,
    team_formation      VARCHAR(30),
    max_team_size       INT,
    allowed_extensions  VARCHAR(255)  NOT NULL,
    max_file_size_mb    INT           NOT NULL,
    allow_resubmission  BOOLEAN       NOT NULL DEFAULT TRUE,
    allow_late          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by          BIGINT        NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_projects_mode CHECK (participation_mode IN ('INDIVIDUAL', 'GROUP')),
    CONSTRAINT ck_projects_formation CHECK (team_formation IS NULL OR team_formation IN ('STUDENT_INVITE', 'TEACHER_ASSIGNED')),
    CONSTRAINT ck_projects_group CHECK (participation_mode = 'INDIVIDUAL' OR (team_formation IS NOT NULL AND max_team_size BETWEEN 2 AND 20)),
    CONSTRAINT ck_projects_marks CHECK (max_marks > 0),
    CONSTRAINT ck_projects_size CHECK (max_file_size_mb > 0)
);
CREATE INDEX ix_projects_classroom_deadline ON projects (classroom_id, deadline);

-- Rubric criteria belong to exactly one assignment or one project.
CREATE TABLE rubric_criteria (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    assignment_id   BIGINT,
    project_id      BIGINT,
    title           VARCHAR(150)  NOT NULL,
    description     VARCHAR(500),
    max_marks       INT           NOT NULL,
    position        INT           NOT NULL,
    CONSTRAINT pk_rubric_criteria PRIMARY KEY (id),
    CONSTRAINT fk_rubric_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_rubric_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_rubric_owner CHECK ((assignment_id IS NULL AND project_id IS NOT NULL) OR (assignment_id IS NOT NULL AND project_id IS NULL)),
    CONSTRAINT ck_rubric_marks CHECK (max_marks > 0)
);
CREATE INDEX ix_rubric_assignment ON rubric_criteria (assignment_id);
CREATE INDEX ix_rubric_project ON rubric_criteria (project_id);

CREATE TABLE teams (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    project_id          BIGINT        NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    github_url          VARCHAR(300),
    notes               TEXT,
    notes_updated_by    BIGINT,
    notes_updated_at    DATETIME(6),
    created_by          BIGINT        NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_teams PRIMARY KEY (id),
    CONSTRAINT uq_teams_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_teams_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_teams_notes_updated_by FOREIGN KEY (notes_updated_by) REFERENCES users (id)
);

-- project_id is denormalised so the database itself guarantees one team per student per project.
CREATE TABLE team_members (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    team_id         BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    student_id      BIGINT       NOT NULL,
    joined_at       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_team_members PRIMARY KEY (id),
    CONSTRAINT uq_team_members_project_student UNIQUE (project_id, student_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_team_members_student FOREIGN KEY (student_id) REFERENCES users (id)
);
CREATE INDEX ix_team_members_student ON team_members (student_id);

CREATE TABLE team_invitations (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    team_id         BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    invitee_id      BIGINT       NOT NULL,
    invited_by      BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    responded_at    DATETIME(6),
    CONSTRAINT pk_team_invitations PRIMARY KEY (id),
    CONSTRAINT fk_invitations_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_invitations_invitee FOREIGN KEY (invitee_id) REFERENCES users (id),
    CONSTRAINT fk_invitations_invited_by FOREIGN KEY (invited_by) REFERENCES users (id),
    CONSTRAINT ck_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);
CREATE INDEX ix_invitations_invitee_status ON team_invitations (invitee_id, status);
CREATE INDEX ix_invitations_team_status ON team_invitations (team_id, status);

-- Metadata for files stored on disk. Content is never stored in the database.
CREATE TABLE stored_files (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    original_name   VARCHAR(255)  NOT NULL,
    stored_name     VARCHAR(80)   NOT NULL,
    content_type    VARCHAR(120)  NOT NULL,
    size_bytes      BIGINT        NOT NULL,
    sha256          VARCHAR(64)   NOT NULL,
    context_type    VARCHAR(30)   NOT NULL,
    context_id      BIGINT        NOT NULL,
    uploaded_by     BIGINT        NOT NULL,
    uploaded_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_stored_files PRIMARY KEY (id),
    CONSTRAINT uq_stored_files_name UNIQUE (stored_name),
    CONSTRAINT fk_stored_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_stored_files_context CHECK (context_type IN ('SUBMISSION', 'TEAM_WORKSPACE'))
);
CREATE INDEX ix_stored_files_context ON stored_files (context_type, context_id);

-- Shared group workspace files. Rows are never hard-deleted: replaced/deleted rows remain as history
-- and deleted files can be restored.
CREATE TABLE workspace_files (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    team_id         BIGINT       NOT NULL,
    file_id         BIGINT       NOT NULL,
    category        VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    replaces_id     BIGINT,
    uploaded_by     BIGINT       NOT NULL,
    uploaded_at     DATETIME(6)  NOT NULL,
    removed_by      BIGINT,
    removed_at      DATETIME(6),
    CONSTRAINT pk_workspace_files PRIMARY KEY (id),
    CONSTRAINT fk_workspace_files_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_files_file FOREIGN KEY (file_id) REFERENCES stored_files (id),
    CONSTRAINT fk_workspace_files_replaces FOREIGN KEY (replaces_id) REFERENCES workspace_files (id),
    CONSTRAINT fk_workspace_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT fk_workspace_files_removed_by FOREIGN KEY (removed_by) REFERENCES users (id),
    CONSTRAINT ck_workspace_category CHECK (category IN ('REPORT', 'PRESENTATION', 'DOCUMENTATION', 'SOURCE_CODE', 'OTHER')),
    CONSTRAINT ck_workspace_status CHECK (status IN ('ACTIVE', 'REPLACED', 'DELETED'))
);
CREATE INDEX ix_workspace_files_team_status ON workspace_files (team_id, status);

-- A submission is the record for one (assignment|project) x (student|team) pair.
CREATE TABLE submissions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    assignment_id       BIGINT,
    project_id          BIGINT,
    student_id          BIGINT,
    team_id             BIGINT,
    status              VARCHAR(30)  NOT NULL,
    current_version     INT          NOT NULL DEFAULT 0,
    last_submitted_at   DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_submissions PRIMARY KEY (id),
    CONSTRAINT uq_submissions_assignment_student UNIQUE (assignment_id, student_id),
    CONSTRAINT uq_submissions_project_student UNIQUE (project_id, student_id),
    CONSTRAINT uq_submissions_project_team UNIQUE (project_id, team_id),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id),
    CONSTRAINT fk_submissions_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_submissions_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT ck_submissions_item CHECK ((assignment_id IS NULL AND project_id IS NOT NULL) OR (assignment_id IS NOT NULL AND project_id IS NULL)),
    CONSTRAINT ck_submissions_owner CHECK ((student_id IS NULL AND team_id IS NOT NULL) OR (student_id IS NOT NULL AND team_id IS NULL)),
    CONSTRAINT ck_submissions_status CHECK (status IN ('NOT_SUBMITTED', 'SUBMITTED', 'LATE', 'EVALUATED', 'REVISION_REQUESTED', 'REJECTED'))
);
CREATE INDEX ix_submissions_status ON submissions (status);
CREATE INDEX ix_submissions_team ON submissions (team_id);
CREATE INDEX ix_submissions_student ON submissions (student_id);

-- Every (re)submission creates an immutable version; earlier versions are never overwritten.
CREATE TABLE submission_versions (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    submission_id   BIGINT        NOT NULL,
    version_no      INT           NOT NULL,
    submitted_by    BIGINT        NOT NULL,
    submitted_at    DATETIME(6)   NOT NULL,
    late            BOOLEAN       NOT NULL DEFAULT FALSE,
    github_url      VARCHAR(300),
    notes           TEXT,
    CONSTRAINT pk_submission_versions PRIMARY KEY (id),
    CONSTRAINT uq_submission_versions_no UNIQUE (submission_id, version_no),
    CONSTRAINT fk_submission_versions_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT fk_submission_versions_submitted_by FOREIGN KEY (submitted_by) REFERENCES users (id)
);

CREATE TABLE submission_version_files (
    version_id      BIGINT NOT NULL,
    file_id         BIGINT NOT NULL,
    CONSTRAINT pk_submission_version_files PRIMARY KEY (version_id, file_id),
    CONSTRAINT fk_svf_version FOREIGN KEY (version_id) REFERENCES submission_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_svf_file FOREIGN KEY (file_id) REFERENCES stored_files (id)
);

-- Evaluations are append-only: each save creates a new row and the previous one stops being current.
CREATE TABLE evaluations (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    submission_id   BIGINT         NOT NULL,
    version_id      BIGINT,
    evaluator_id    BIGINT         NOT NULL,
    decision        VARCHAR(30)    NOT NULL,
    total_marks     DECIMAL(7, 2),
    feedback        TEXT,
    reason          VARCHAR(1000),
    is_current      BOOLEAN        NOT NULL DEFAULT TRUE,
    published       BOOLEAN        NOT NULL DEFAULT FALSE,
    published_at    DATETIME(6),
    created_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_evaluations PRIMARY KEY (id),
    CONSTRAINT fk_evaluations_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE,
    CONSTRAINT fk_evaluations_version FOREIGN KEY (version_id) REFERENCES submission_versions (id),
    CONSTRAINT fk_evaluations_evaluator FOREIGN KEY (evaluator_id) REFERENCES users (id),
    CONSTRAINT ck_evaluations_decision CHECK (decision IN ('EVALUATED', 'REVISION_REQUESTED', 'REJECTED'))
);
CREATE INDEX ix_evaluations_submission ON evaluations (submission_id, is_current);

CREATE TABLE evaluation_scores (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    evaluation_id   BIGINT        NOT NULL,
    criterion_id    BIGINT        NOT NULL,
    score           DECIMAL(7, 2) NOT NULL,
    CONSTRAINT pk_evaluation_scores PRIMARY KEY (id),
    CONSTRAINT uq_evaluation_scores UNIQUE (evaluation_id, criterion_id),
    CONSTRAINT fk_scores_evaluation FOREIGN KEY (evaluation_id) REFERENCES evaluations (id) ON DELETE CASCADE,
    CONSTRAINT fk_scores_criterion FOREIGN KEY (criterion_id) REFERENCES rubric_criteria (id)
);

CREATE TABLE notifications (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    type            VARCHAR(40)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    link            VARCHAR(255),
    dedupe_key      VARCHAR(150),
    read_at         DATETIME(6),
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uq_notifications_dedupe UNIQUE (dedupe_key),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at);

-- Audit trail. team_id is set for group-workspace activity; other entries are general audit records.
CREATE TABLE activity_logs (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    team_id         BIGINT,
    actor_id        BIGINT        NOT NULL,
    action          VARCHAR(50)   NOT NULL,
    details         VARCHAR(1000),
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_activity_logs PRIMARY KEY (id),
    CONSTRAINT fk_activity_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);
CREATE INDEX ix_activity_team_created ON activity_logs (team_id, created_at);
