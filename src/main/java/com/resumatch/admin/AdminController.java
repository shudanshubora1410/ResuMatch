package com.resumatch.admin;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.AccountStatus;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.common.JobStatus;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.user.UserEntity;
import com.resumatch.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AuthService authService;
    private final UserRepository userRepo;
    private final JobPostingRepository jobRepo;
    private final ApplicationRepository appRepo;

    public AdminController(AuthService authService, UserRepository userRepo,
                           JobPostingRepository jobRepo, ApplicationRepository appRepo) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.jobRepo = jobRepo;
        this.appRepo = appRepo;
    }

    private void requireAdmin(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        if (!"ADMIN".equals(u.role.name())) throw new SecurityException("Admin only");
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(HttpSession session) {
        requireAdmin(session);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalUsers", userRepo.count());
        out.put("seekers", userRepo.findByRole(com.resumatch.common.Role.SEEKER).size());
        out.put("recruiters", userRepo.findByRole(com.resumatch.common.Role.RECRUITER).size());
        out.put("totalJobs", jobRepo.count());
        out.put("openJobs", jobRepo.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN).size());
        out.put("totalApplications", appRepo.count());
        return ApiResponse.ok(out);
    }

    @GetMapping("/users")
    public ApiResponse<List<UserEntity>> users(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(userRepo.findAll());
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<UserEntity> userStatus(@PathVariable Long userId,
                                              @RequestBody Map<String, String> body,
                                              HttpSession session) {
        requireAdmin(session);
        UserEntity u = userRepo.findById(userId).orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        u.accountStatus = AccountStatus.valueOf(body.getOrDefault("status", "ACTIVE"));
        return ApiResponse.ok("User status updated", userRepo.save(u));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<com.resumatch.jobs.JobPostingEntity>> jobs(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(jobRepo.findAll());
    }

    @PatchMapping("/jobs/{jobId}/status")
    public ApiResponse<Object> jobStatus(@PathVariable Long jobId,
                                          @RequestBody Map<String, String> body,
                                          HttpSession session) {
        requireAdmin(session);
        var j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        j.status = JobStatus.valueOf(body.getOrDefault("status", "OPEN"));
        j.updatedAt = java.time.Instant.now();
        jobRepo.save(j);
        return ApiResponse.ok("Job status updated", j);
    }
}
