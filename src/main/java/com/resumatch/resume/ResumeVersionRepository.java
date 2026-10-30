package com.resumatch.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersionEntity, Long> {
    List<ResumeVersionEntity> findByResumeIdOrderByVersionNumberDesc(Long resumeId);
    Optional<ResumeVersionEntity> findFirstByResumeIdOrderByVersionNumberDesc(Long resumeId);
}
