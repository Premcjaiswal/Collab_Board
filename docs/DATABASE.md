# Database design

- **DBMS:** MySQL 8 (InnoDB, utf8mb4).
- **Migrations:** Flyway, in `src/main/resources/db/migration`. The schema is created by `V1__init_schema.sql`; `V2__team_join_codes.sql` adds the nullable, unique `teams.join_code` (existing student-created teams get a code at startup).
- **Never edit an applied migration.** Add the next version (`V3__description.sql`, ...) instead.
- On startup Hibernate only *validates* the schema (`ddl-auto: validate`); it never changes tables.
- All timestamps are stored as `DATETIME(6)` in UTC.

There is deliberately **no department table**. A *class* (`classrooms`) is one teaching instance of a subject, for
example "DLCS — Semester 7 — Division A", owned by one teacher.

## Entity relationships

```
users 1─1 student_profiles            users 1─1 teacher_profiles
users 1─* verification_codes
users(teacher) 1─* classrooms 1─* enrollments *─1 users(student)
classrooms 1─* assignments 1─* rubric_criteria
classrooms 1─* projects    1─* rubric_criteria
projects 1─* teams 1─* team_members *─1 users(student)
teams 1─* team_invitations *─1 users(invitee)
teams 1─* workspace_files *─1 stored_files
teams 1─* activity_logs
assignments|projects 1─* submissions (owner = student OR team)
submissions 1─* submission_versions *─* stored_files   (via submission_version_files)
submissions 1─* evaluations 1─* evaluation_scores *─1 rubric_criteria
users 1─* notifications
```

## Tables

| Table | Purpose | Key constraints |
|---|---|---|
| `users` | Accounts for both roles | `UNIQUE(email)`; `role IN (STUDENT, TEACHER)`; `password_hash` is BCrypt (`{bcrypt}` prefix) |
| `student_profiles` | Roll number, branch, year, semester | PK = FK `user_id`; year 1–6, semester 1–12; roll numbers are intentionally not unique |
| `teacher_profiles` | College name, designation | PK = FK `user_id` |
| `verification_codes` | Email-verification and password-reset codes | Only an **HMAC-SHA256** of the code is stored. Has `expires_at`, an `attempts` counter and `consumed_at` (single use). Index on `(user_id, purpose, created_at)` for the cooldown and hourly limits. |
| `classrooms` | Classes | `UNIQUE(join_code)`; FK teacher; index on `teacher_id` |
| `enrollments` | Student ↔ class | `UNIQUE(classroom_id, student_id)` prevents duplicate enrollment |
| `assignments` | Individual assignments | FK class; `category` check; `max_marks > 0`; index `(classroom_id, deadline)` |
| `projects` | Individual or group projects | Check: a GROUP project must have a `team_formation` and a team size of 2–20 |
| `rubric_criteria` | Per-item rubric lines | Exactly one of `assignment_id` / `project_id` is set (CHECK); cascades with its item |
| `teams` | Group-project teams; shared GitHub URL and notes; `join_code` for student-created teams (V2, nullable) | `UNIQUE(project_id, name)`, `UNIQUE(join_code)` |
| `team_members` | Current members | `project_id` is stored redundantly so that `UNIQUE(project_id, student_id)` guarantees **one team per student per project** at the database level |
| `team_invitations` | Invitations: PENDING / ACCEPTED / DECLINED / CANCELLED | Indexes `(invitee_id, status)` and `(team_id, status)` |
| `stored_files` | Metadata of files on disk | `stored_name` is a random 32-hex name, `UNIQUE`; SHA-256; `context_type` (SUBMISSION / TEAM_WORKSPACE) + `context_id` drive download authorization |
| `workspace_files` | Team workspace entries | `status` ACTIVE / REPLACED / DELETED and `replaces_id` give version history and soft delete; rows are never hard-deleted |
| `submissions` | One row per (item × owner) | CHECK: exactly one item (assignment or project) and exactly one owner (student or team); `UNIQUE(assignment_id, student_id)`, `UNIQUE(project_id, student_id)`, `UNIQUE(project_id, team_id)` |
| `submission_versions` | Immutable snapshot of every (re)submission | `UNIQUE(submission_id, version_no)`; who submitted, when, the late flag, GitHub URL, notes |
| `submission_version_files` | The exact files of each version | PK `(version_id, file_id)` |
| `evaluations` | Append-only evaluation records | `is_current` marks the latest; `published` and `published_at` control what students see; `decision` check |
| `evaluation_scores` | Rubric scores | `UNIQUE(evaluation_id, criterion_id)` |
| `notifications` | In-app notifications | Unique nullable `dedupe_key` (e.g. `deadline24:A:{assignment}:{student}`) so scheduled reminders are created once; index `(user_id, created_at)` |
| `activity_logs` | Workspace audit trail (and general audit when `team_id` is null) | Index `(team_id, created_at)` |

## Design decisions

- **One submissions table for all work.** Assignments, individual projects and group projects share one model. The
  owner is either a student or a team, and the uniqueness and check constraints enforce valid combinations. This keeps
  versioning, evaluation and notifications identical for every kind of work.
- **History instead of overwrites.**
  - Every resubmission adds a `submission_versions` row.
  - Every evaluation change adds an `evaluations` row; the old one is kept with `is_current = false`.
  - Workspace replacements and deletions keep the old rows and files.
  - Team changes are recorded in `activity_logs`.
- **Files on disk, metadata in MySQL.** Uploaded bytes are stored under `STORAGE_ROOT`, outside the web root, using
  random names. The database holds metadata and a SHA-256 checksum. This keeps the database small and backups simple.
  Back up the storage folder together with the database.
- **Codes are never stored in plaintext.** Verification and reset codes are HMAC'd with `OTP_SECRET`. Passwords use
  BCrypt through Spring Security's `DelegatingPasswordEncoder`.

## Creating and configuring the database

See the README, section 2. Point `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` at the database through environment
variables or `.env`. Flyway creates every table on first start.
