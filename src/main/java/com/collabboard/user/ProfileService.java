package com.collabboard.user;

import com.collabboard.common.ApiException;
import com.collabboard.common.Texts;
import com.collabboard.notification.NotificationService;
import com.collabboard.notification.NotificationType;
import com.collabboard.user.ProfileDtos.ChangePasswordRequest;
import com.collabboard.user.ProfileDtos.ProfileResponse;
import com.collabboard.user.ProfileDtos.UpdateProfileRequest;
import com.collabboard.user.ProfileDtos.UserSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final UserRepository users;
    private final StudentProfileRepository studentProfiles;
    private final TeacherProfileRepository teacherProfiles;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;

    public ProfileService(UserRepository users, StudentProfileRepository studentProfiles,
                          TeacherProfileRepository teacherProfiles, PasswordEncoder passwordEncoder,
                          NotificationService notifications) {
        this.users = users;
        this.studentProfiles = studentProfiles;
        this.teacherProfiles = teacherProfiles;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        return toResponse(u);
    }

    @Transactional
    public ProfileResponse update(Long userId, UpdateProfileRequest req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        u.setFullName(req.fullName().trim());
        if (u.getRole() == Role.STUDENT) {
            StudentProfile p = studentProfiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
            p.setBranch(Texts.trimToNull(req.branch()));
            p.setYearOfStudy(req.yearOfStudy());
            p.setSemester(req.semester());
        } else {
            TeacherProfile p = teacherProfiles.findById(userId).orElseThrow(() -> ApiException.notFound("Profile"));
            p.setCollegeName(Texts.trimToNull(req.collegeName()));
            p.setDesignation(Texts.trimToNull(req.designation()));
        }
        return toResponse(u);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect.");
        }
        if (passwordEncoder.matches(req.newPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "New password must be different from the current password.");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        notifications.notify(u, NotificationType.ACCOUNT, "Password changed",
                "Your account password was changed. If this wasn't you, reset your password immediately.", "#/settings");
    }

    @Transactional(readOnly = true)
    public ProfileResponse toResponse(User u) {
        if (u.getRole() == Role.STUDENT) {
            StudentProfile p = studentProfiles.findById(u.getId()).orElse(null);
            return new ProfileResponse(u.getId(), u.getEmail(), u.getRole(), u.getFullName(), u.isEmailVerified(),
                    u.getCreatedAt(), p == null ? null : p.getRollNumber(), p == null ? null : p.getBranch(),
                    p == null ? null : p.getYearOfStudy(), p == null ? null : p.getSemester(), null, null);
        }
        TeacherProfile p = teacherProfiles.findById(u.getId()).orElse(null);
        return new ProfileResponse(u.getId(), u.getEmail(), u.getRole(), u.getFullName(), u.isEmailVerified(),
                u.getCreatedAt(), null, null, null, null,
                p == null ? null : p.getCollegeName(), p == null ? null : p.getDesignation());
    }

    /** Builds summaries for a set of users, including roll numbers for students. */
    @Transactional(readOnly = true)
    public List<UserSummary> summaries(Collection<User> list) {
        Map<Long, String> rolls = studentProfiles.findByUserIdIn(list.stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, StudentProfile::getRollNumber));
        return list.stream()
                .map(u -> new UserSummary(u.getId(), u.getFullName(), u.getEmail(), rolls.get(u.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, UserSummary> summaryMap(Collection<User> list) {
        return summaries(list).stream().collect(Collectors.toMap(UserSummary::id, Function.identity(), (a, b) -> a));
    }

    public UserSummary summary(User u) {
        return summaries(List.of(u)).getFirst();
    }
}
