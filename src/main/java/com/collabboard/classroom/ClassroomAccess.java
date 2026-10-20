package com.collabboard.classroom;

import com.collabboard.auth.AuthUser;
import com.collabboard.common.ApiException;
import com.collabboard.enrollment.EnrollmentRepository;
import org.springframework.stereotype.Component;

/**
 * Resource-level authorization for classes. Inaccessible classes are reported as "not found"
 * so that identifiers cannot be probed (IDOR protection).
 */
@Component
public class ClassroomAccess {

    private final ClassroomRepository classrooms;
    private final EnrollmentRepository enrollments;

    public ClassroomAccess(ClassroomRepository classrooms, EnrollmentRepository enrollments) {
        this.classrooms = classrooms;
        this.enrollments = enrollments;
    }

    /** The class, if the user is its teacher. */
    public Classroom requireOwned(Long classId, AuthUser user) {
        Classroom c = classrooms.findById(classId).orElseThrow(() -> ApiException.notFound("Class"));
        if (!user.isTeacher() || !c.getTeacher().getId().equals(user.id())) {
            throw ApiException.notFound("Class");
        }
        return c;
    }

    /** The class, if the user teaches it or is enrolled in it. */
    public Classroom requireMember(Long classId, AuthUser user) {
        Classroom c = classrooms.findById(classId).orElseThrow(() -> ApiException.notFound("Class"));
        checkMember(c, user);
        return c;
    }

    public void checkMember(Classroom c, AuthUser user) {
        if (!isMember(c, user)) {
            throw ApiException.notFound("Class");
        }
    }

    public boolean isMember(Classroom c, AuthUser user) {
        if (user.isTeacher()) {
            return c.getTeacher().getId().equals(user.id());
        }
        return enrollments.existsByClassroomIdAndStudentId(c.getId(), user.id());
    }

    public boolean isTeacherOf(Classroom c, AuthUser user) {
        return user.isTeacher() && c.getTeacher().getId().equals(user.id());
    }

    /** Archived classes are read-only for students: work can be viewed and downloaded, but nothing new is accepted. */
    public static void requireNotArchived(Classroom c) {
        if (c.isArchived()) {
            throw ApiException.badRequest("CLASS_ARCHIVED", "This class is archived, so no changes can be made.");
        }
    }

    public boolean isEnrolled(Classroom c, Long studentId) {
        return enrollments.existsByClassroomIdAndStudentId(c.getId(), studentId);
    }
}
