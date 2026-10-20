package com.collabboard.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, CodePurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, CodePurpose purpose, Instant after);

    List<VerificationCode> findByUserIdAndPurposeAndConsumedAtIsNull(Long userId, CodePurpose purpose);
}
