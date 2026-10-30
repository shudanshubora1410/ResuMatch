package com.resumatch.dashboards;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.ats.GeneralScoreEntity;
import com.resumatch.ats.GeneralScoreRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.jobs.JobSkillRepository;
import com.resumatch.resume.ResumeRepository;
import com.resumatch.resume.ResumeVersionRepository;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class DashboardController {

    private final AuthService authService;
    private final ResumeRepository resumeRepo;
    private final ResumeVersionRepository versionRepo;
    private final GeneralScoreRepository generalScoreRepo;
    private final ApplicationRepository applicationRepo;
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository jobSkillRepo;
    private final InsightsService insightsService;

    public DashboardController(AuthService authService,
                               ResumeRepository resumeRepo,
                               ResumeVersionRepository versionRepo,
                               GeneralScoreRepository generalScoreRepo,
                               ApplicationRepository applicationRepo,
                               JobPostingRepository jobRepo,
                               JobSkillRepository jobSkillRepo,
                               InsightsService insightsService) {
        this.authService = authService;
        this.resumeRepo = resumeRepo;
        this.versionRepo = versionRepo;
        this.generalScoreRepo = generalScoreRepo;
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.jobSkillRepo = jobSkillRepo;
        this.insightsService = insightsService;
    }

    @GetMapping("/api/seeker/dashboard")
    public ApiResponse<Map<String, Object>> seekerDashboard(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", u.id);
        out.put("displayName", u.displayName);

        var resume = resumeRepo.findFirstBySeekerId(u.id).orElse(null);
        if (resume != null && resume.activeVersionId != null) {
            var gs = generalScoreRepo.findFirstByResumeVersionIdOrderByScoredAtDesc(resume.activeVersionId).orElse(null);
            out.put("activeScore", gs);
            out.put("activeVersionId", resume.activeVersionId);
        } else {
            out.put("activeScore", null);
        }
        out.put("totalApplications", applicationRepo.findBySeekerIdOrderByAppliedAtDesc(u.id).size());

        // top jobs by score
        var apps = applicationRepo.findBySeekerIdOrderByAppliedAtDesc(u.id);
        out.put("recentApplications", apps.stream().limit(5).toList());
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/recruiter/dashboard")
    public ApiResponse<Map<String, Object>> recruiterDashboard(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        var jobs = jobRepo.findByRecruiterIdOrderByCreatedAtDesc(u.id);
        long totalApplicants = 0;
        long openJobs = 0;
        for (var j : jobs) {
            totalApplicants += applicationRepo.findByJobIdOrderByAppliedAtDesc(j.id).size();
            if (j.status == com.resumatch.common.JobStatus.OPEN) openJobs++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalJobs", jobs.size());
        out.put("openJobs", openJobs);
        out.put("totalApplicants", totalApplicants);
        out.put("jobs", jobs);
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/seeker/recommendations")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> recommendations(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        return ApiResponse.ok(insightsService.recommendedJobs(u.id));
    }

    @GetMapping("/api/seeker/skills-gap")
    public ApiResponse<java.util.Map<String, Object>> skillsGap(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        return ApiResponse.ok(insightsService.skillsGap(u.id));
    }
}
