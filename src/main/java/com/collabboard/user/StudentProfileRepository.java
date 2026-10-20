package com.collabboard.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    List<StudentProfile> findByUserIdIn(Collection<Long> userIds);
}
