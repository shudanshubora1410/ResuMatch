package com.resumatch.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {
    Optional<ResumeEntity> findFirstBySeekerId(Long seekerId);
}
