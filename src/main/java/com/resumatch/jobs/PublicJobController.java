package com.resumatch.jobs;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/seeker/jobs")
public class PublicJobController {
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository skillRepo;
    private final AuthService authService;
    private final ApplicationRepository appRepo;

    public PublicJobController(JobPostingRepository jobRepo, JobSkillRepository skillRepo,
                               AuthService authService, ApplicationRepository appRepo) {
        this.jobRepo = jobRepo;
        this.skillRepo = skillRepo;
        this.authService = authService;
        this.appRepo = appRepo;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String location,
                                                       @RequestParam(required = false) String skill,
                                                       @RequestParam(required = false) String company,
                                                       @RequestParam(required = false) String title,
                                                       HttpSession session) {
        var jobs = jobRepo.searchOpen(emptyToNull(location), emptyToNull(title));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var j : jobs) {
            var skills = skillRepo.findByJobId(j.id);
            if (skill != null && !skill.isBlank()) {
                String sn = skill.toLowerCase();
                boolean match = skills.stream().anyMatch(s -> s.rawSkillText.toLowerCase().contains(sn));
                if (!match) continue;
            }
            out.add(toDto(j, skills));
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/{jobId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long jobId) {
        var j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        return ApiResponse.ok(toDto(j, skillRepo.findByJobId(j.id)));
    }

    private Map<String, Object> toDto(JobPostingEntity j, List<JobSkillEntity> skills) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id);
        m.put("title", j.title);
        m.put("description", j.description);
        m.put("location", j.location);
        m.put("employmentType", j.employmentType);
        m.put("minExperienceYears", j.minExperienceYears);
        m.put("maxExperienceYears", j.maxExperienceYears);
        m.put("status", j.status.name());
        m.put("createdAt", j.createdAt);
        m.put("skills", skills.stream().map(s -> Map.of(
                "id", s.id, "name", s.rawSkillText,
                "importance", s.importance.name(), "weight", s.weight)).toList());
        return m;
    }

    private String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
