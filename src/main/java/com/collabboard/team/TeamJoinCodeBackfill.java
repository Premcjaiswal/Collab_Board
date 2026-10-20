package com.collabboard.team;

import com.collabboard.common.JoinCodes;
import com.collabboard.project.TeamFormation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives student-created teams that predate team join codes (migration V2) a code, once, at startup.
 * Existing members, invitations and files are untouched.
 */
@Component
public class TeamJoinCodeBackfill {

    private static final Logger log = LoggerFactory.getLogger(TeamJoinCodeBackfill.class);

    private final TeamRepository teams;

    public TeamJoinCodeBackfill(TeamRepository teams) {
        this.teams = teams;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void assignMissingCodes() {
        List<Team> missing = teams.findByJoinCodeIsNullAndProjectTeamFormation(TeamFormation.STUDENT_INVITE);
        for (Team t : missing) {
            t.setJoinCode(JoinCodes.generate(teams::existsByJoinCode));
            teams.flush(); // so the next uniqueness check sees this code
        }
        if (!missing.isEmpty()) {
            log.info("Assigned join codes to {} existing student-created team(s).", missing.size());
        }
    }
}
