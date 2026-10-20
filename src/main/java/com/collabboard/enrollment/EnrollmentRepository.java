package com.collabboard.enrollment;

import com.collabboard.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByClassroomIdAndStudentId(Long classroomId, Long studentId);

    Optional<Enrollment> findByClassroomIdAndStudentId(Long classroomId, Long studentId);

    List<Enrollment> findByClassroomIdOrderByJoinedAtAsc(Long classroomId);

    List<Enrollment> findByStudentId(Long studentId);

    long countByClassroomId(Long classroomId);

    @Query("select e.student from Enrollment e where e.classroom.id = :classId")
    List<User> findStudents(@Param("classId") Long classId);
}
