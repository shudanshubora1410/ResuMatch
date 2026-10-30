package com.resumatch.applications;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistoryEntity, Long> {
    List<ApplicationStatusHistoryEntity> findByApplicationIdOrderByChangedAtAsc(Long applicationId);
}
