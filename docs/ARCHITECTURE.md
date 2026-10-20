# Architecture and workflows

## Overview

CollabBoard is a single Spring Boot application. The same process serves the static frontend (HTML, CSS and vanilla
JavaScript) and the JSON REST API, so the browser only ever talks to one origin.

```
Browser (HTML/CSS/vanilla JS, hash router in app.html)
   │  same-origin fetch, session cookie + X-XSRF-TOKEN header
   ▼
Spring Security filter chain (session auth, CSRF, CSP, security headers)
   ▼
Controllers (HTTP + DTO validation) → Services (business rules, authorization, transactions) → Repositories (JPA)
   │                                          │
   │                                          ├─ StorageService → files on disk (STORAGE_ROOT)
   │                                          ├─ EmailService → SMTP (Gmail)
   │                                          └─ NotificationService → notifications table
   ▼
MySQL 8 (schema managed by Flyway)
```

### Packages

| Package | Responsibility |
|---|---|
| `auth` | Registration, email verification, login and logout, password reset, OTP codes (`VerificationCodeService`), brute-force protection (`LoginAttemptService`), registration domain policy, `CurrentUser` |
| `user` | `User`, student and teacher profiles, the profile and password-change API |
| `classroom` / `enrollment` | Classes, join codes, enrollment, `ClassroomAccess` (resource-level authorization) |
| `assignment` / `project` | Creating and editing work items; per-item rubrics are stored via `evaluation.RubricService` |
| `team` | Teams, invitations, membership rules, shared workspace, activity log |
| `submission` | Submissions, immutable versions, the deadline policy (`SubmissionPolicy`), student views |
| `evaluation` | Rubrics, evaluations, scores, the teacher's evaluation API |
| `notification` | In-app notifications, the email sender, the scheduled deadline reminder job |
| `storage` | Saving files on disk, file-type allow-list and signature checks, authorized downloads |
| `dashboard` | Aggregated dashboards for students and teachers |
| `config` / `common` | Security configuration, typed properties, error handling, helpers |

## Security model

### Authentication
- Login uses a custom JSON endpoint. It checks the password first (with constant-time behaviour for unknown emails).
  Only after the password is correct does it reveal whether the email is verified, so guessers learn nothing about
  account state.
- On success it rotates the session id and stores the `SecurityContext` in the HTTP session. Sessions time out after
  60 minutes by default.
- Unverified accounts cannot sign in.
- Each session is bound to a digest of the account's password hash (`SessionCredentials`). A password change or reset
  therefore signs out the account's other sessions; the session that changed the password is re-bound. The check
  runs for every request that carries a signed-in session, not just URLs that literally start with `/api/`, because
  encoded paths such as `/%61pi/...` are routed to `/api/...`.

### Passwords and codes
- Passwords are hashed with BCrypt (`DelegatingPasswordEncoder`).
- Verification and reset codes are 6 digits from `SecureRandom`. Only an HMAC-SHA256 of each code is stored. Each code:
  - expires after 10 minutes,
  - can be used once,
  - is invalidated when a newer code is issued,
  - allows at most 5 wrong attempts.
- Code requests have a 60-second resend cooldown and a limit of 5 per hour.

### Rate limiting
- After 5 failed logins for an email within 15 minutes, that email is locked for 15 minutes. The counter is keyed on the
  stored address, so alternative spellings that the database collation matches (e.g. accented letters) share it.
- An IP address is locked after 20 failures (a higher limit, because many students can share one campus IP).

### Account enumeration
- Resend-verification and forgot-password always return the same generic response.
- Duplicate registrations are still reported (409), because users need to know an account already exists.

### CSRF
- Uses a double-submit cookie: the `XSRF-TOKEN` cookie (readable by JavaScript, SameSite=Lax) must be echoed in the
  `X-XSRF-TOKEN` header. This follows Spring Security's documented single-page-app setup.
- Login and logout are CSRF-protected too.

### Authorization (enforced on the server, never in the UI)
- **Role checks:** `@PreAuthorize` on controllers.
- **Resource checks in services:**
  - `ClassroomAccess` allows only the owning teacher or an enrolled student.
  - `TeamService.requireCurrentMember` requires current team membership *and* continued enrollment.
  - The evaluation API only works for the teacher who owns the class.
  - File downloads are checked against the file's owning context.
- Inaccessible records return 404.
- The user's identity always comes from the session.
- Request DTOs only contain fields the client may set, which prevents mass assignment.

### Uploads
- Each assignment or project has its own allowed extensions, chosen from a server allow-list.
- The per-item size limit is checked while streaming.
- The file's leading bytes must match the extension (for example `%PDF`, the ZIP signature for Office files, PNG/JPEG
  headers); text files must not contain NUL bytes.
- Files are stored under random names outside the web root.
- Downloads are always attachments with `nosniff`.

### Headers
- Strict Content-Security-Policy: `script-src 'self'`, no inline scripts or styles, `frame-ancestors 'none'`.
- `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`.

### Errors and secrets
- `GlobalExceptionHandler` returns safe messages. Stack traces are only logged.
- Secrets come from the environment or a git-ignored `.env`. They are never logged; one-time codes are logged only
  when the explicit development flag `MAIL_LOG_CODES` is on and mail is disabled.

### Frontend safety
- The UI builds the DOM with `createElement`/`textContent` (the `UI.h` helper). User-provided text is never parsed as
  HTML, which prevents XSS.

## Key workflows

### Registration and verification
1. `POST /register/{role}`: checks the domain policy and duplicate email, stores the BCrypt hash and the profile,
   issues a code and emails it.
2. The user enters the code at `POST /verify`. On success the account becomes verified and an in-app "Email verified"
   notification is created.
3. `POST /login` with the role chosen in the portal toggle.

### Classes
1. A teacher creates a class and gets an 8-character join code (no ambiguous characters such as O/0 or I/1).
2. A student enters the code at `POST /classes/join` and is enrolled immediately. The unique key prevents duplicates.
   Both the student and the teacher are notified.
3. The teacher can regenerate the code, archive the class, or remove a student. Removing a student also takes them out
   of teams in that class and cancels their invitations, but keeps their submissions.

### Assignment submission (always individual)
- Each submit uploads 1–10 files and creates a new `submission_versions` row. Earlier versions stay downloadable.
- `SubmissionPolicy` decides what is allowed:

| Situation | Result |
|---|---|
| First submission before the deadline | Allowed |
| First submission after the deadline | Allowed only if late submissions are enabled; marked **LATE** |
| Resubmission | Allowed only if resubmission is enabled **and** the deadline has not passed |
| `REVISION_REQUESTED` | Resubmission allowed, even after the deadline (the teacher asked for it) |
| `EVALUATED`, `REJECTED` | Final; no more submissions |

### Group project
1. When creating a group project the teacher must choose how teams are formed (there is no default):
   - **STUDENT_INVITE (student-created teams):** a student creates a team, becomes a member and gets a unique team join
     code. Classmates join with the code (`POST /projects/{id}/teams/join`), or any member can invite enrolled,
     verified classmates. Pending invitations count toward `maxTeamSize`. Joining with a code applies the same checks as
     accepting an invitation, under the same team row lock; any member can generate a new code.
   - **TEACHER_ASSIGNED:** the teacher creates teams and assigns students. Students cannot create, invite or leave.
2. The invitee accepts or declines. Accepting checks enrollment, the "one team per project" rule and team size again,
   under a row lock on the team. It also cancels the invitee's other pending invitations for that project.
3. All members have identical rights. In the shared workspace any member can upload, replace (old file kept), delete
   (soft delete, restorable) or restore files, and edit the GitHub URL and notes. Every action is recorded in the
   activity log.
4. Any member can submit. The version snapshots the active files, GitHub URL and notes. All members are notified.
5. The teacher evaluates the team submission once. All **current** members see the same marks and feedback. After a
   final evaluation, student changes to the workspace and membership are locked.

### Evaluation
1. The teacher filters by class, type, item and status. Picking a specific item shows the full roster, including
   students or teams who have not submitted.
2. On the detail page the teacher downloads any version, then awards marks (per criterion when the item has a rubric),
   requests a revision (feedback required) or rejects (reason required).
3. An evaluation can be saved as a draft or published right away. Each save is a new record. Only the current draft can
   be published.
4. Publishing updates the submission status and notifies the student, or every current team member.

### Notifications
In-app notifications are created for:
- email verified, password changed or reset,
- class enrollment (for the student and the teacher),
- new or changed assignments and projects,
- team invitations, responses, membership changes and withdrawn invitations,
- submission confirmations,
- revision requests, rejections and published marks.

The `DeadlineReminderJob` runs every 30 minutes. For students who have not yet submitted, it creates one "due within
24 hours" reminder per item, de-duplicated by the unique `dedupe_key`. Notifications are never created as filler.

## Decisions and assumptions

- **Single-instance deployment.** Login lockout counters are kept in memory. A cluster would need a shared store such
  as a database table.
- **Two-step file saves.** Files are written to disk before the database transaction commits. If the transaction then
  fails, an orphan file can remain on disk; it is harmless and is never referenced.
- **Deadlines** are entered in the browser's local time and stored as UTC instants. Notification text formats
  deadlines in UTC.
- **Teacher identity.** Email verification only proves control of the mailbox. Who may register as a teacher is
  controlled by `TEACHER_EMAIL_DOMAINS`. Set it in production; leaving it empty allows any domain.
- **Leaving a team.** A member who leaves loses access to the workspace and to its evaluation. Their earlier actions
  stay in the activity log and in submission versions, where they appear as the submitter. Exception: an empty team
  with no files or submissions is deleted together with its activity log when its last member leaves (or when the
  teacher deletes it).
- **Roll numbers** are stored in upper case and cannot be changed by students. They are deliberately **not unique**: they
  repeat across years, divisions and colleges. A student is identified by their account (id and verified email).
- **No Department entity** exists, as the requirements specify.
