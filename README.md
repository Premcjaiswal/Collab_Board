# CollabBoard

CollabBoard is a college portal for submitting assignments and evaluating projects. Teachers create classes and publish
assignments and projects. Students join classes with a code, submit their work (individually, or as a team for group
projects) and receive marks and feedback.

> CollabBoard is **not** a Kanban or task-management tool. It is an academic submission and evaluation portal.

- **Backend:** Java 21, Spring Boot 3.5.5 (Web, Security, Data JPA/Hibernate, Validation, Mail), Flyway, Maven
- **Database:** MySQL 8 (schema managed by Flyway migrations)
- **Frontend:** plain HTML5, CSS3 and vanilla JavaScript (no framework), served by the same Spring Boot application
- **Architecture:** a single Spring Boot application (monolith). See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

More documentation:
[API reference](docs/API.md) · [Database design](docs/DATABASE.md) · [Architecture and workflows](docs/ARCHITECTURE.md)

---

## 1. Required software

| Software | Version used/tested | Notes |
|---|---|---|
| JDK | **21** (tested with Temurin 21.0.11) | `JAVA_HOME` must point to a JDK 21+. Maven must run on JDK 21 or newer. |
| Maven | 3.9.x (tested 3.9.12) | |
| MySQL Server | **8.0** (tested 8.0.45) | MySQL 8.4 LTS should also work. |
| Browser | Any current Chrome, Edge, Firefox or Safari | |

Check your setup:

```bash
java -version
```

```bash
mvn -version
```

```bash
mysql --version
```

## 2. MySQL database setup

Create a database and a dedicated user. Choose your own strong password; never commit it.

```sql
CREATE DATABASE collabboard CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'collabboard'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT ALL PRIVILEGES ON collabboard.* TO 'collabboard'@'localhost';
FLUSH PRIVILEGES;
```

Run this as the MySQL root user, for example with `mysql -u root -p`.

You do **not** need to create any tables. On startup, Flyway applies the migrations in
`src/main/resources/db/migration` (currently `V1__init_schema.sql`, `V2__team_join_codes.sql` and `V3__file_blobs.sql`). Hibernate then checks that the entities match the
schema (`ddl-auto: validate`).

## 3. Configuration (environment variables)

Secrets are never stored in source control. All settings come from environment variables. For convenience, they can
also come from a git-ignored `.env` file in the directory you start the application from (it is loaded with
`spring.config.import`).

```bash
cp .env.example .env
```

Then edit `.env`. The most important settings:

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:mysql://localhost:3306/collabboard?...` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | `collabboard` / *(empty)* |
| `OTP_SECRET` | Secret key used to HMAC one-time codes. **Set this in every real deployment.** | random per process |
| `MAIL_ENABLED` | Set to `true` to actually send email | `false` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP login (Gmail address and app password) | *(empty)* |
| `MAIL_FROM` | Sender address | `collabboard.verify@gmail.com` |
| `STUDENT_EMAIL_DOMAINS` / `TEACHER_EMAIL_DOMAINS` | Comma-separated college email domains allowed to register. Leave empty to allow any domain (development only). | *(empty)* |
| `COOKIE_SECURE` | `true` when served over HTTPS | `false` |
| `STORAGE_ROOT` | Folder for uploaded files. Keep it outside the web root. | `./data/uploads` |
| `MAIL_LOG_CODES` | **Development only:** when mail is disabled, print one-time codes to the log | `false` |
| `MAIL_TRANSPORT` | `smtp` (Gmail etc.) or `brevo` (HTTPS email API, for hosts that block outbound SMTP) | `smtp` |
| `BREVO_API_KEY` | Brevo API key, only used when `MAIL_TRANSPORT=brevo` | *(empty)* |
| `STORAGE_TYPE` | `filesystem` (files under `STORAGE_ROOT`) or `database` (file contents in MySQL, for hosts without a persistent disk) | `filesystem` |
| `FORWARD_HEADERS_STRATEGY` | `native` when running behind a hosting platform's proxy (real client IP for the login lockout, HTTPS detection) | `none` |
| `PORT` | Used when `SERVER_PORT` is not set (hosting platforms set it) | *(unset)* |

`.env.example` lists every setting, including OTP expiry and attempt limits, the resend cooldown, the login lockout
and upload size limits.

Generate an OTP secret with:

```bash
openssl rand -hex 32
```

## 4. Email (Gmail SMTP with an App Password)

> On hosts that block outbound SMTP (such as Render's free tier), set `MAIL_TRANSPORT=brevo` and `BREVO_API_KEY`
> instead; see [Deploying to Render](#10-deploying-to-render-free-tier).

Verification and password-reset codes are emailed from **collabboard.verify@gmail.com**. Gmail will **not** accept the
normal account password over SMTP. You need an **App Password**:

1. Sign in to the `collabboard.verify@gmail.com` Google account and turn on **2-Step Verification**
   (Google Account → Security).
2. Open **Google Account → Security → App passwords** (https://myaccount.google.com/apppasswords). Create an app
   password named, for example, "CollabBoard".
3. Copy the 16-character password and put it in `.env` (or the environment):
   ```properties
   MAIL_ENABLED=true
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=collabboard.verify@gmail.com
   MAIL_PASSWORD=<16-character app password, without spaces>
   MAIL_FROM=collabboard.verify@gmail.com
   ```
4. Restart the application, register a test account and confirm that the code arrives.

How delivery is handled:

- The connection uses STARTTLS on port 587. Connect, read and write timeouts are 10 s each.
- If sending fails, the account is still created. The UI says the email could not be sent and offers **Resend code**.
  The error is logged without credentials or codes.
- **Local development without SMTP:** keep `MAIL_ENABLED=false` and set `MAIL_LOG_CODES=true`. Codes are then printed
  to the server log as `[DEV MAIL - delivery disabled] ... code=123456`. Never enable this in production.

> Email delivery through Gmail was **not** tested while building this project, because no app password was
> available. Everything else in the email flow was tested with mail disabled and logged codes. See
> [Verification status](#9-verification-status).

## 5. Start the backend (which also serves the frontend)

Load your environment (or create `.env` as described above), then build and run:

```bash
mvn clean package
```

```bash
java -jar target/collabboard-1.0.0.jar
```

During development you can also use:

```bash
mvn spring-boot:run
```

Open **http://localhost:8080**.

### Serving the frontend

You do not need a separate frontend server. The pages are static files in `src/main/resources/static` and are served
by Spring Boot from the same origin as the API. This keeps session cookies and CSRF protection simple.

| Page | Purpose |
|---|---|
| `/` (`index.html`) | Sign in (choose Student or Teacher) |
| `/register.html` | Student or teacher registration |
| `/verify.html` | Enter the email verification code |
| `/forgot-password.html`, `/reset-password.html` | Password reset |
| `/app.html#/…` | The signed-in portal (role-specific navigation) |

### First steps

1. Register a **teacher**, verify the email and sign in. Create a class and copy its 8-character join code.
2. Register a **student**, verify the email and sign in. Join the class using the code.
3. As the teacher, publish an assignment or a project. As the student, submit work.
4. As the teacher, open **Submissions & Evaluation** to grade the work and publish the results.

## 6. Running the tests

```bash
mvn test
```

The tests start the full Spring context with the real security filter chain. They run the same Flyway migration
against an in-memory H2 database in MySQL mode (`src/test/resources/application-test.yml`). Email is replaced with a
recording test double, so no MySQL server or SMTP account is needed.

| Test class | Covers |
|---|---|
| `AuthFlowTest` | Registration, email verification (single-use codes, attempt limits, resend cooldown), BCrypt hashing, duplicate email, login with wrong password or wrong portal, lockout, forgot/reset password, CSRF, 401 for anonymous requests, logout, password change, protected profile fields, no stack traces in responses |
| `ClassroomTest` | Join codes, immediate enrollment, duplicate enrollment, invalid and archived codes, code regeneration, multiple classes, teacher/student isolation, removing a student, enrollment notifications |
| `AssignmentSubmissionTest` | Submissions and versions, file type, content and size checks, download authorization, privacy between students, deadline and late policy, disabled resubmission, assignment validation |
| `TeamJoinCodeTest` | Explicit project type and team formation, joining student-created teams with a code (eligibility, other class, wrong/other-project code, duplicate membership, team size with pending invitations, teacher-assigned teams, regeneration, locks, backfill of existing teams) |
| `RollNumberTest` | Students may share a roll number and remain separate accounts |
| `GroupProjectTest` | Invitations reserve team slots, pending invitees have no workspace access, one team per project, class eligibility, equal permissions for all members (upload, replace, delete, restore, details, submit), shared rubric marks for the team, locks after evaluation, leaving a team, teacher-assigned teams, individual projects |
| `EvaluationTest` | Marks range and required reason/feedback, draft vs published evaluations, evaluation history, revision requests, final rejection, teacher-only access, roster including students who have not submitted, deadline reminders created once, dashboard data scoping |
| `QaRegressionTest` | Regressions from the end-to-end QA and audit: last member leaving, stale draft publish, marks/rubric lock, archived classes read-only, 415 for unsupported content types, password change/reset signing out other sessions (including via an encoded `/%61pi/` path), team settings kept on edit, outsiders get 404, dashboard after removal |
| `UnitRulesTest` | Submission policy matrix, file signatures, permitted-extension parsing |
| `ApplicationContextTest` | Context starts; migrations and entity mappings are consistent |

## 7. Project layout

```
pom.xml
.env.example                 configuration template (copy to .env, never commit .env)
src/main/java/com/collabboard/
  auth/          registration, login, OTP codes, rate limiting, current user
  user/          users, student/teacher profiles, profile & password endpoints
  classroom/     classes, join codes, class access checks
  enrollment/    class enrollments
  assignment/    individual assignments
  project/       individual and group projects
  team/          teams, invitations, shared workspace, activity log
  submission/    submissions, immutable versions, deadline policy
  evaluation/    rubrics, evaluations, scores, teacher evaluation API
  notification/  in-app notifications, email sender, deadline reminders
  storage/       file storage, file-type validation, authorized downloads
  dashboard/     student and teacher dashboards
  config/        security configuration and typed properties
  common/        error handling and helpers
src/main/resources/
  application.yml
  db/migration/  Flyway SQL migrations
  static/        HTML, CSS, JS frontend (Emerald & Cream theme in css/styles.css;
                 self-hosted Figtree and Cormorant Garamond fonts in fonts/, SIL OFL 1.1)
src/test/        integration and unit tests
docs/            API, database and architecture documentation
```

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `Communications link failure` / `Connection refused` on startup | MySQL is not running, or `DB_URL` has the wrong host or port. On Windows, start the `MySQL80` service (this needs an admin prompt): `net start MySQL80`. |
| `Access denied for user 'collabboard'@'localhost'` | Wrong `DB_USERNAME`/`DB_PASSWORD`, or the GRANT was not run. Re-run the SQL in section 2. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `DB_URL` for local, non-SSL connections, or set up SSL. |
| `Schema-validation: missing table ...` | The database was changed by hand. Use an empty database so Flyway can create the schema. |
| Flyway `Validate failed: Migrations have failed validation` | A migration file was edited after it was applied. Revert the edit and add a new migration (`V4__...sql`) instead. |
| `release version 21 not supported` | Maven is running on an older JDK. Point `JAVA_HOME` to JDK 21. |
| Registration says the email "could not be sent" | `MAIL_ENABLED=false`, or the SMTP login failed. Check the log for `Failed to send ... email`. For Gmail, use an **App Password** (see section 4). `535-5.7.8 Username and Password not accepted` means a normal password was used or 2-Step Verification is off. |
| Startup log shows `SMTP check FAILED ... 534-5.7.14 Please log in via your web browser` | Google is holding the sign-in for a security check. Sign in to the Gmail account in a browser, approve any "Was this you?" or "Review security activity" prompt, then open https://accounts.google.com/DisplayUnlockCaptcha and click **Continue**. Retry within about 10 minutes. No restart is needed, because each email logs in again; the startup check only runs at startup. |
| Verification code is rejected | Codes expire after `OTP_TTL_MINUTES` (default 10) and only the newest code works. After 5 wrong attempts, request a new code. If `OTP_SECRET` is unset, a restart invalidates outstanding codes. |
| "Too many failed sign-in attempts" | Wait `LOGIN_LOCK_MINUTES` (default 15), or restart the server (the lockout is kept in memory). |
| `403 CSRF_INVALID` when calling the API with curl | GET `/api/auth/csrf` first. Then send the `XSRF-TOKEN` cookie value back in the `X-XSRF-TOKEN` header, together with the cookies. |
| Session cookie not kept when using HTTPS behind a proxy | Set `COOKIE_SECURE=true` and make sure the proxy forwards to the app on the same origin. |
| Upload rejected as "does not look like a valid .pdf" | The file's content does not match its extension (for example, a renamed file). Export it in the correct format. |
| `413 FILE_TOO_LARGE` | The file is larger than the item's limit, or than `MAX_UPLOAD_FILE_SIZE`/`MAX_UPLOAD_REQUEST_SIZE`. |

## 9. Verification status

Results of the final pre-release audit (all run against this codebase, not assumed):

- `mvn clean package`: **81 tests, 0 failures** across 12 test classes (H2 in MySQL mode), including database file
  storage and the Brevo email transport.
- **MySQL 8.0.45 (MySQL80 Windows service):** Flyway applied `V1__init_schema.sql` and `V2__team_join_codes.sql`;
  Hibernate schema validation passed. Existing user data was fingerprinted before the audit and was identical after it.
- **Live API suites on a separate instance against MySQL** (mail disabled, codes read from the log; all QA data removed
  afterwards):
  - 312 end-to-end checks: auth, OTP, lockout, CSRF, session fixation, classes and join codes, assignments, upload
    validation, download permissions, evaluation, marks and feedback, deadlines and late rules, group projects,
    invitations, workspace, teacher-assigned teams, removal from a class, archived classes, security headers, and data
    and file persistence after a hard kill and restart.
  - 42 checks for explicit project type, team formation and size, and team join codes (join, wrong code, full team,
    code regeneration).
  - 30 supplementary checks: OTP attempt exhaustion, resend cooldown, notifications (list, unread count, mark read,
    read all, isolation), teacher and student profile validation, logout.
- **Browser (in-app Chromium):**
  - Student and teacher registration, OTP verification, sign-in, logout, forgot/reset password, profile edit and
    password change through the real forms.
  - Every route for both roles at 1280px and 375px: no JavaScript errors, no CSP violations, no failed requests, no
    horizontal overflow. The Emerald & Cream theme and the self-hosted fonts are active.
- **Gmail OTP delivery** was confirmed with a Google App Password configured in `.env`: the startup SMTP check passed and
  a verification code arrived in a real inbox.

Known limitations (not reproduced, documented for future work):

- A few rare races are guarded by validation but not by row locks: publishing a draft at the same moment the student
  resubmits; lowering a project's maximum team size while a classmate joins; accepting an invitation while it is being
  withdrawn. Each needs two requests in the same instant.
- Login lockout counters are in memory (single-instance deployment); bursts of parallel login attempts were not
  stress-tested.
- The deadline reminder job is covered by an integration test, not observed live (it runs every 30 minutes).

## 10. Deploying to Render (free tier)

The repository includes a `Dockerfile` and a `render.yaml` Blueprint. The same jar runs locally and on Render; only
environment variables differ. Render's free tier has no MySQL, no persistent disk and blocks outbound SMTP, so the
deployment uses:

| Need | Free service | Setting |
|---|---|---|
| MySQL 8 | [Aiven](https://aiven.io) free MySQL (1 GB) | `DB_URL=jdbc:mysql://<host>:<port>/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8`, `DB_USERNAME`, `DB_PASSWORD` |
| Email (OTP codes) | [Brevo](https://www.brevo.com) free API (300 emails/day) with a verified sender | `MAIL_TRANSPORT=brevo`, `BREVO_API_KEY`, `MAIL_FROM` |
| Uploaded files | stored in MySQL | `STORAGE_TYPE=database`, uploads capped at 10 MB |

Steps:

1. Create the Aiven MySQL service and the Brevo account; verify the sender email in Brevo and create an API key.
2. In Render choose **New → Blueprint**, select this repository and apply `render.yaml`.
3. Enter the values Render asks for (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `BREVO_API_KEY`, `MAIL_FROM`,
   `APP_BASE_URL`). `OTP_SECRET` is generated by Render. Never commit these values.
4. Deploy. Flyway creates the schema on first start; the health check is `/api/auth/csrf`.

Free-tier behaviour: the service sleeps after 15 minutes without traffic and takes about a minute to wake; signed-in
sessions are kept in memory, so a restart signs users out (data and files are kept in MySQL). Run a single instance.
