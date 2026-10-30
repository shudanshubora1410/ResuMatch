package com.resumatch.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobSkillRepository extends JpaRepository<JobSkillEntity, Long> {
    List<JobSkillEntity> findByJobId(Long jobId);
    void deleteByJobId(Long jobId);
}
