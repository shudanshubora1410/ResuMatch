package com.resumatch.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResumeFormatFlagRepository extends JpaRepository<ResumeFormatFlagEntity, Long> {
    List<ResumeFormatFlagEntity> findByResumeVersionId(Long versionId);
}
