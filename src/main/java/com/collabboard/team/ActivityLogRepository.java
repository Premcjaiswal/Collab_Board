package com.collabboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop100ByTeamIdOrderByCreatedAtDescIdDesc(Long teamId);

    List<ActivityLog> findByTeamId(Long teamId);
}
