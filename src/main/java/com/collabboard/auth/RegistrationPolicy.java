package com.collabboard.auth;

import com.collabboard.common.Texts;
import com.collabboard.config.AppProperties;
import com.collabboard.user.Role;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Configurable registration policy: which college email domains may register for each role.
 * Email verification proves control of the address only; this policy is what restricts who may
 * register as a teacher.
 */
@Component
public class RegistrationPolicy {

    private final Set<String> studentDomains;
    private final Set<String> teacherDomains;

    public RegistrationPolicy(AppProperties props) {
        this.studentDomains = Texts.parseExtensions(props.registration().studentEmailDomains());
        this.teacherDomains = Texts.parseExtensions(props.registration().teacherEmailDomains());
    }

    public boolean isAllowed(Role role, String email) {
        Set<String> domains = role == Role.TEACHER ? teacherDomains : studentDomains;
        if (domains.isEmpty()) {
            return true;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        String domain = email.substring(at + 1);
        return domains.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    public Set<String> domainsFor(Role role) {
        return role == Role.TEACHER ? teacherDomains : studentDomains;
    }
}
