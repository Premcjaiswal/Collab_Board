# CollabBoard REST API

All endpoints are under `/api` and use JSON unless noted. The browser frontend calls them from the same origin.

## Conventions

**Authentication.** Signing in creates a server-side session (cookie `CBSESSION`, HttpOnly, SameSite=Lax, `Secure`
when `COOKIE_SECURE=true`). Every endpoint except the public auth endpoints needs a session. Without one, the response
is `401 UNAUTHENTICATED`.

**CSRF.** Every `POST`, `PUT` and `DELETE` must send the value of the `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header.
The cookie is issued on any request; `GET /api/auth/csrf` fetches it explicitly. A missing or invalid token returns
`403 CSRF_INVALID`.

**Authorization.**
- Role checks: a student calling a teacher-only endpoint (or the reverse) gets `403 FORBIDDEN`.
- Resource checks: a record that belongs to another class or user is reported as `404 NOT_FOUND`, so ids cannot be
  probed.
- User ids, roles and team membership always come from the session, never from the request body.

**Errors.**

```json
{ "status": 400, "code": "VALIDATION_FAILED", "message": "Please correct the highlighted fields.",
  "fieldErrors": { "email": "Enter a valid email address." } }
```

Common codes: `VALIDATION_FAILED`, `BAD_REQUEST`, `NOT_FOUND`, `FORBIDDEN`, `CONFLICT`, `RATE_LIMITED`, `CSRF_INVALID`,
`FILE_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE` (415), `INTERNAL_ERROR` (generic message; details are only logged). Error responses never contain stack
traces.

**Timestamps** are ISO-8601 instants in UTC, e.g. `2026-10-16T18:29:00Z`.

---

## Auth — `/api/auth`

| Method | Path | Access | Body / notes |
|---|---|---|---|
| GET | `/csrf` | public | Issues the `XSRF-TOKEN` cookie. Returns 204. |
| GET | `/config` | public | `{studentEmailDomains:[], teacherEmailDomains:[]}`: registration hints for the UI. |
| POST | `/register/student` | public | `{fullName, email, password, rollNumber, branch?, yearOfStudy?(1-6), semester?(1-12)}`. Returns 201 `{message, email, emailSent}`. |
| POST | `/register/teacher` | public | `{fullName, email, password, collegeName?, designation?}`. Returns 201. |
| POST | `/verify` | public | `{email, code}` (6 digits). Invalid, expired or used codes return `400 INVALID_CODE`. Too many attempts returns `429`. |
| POST | `/resend-verification` | public | `{email}`. Always returns a generic message. A resend inside the cooldown returns `429`. |
| POST | `/login` | public | `{email, password, role: STUDENT\|TEACHER}`. Returns the profile and sets the session. `401 INVALID_CREDENTIALS`; `403 EMAIL_NOT_VERIFIED` (only shown after the correct password); `429 RATE_LIMITED` after repeated failures. |
| POST | `/logout` | session | Invalidates the session and clears cookies. |
| POST | `/forgot-password` | public | `{email}`. Always returns a generic message. Emails a reset code to verified accounts. |
| POST | `/reset-password` | public | `{email, code, newPassword}` |
| GET | `/me` | session | The current profile. |

Password policy: 8–72 characters, with at least one letter and one digit. A password reset signs out every existing session of the account, whatever URL it is used with.

## Profile — `/api/profile`

| Method | Path | Body |
|---|---|---|
| GET | `/api/profile` | — |
| PUT | `/api/profile` | `{fullName, branch?, yearOfStudy?, semester?}` for students, `{fullName, collegeName?, designation?}` for teachers. Email, role and roll number cannot be changed; any such fields in the body are ignored. |
| POST | `/api/profile/password` | `{currentPassword, newPassword}`. Signs out the account's other sessions; the current one stays signed in. |

## Classes — `/api/classes`

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/classes` | any | Teachers get the classes they own; students get the classes they joined. |
| GET | `/api/classes/{id}` | owner / enrolled | `joinCode` is only included for the teacher. |
| POST | `/api/classes` | teacher | `{name, subjectName, semester, division?, description?}`. A unique 8-character join code is generated. |
| PUT | `/api/classes/{id}` | owner | Same body as POST. |
| POST | `/api/classes/{id}/archive` | owner | `{archived: true\|false}`. Archived classes are read-only for students: joining, submissions, team creation, invitations and workspace changes return `400 CLASS_ARCHIVED`; existing work stays viewable and downloadable. |
| POST | `/api/classes/{id}/regenerate-code` | owner | The old code stops working. |
| GET | `/api/classes/{id}/students` | owner | Roster. |
| DELETE | `/api/classes/{id}/students/{studentId}` | owner | Removes the student, takes them out of teams in this class and cancels their pending invitations. Submissions are kept. |
| POST | `/api/classes/join` | student | `{code}`. Enrolls immediately. `400 INVALID_CLASS_CODE`, `400 CLASS_ARCHIVED`, `409 ALREADY_ENROLLED`. |

## Assignments (always individual) — `/api/assignments`

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/assignments?classId=` | any | Scoped to the caller's classes. Students get `mySubmission`; teachers get `stats`. |
| GET | `/api/assignments/{id}` | owner / enrolled | Includes the `rubric`. |
| POST | `/api/assignments` | teacher (class owner) | See the body below. |
| PUT | `/api/assignments/{id}` | owner | Same body; `classId` cannot change. Once evaluations exist, the rubric and `maxMarks` are locked (`409 RUBRIC_LOCKED` / `409 MARKS_LOCKED`). |
| DELETE | `/api/assignments/{id}` | owner | `409 HAS_SUBMISSIONS` if anything was submitted. |
| GET | `/api/assignments/{id}/my-submission` | enrolled student | `SubmissionView` (see below). |
| POST | `/api/assignments/{id}/submissions` | enrolled student | `multipart/form-data`: `files` (1–10), `notes?`. Creates a new version. |

Body for POST and PUT:

```json
{ "classId": 1, "title": "Lab 3", "description": "...", "category": "ASSIGNMENT|LAB_REPORT|PROJECT_REPORT",
  "deadline": "2026-10-16T18:29:00Z", "maxMarks": 10, "allowedExtensions": "pdf, docx",
  "maxFileSizeMb": 10, "allowResubmission": true, "allowLate": false,
  "rubric": [ { "title": "Correctness", "description": "...", "maxMarks": 6 },
              { "title": "Report", "maxMarks": 4 } ] }
```

Rubric criteria are optional. If given, their marks must add up to `maxMarks`. Allowed extensions must be on the
server's allow-list: pdf, doc(x), ppt(x), xls(x), od[tps], zip, rar, 7z, gz, tar, png, jpg/jpeg, gif, ipynb, and
common text/source formats.

## Projects — `/api/projects`

Same shape as assignments, without `category` and with these extra fields:

```json
{ "participationMode": "INDIVIDUAL|GROUP", "teamFormation": "STUDENT_INVITE|TEACHER_ASSIGNED", "maxTeamSize": 4 }
```

`participationMode` is required. For `GROUP` the teacher must choose `teamFormation` and `maxTeamSize` (2–20)
explicitly; there are no defaults (`400 TEAM_FORMATION_REQUIRED`, `400 TEAM_SIZE_REQUIRED`). `STUDENT_INVITE` means student-created teams that share a join code.

| Method | Path | Access | Notes |
|---|---|---|---|
| GET / POST / PUT / DELETE | as for assignments | | `participationMode` and `teamFormation` cannot change once teams or submissions exist. On PUT, omitted `teamFormation`/`maxTeamSize` keep their current values. |
| GET | `/api/projects/{id}/my-submission` | enrolled student | Individual project: the student's own submission. Group project: the team's submission. |
| POST | `/api/projects/{id}/submissions` | enrolled student (individual projects only) | Multipart: `files?`, `githubUrl?`, `notes?`. At least one file or a GitHub URL is required. |

## Teams, invitations and the shared workspace (group projects)

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/api/projects/{id}/teams` | owner teacher | All teams, with members and pending invitations. |
| GET | `/api/projects/{id}/my-team` | enrolled student | `{team \| null, invitations: [...]}` |
| POST | `/api/projects/{id}/teams` | student (STUDENT_INVITE) or owner teacher | `{name, studentIds?}`. `studentIds` is only used for teachers. A student who creates a team becomes a member. Teams in STUDENT_INVITE projects get a unique 8-character `joinCode`; teacher-assigned teams have none. `TeamDto.joinCode` is only returned to team members and the class teacher. |
| POST | `/api/projects/{id}/teams/join` | enrolled student (STUDENT_INVITE) | `{code}` (case, spaces and hyphens ignored). Joins the team with that code. Same checks as accepting an invitation: `400 INVALID_TEAM_CODE` (unknown or another project's code), `409 ALREADY_IN_TEAM`, `409 TEAM_FULL` (members + pending invitations; the joiner's own pending invitation from that team is not counted twice and becomes ACCEPTED), `409 TEAM_LOCKED`, `400 CLASS_ARCHIVED`. Other pending invitations for the project are cancelled. |
| POST | `/api/teams/{teamId}/join-code` | any current member (STUDENT_INVITE) | Generates a new code; the old one stops working. |
| GET | `/api/projects/{id}/eligible-students` | team member or teacher | Enrolled students not in any team for the project. |
| POST | `/api/teams/{teamId}/invitations` | any current member | `{email}`: a registered, verified student enrolled in the class. Pending invitations reserve slots: `409 TEAM_FULL` when members + pending = max. |
| GET | `/api/invitations` | student | The caller's pending invitations. |
| POST | `/api/invitations/{id}/accept` | invitee | Checks enrollment and team size again; cancels the invitee's other pending invitations for the project. |
| POST | `/api/invitations/{id}/decline` | invitee | |
| DELETE | `/api/invitations/{id}` | any current member / owner teacher | Withdraws a pending invitation. |
| POST | `/api/teams/{teamId}/leave` | member (STUDENT_INVITE) | Submission versions and the activity log are kept. Exception: when the last member leaves a team with no files or submissions, the team is removed together with its activity log. |
| POST | `/api/teams/{teamId}/members` | owner teacher | `{studentId}` |
| DELETE | `/api/teams/{teamId}/members/{studentId}` | owner teacher | |
| DELETE | `/api/teams/{teamId}` | owner teacher | Only teams without files or submissions can be deleted. |
| GET | `/api/teams/{teamId}/workspace` | current member (editable) / owner teacher (read-only) | Team, active files, file history, GitHub URL, notes, activity log, `submission` (published evaluations only). |
| POST | `/api/teams/{teamId}/files` | any current member | Multipart: `file`, `category` (REPORT, PRESENTATION, DOCUMENTATION, SOURCE_CODE, OTHER), `replaceId?`. Replacing keeps the old file as REPLACED history. |
| DELETE | `/api/teams/{teamId}/files/{id}` | any current member | Soft delete; the file can be restored. |
| POST | `/api/teams/{teamId}/files/{id}/restore` | any current member | |
| PUT | `/api/teams/{teamId}/details` | any current member | `{githubUrl?, notes?}`. The URL must look like `https://github.com/owner/repo`. |
| POST | `/api/teams/{teamId}/submit` | any current member | `{notes?}`. Snapshots the active files, GitHub URL and notes into a new version. |

Student changes to the workspace and team membership are locked (`409`) once the team's submission is EVALUATED or
REJECTED.

## Files

| Method | Path | Access |
|---|---|---|
| GET | `/api/files/{id}/download` | Individual submission files: the owning student (while enrolled) or the class teacher. Workspace files: current team members (while enrolled) or the class teacher. |

Files are always sent as attachments (`application/octet-stream`, `X-Content-Type-Options: nosniff`,
`Cache-Control: no-store, private`).

## Submissions & evaluation (teacher) — `/api/evaluation`

| Method | Path | Notes |
|---|---|---|
| GET | `/api/evaluation/submissions?classId=&kind=ASSIGNMENT\|PROJECT&itemId=&status=` | Without `itemId`: all submitted work in the teacher's classes. With `kind` + `itemId`: the full roster, including students or teams who have not submitted. `status` is a submission status or `PENDING` (SUBMITTED + LATE). |
| GET | `/api/evaluation/submissions/{id}` | Item, rubric, owner (student or team members), all versions with files, and all evaluations including drafts. |
| POST | `/api/evaluation/submissions/{id}/evaluations` | See the body below. |
| POST | `/api/evaluation/evaluations/{id}/publish` | Publishes the current draft. `409 EVALUATION_OUTDATED` if a newer evaluation exists or the student submitted a newer version after the draft was saved. |

Body for saving an evaluation:

```json
{ "decision": "EVALUATED|REVISION_REQUESTED|REJECTED",
  "marks": 8.5,
  "scores": [ { "criterionId": 3, "score": 5.5 } ],
  "feedback": "...", "reason": "...", "publish": true }
```

Rules:
- **EVALUATED:** if the item has a rubric, every criterion needs a score between 0 and that criterion's maximum, and
  the total is their sum. Without a rubric, `marks` must be between 0 and `maxMarks`. At most 2 decimal places.
- **REVISION_REQUESTED** requires `feedback`. **REJECTED** requires `reason`.
- Each save creates a new record and marks the previous one as not current.
- Publishing sets the submission status and notifies the student, or every current team member.

## Dashboard and notifications

| Method | Path | Notes |
|---|---|---|
| GET | `/api/dashboard` | Role-specific summary for the caller. |
| GET | `/api/notifications?unreadOnly=&limit=` | Newest first. |
| GET | `/api/notifications/unread-count` | `{count}` |
| POST | `/api/notifications/{id}/read` | The caller's own notifications only. |
| POST | `/api/notifications/read-all` | |

## Shared response shapes

`SubmissionView` (what a student sees for their own or their team's work):

```json
{ "submissionId": 5, "status": "NOT_SUBMITTED|SUBMITTED|LATE|EVALUATED|REVISION_REQUESTED|REJECTED",
  "deadline": "...", "pastDeadline": false, "canSubmit": true, "nextSubmissionLate": false, "blockedReason": null,
  "versions": [ { "versionNo": 2, "submittedBy": "Ravi Kumar", "submittedAt": "...", "late": false,
                  "githubUrl": null, "notes": null,
                  "files": [ { "id": 9, "name": "report.pdf", "sizeBytes": 1234, "downloadUrl": "/api/files/9/download" } ] } ],
  "evaluations": [ { "decision": "EVALUATED", "totalMarks": 8.5, "maxMarks": 10, "feedback": "...",
                     "scores": [ { "criterion": "Correctness", "maxMarks": 6, "score": 5.5 } ] } ] }
```
