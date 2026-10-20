package com.collabboard.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, Long> {

    List<SubmissionVersion> findBySubmissionIdOrderByVersionNoDesc(Long submissionId);

    Optional<SubmissionVersion> findBySubmissionIdAndVersionNo(Long submissionId, int versionNo);

    /** Whether a stored file is part of any version of the given submission. */
    @Query("select count(v) > 0 from SubmissionVersion v join v.files f where v.submission.id = :submissionId and f.id = :fileId")
    boolean submissionContainsFile(@Param("submissionId") Long submissionId, @Param("fileId") Long fileId);
}
