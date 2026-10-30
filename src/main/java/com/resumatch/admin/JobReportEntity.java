package com.resumatch.admin;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_reports")
public class JobReportEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "job_id", nullable = false)
    public Long jobId;

    @Column(name = "reported_by_user_id", nullable = false)
    public Long reportedByUserId;

    @Column(nullable = false, length = 32)
    public String reason; // FAKE_COMPANY, SCAM_OR_PAYMENT, MISLEADING_SKILLS, OTHER

    @Column(name = "other_reason_text", length = 500)
    public String otherReasonText;

    @Column(name = "report_status", nullable = false, length = 16)
    public String reportStatus = "PENDING"; // PENDING, REVIEWED, RESOLVED, DISMISSED

    @Column(name = "reviewed_by_admin_id")
    public Long reviewedByAdminId;

    @Column(name = "reviewed_at")
    public Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
