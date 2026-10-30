package com.resumatch.applications;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {
    Optional<ApplicationEntity> findByJobIdAndSeekerId(Long jobId, Long seekerId);
    List<ApplicationEntity> findBySeekerIdOrderByAppliedAtDesc(Long seekerId);
    List<ApplicationEntity> findByJobIdOrderByAppliedAtDesc(Long jobId);
}
