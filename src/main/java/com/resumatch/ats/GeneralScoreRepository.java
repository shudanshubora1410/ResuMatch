package com.resumatch.ats;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GeneralScoreRepository extends JpaRepository<GeneralScoreEntity, Long> {
    Optional<GeneralScoreEntity> findFirstByResumeVersionIdOrderByScoredAtDesc(Long versionId);
    List<GeneralScoreEntity> findByResumeVersionIdInOrderByScoredAtAsc(List<Long> versionIds);
}
