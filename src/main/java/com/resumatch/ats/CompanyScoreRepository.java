package com.resumatch.ats;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyScoreRepository extends JpaRepository<CompanyScoreEntity, Long> {
    Optional<CompanyScoreEntity> findFirstByApplicationIdOrderByComputedAtDesc(Long applicationId);
    List<CompanyScoreEntity> findByApplicationIdIn(List<Long> applicationIds);
}
