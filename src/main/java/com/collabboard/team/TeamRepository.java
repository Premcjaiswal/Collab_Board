package com.collabboard.team;

import com.collabboard.project.TeamFormation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByProjectIdOrderByNameAsc(Long projectId);

    boolean existsByProjectIdAndNameIgnoreCase(Long projectId, String name);

    Optional<Team> findByProjectIdAndJoinCode(Long projectId, String joinCode);

    boolean existsByJoinCode(String joinCode);

    List<Team> findByJoinCodeIsNullAndProjectTeamFormation(TeamFormation formation);

    long countByProjectId(Long projectId);

    /** Row lock used to serialise membership/invitation changes so team capacity cannot be exceeded. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);
}
