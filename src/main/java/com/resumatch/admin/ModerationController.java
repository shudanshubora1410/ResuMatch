package com.resumatch.admin;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.common.JobStatus;
import com.resumatch.jobs.JobPostingEntity;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.user.UserEntity;
import com.resumatch.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
public class ModerationController {

    private final JobReportRepository reportRepo;
    private final JobPostingRepository jobRepo;
    private final UserRepository userRepo;
    private final ApplicationRepository appRepo;
    private final AuthService authService;

    public ModerationController(JobReportRepository reportRepo, JobPostingRepository jobRepo,
                                UserRepository userRepo, ApplicationRepository appRepo,
                                AuthService authService) {
        this.reportRepo = reportRepo;
        this.jobRepo = jobRepo;
        this.userRepo = userRepo;
        this.appRepo = appRepo;
        this.authService = authService;
    }

    private void requireAdmin(HttpSession s) {
        UserEntity u = authService.requireUser(s);
        if (!"ADMIN".equals(u.role.name())) throw new SecurityException("Admin only");
    }

    // ---- seeker-side: report a job ----
    @PostMapping("/api/seeker/jobs/{jobId}/report")
    @Transactional
    public ApiResponse<JobReportEntity> reportJob(@PathVariable Long jobId,
                                                  @RequestBody Map<String, String> body,
                                                  HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        JobPostingEntity j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        JobReportEntity r = new JobReportEntity();
        r.jobId = j.id;
        r.reportedByUserId = u.id;
        r.reason = body.getOrDefault("reason", "OTHER");
        r.otherReasonText = body.get("otherReasonText");
        r.reportStatus = "PENDING";
        return ApiResponse.ok("Report submitted", reportRepo.save(r));
    }

    // ---- admin-side: queue ----
    @GetMapping("/api/admin/reports/jobs")
    public ApiResponse<List<Map<String, Object>>> queue(HttpSession session) {
        requireAdmin(session);
        var rows = reportRepo.findByReportStatusOrderByCreatedAtDesc("PENDING");
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            var job = jobRepo.findById(r.jobId).orElse(null);
            var reporter = userRepo.findById(r.reportedByUserId).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportId", r.id);
            m.put("jobId", r.jobId);
            m.put("jobTitle", job == null ? null : job.title);
            m.put("jobStatus", job == null ? null : job.status.name());
            m.put("reason", r.reason);
            m.put("otherReason", r.otherReasonText);
            m.put("reportedBy", reporter == null ? null : reporter.displayName);
            m.put("createdAt", r.createdAt);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/admin/reports/jobs/all")
    public ApiResponse<List<Map<String, Object>>> allReports(HttpSession session) {
        requireAdmin(session);
        var rows = reportRepo.findAll();
        rows.sort((a, b) -> b.createdAt.compareTo(a.createdAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            var job = jobRepo.findById(r.jobId).orElse(null);
            var reporter = userRepo.findById(r.reportedByUserId).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportId", r.id);
            m.put("jobId", r.jobId);
            m.put("jobTitle", job == null ? null : job.title);
            m.put("reason", r.reason);
            m.put("otherReason", r.otherReasonText);
            m.put("reportedBy", reporter == null ? null : reporter.displayName);
            m.put("status", r.reportStatus);
            m.put("createdAt", r.createdAt);
            m.put("reviewedAt", r.reviewedAt);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @PatchMapping("/api/admin/reports/jobs/{reportId}/resolve")
    @Transactional
    public ApiResponse<Object> resolve(@PathVariable Long reportId,
                                       @RequestBody Map<String, String> body,
                                       HttpSession session) {
        UserEntity u = authService.requireUser(session);
        requireAdmin(session);
        var r = reportRepo.findById(reportId).orElseThrow();
        String action = body.getOrDefault("action", "DISMISS").toUpperCase();
        switch (action) {
            case "DISMISS" -> r.reportStatus = "DISMISSED";
            case "RESOLVE" -> r.reportStatus = "RESOLVED";
            case "CLOSE_JOB" -> {
                r.reportStatus = "RESOLVED";
                jobRepo.findById(r.jobId).ifPresent(j -> { j.status = JobStatus.CLOSED; j.updatedAt = Instant.now(); jobRepo.save(j); });
            }
            case "DISABLE_RECRUITER" -> {
                r.reportStatus = "RESOLVED";
                jobRepo.findById(r.jobId).ifPresent(j -> {
                    j.status = JobStatus.CLOSED;
                    j.updatedAt = Instant.now();
                    jobRepo.save(j);
                    userRepo.findById(j.recruiterId).ifPresent(rec -> {
                        rec.accountStatus = com.resumatch.common.AccountStatus.DISABLED;
                        rec.updatedAt = Instant.now();
                        userRepo.save(rec);
                    });
                });
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        r.reviewedByAdminId = u.id;
        r.reviewedAt = Instant.now();
        reportRepo.save(r);
        return ApiResponse.ok("Action taken: " + action, r);
    }
}
