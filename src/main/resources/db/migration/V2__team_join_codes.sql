-- Student-created teams get a unique join code that members share with classmates.
-- Nullable: teacher-assigned teams have no code. Existing student-created teams are given a code at application
-- startup (TeamJoinCodeBackfill), so no existing rows or data are changed here.
ALTER TABLE teams ADD COLUMN join_code VARCHAR(12);
CREATE UNIQUE INDEX uq_teams_join_code ON teams (join_code);
