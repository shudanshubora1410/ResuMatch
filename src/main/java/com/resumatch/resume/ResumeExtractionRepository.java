package com.resumatch.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResumeExtractionRepository extends JpaRepository<ResumeExtractionEntity, Long> {
    Optional<ResumeExtractionEntity> findFirstByResumeVersionIdOrderByCreatedAtDesc(Long versionId);
}
