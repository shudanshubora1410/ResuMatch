package com.resumatch.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobReportRepository extends JpaRepository<JobReportEntity, Long> {
    List<JobReportEntity> findByReportStatusOrderByCreatedAtDesc(String status);
    long countByReportStatus(String status);
}
