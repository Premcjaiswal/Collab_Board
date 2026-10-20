package com.collabboard.classroom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    List<Classroom> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    Optional<Classroom> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    @Query("select e.classroom from Enrollment e where e.student.id = :studentId order by e.joinedAt desc")
    List<Classroom> findEnrolledClasses(@Param("studentId") Long studentId);
}
