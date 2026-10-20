package com.collabboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFile, Long> {

    List<WorkspaceFile> findByTeamIdAndStatusOrderByCategoryAscUploadedAtDesc(Long teamId, WorkspaceFileStatus status);

    List<WorkspaceFile> findByTeamIdOrderByUploadedAtDesc(Long teamId);

    Optional<WorkspaceFile> findByIdAndTeamId(Long id, Long teamId);
}
