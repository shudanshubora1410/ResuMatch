package com.resumatch.jobs;

import com.resumatch.ats.TextNormalizer;
import com.resumatch.common.JobStatus;
import com.resumatch.user.CompanyEntity;
import com.resumatch.user.CompanyRepository;
import com.resumatch.user.RecruiterProfileEntity;
import com.resumatch.user.RecruiterProfileRepository;
import com.resumatch.user.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class JobService {
    private final JobPostingRepository jobRepo;
    private final JobSkillRepository skillRepo;
    private final RecruiterProfileRepository recruiterProfileRepo;
    private final CompanyRepository companyRepo;

    public JobService(JobPostingRepository jobRepo, JobSkillRepository skillRepo,
                      RecruiterProfileRepository recruiterProfileRepo, CompanyRepository companyRepo) {
        this.jobRepo = jobRepo;
        this.skillRepo = skillRepo;
        this.recruiterProfileRepo = recruiterProfileRepo;
        this.companyRepo = companyRepo;
    }

    @Transactional
    public JobPostingEntity createJob(UserEntity recruiter, Map<String, Object> body) {
        if (!"RECRUITER".equals(recruiter.role.name())) throw new SecurityException("Recruiter only");
        RecruiterProfileEntity rp = recruiterProfileRepo.findById(recruiter.id)
                .orElseThrow(() -> new IllegalArgumentException("Recruiter profile missing"));
        Long companyId = rp.companyId;
        if (companyId == null) throw new IllegalArgumentException("Recruiter is not associated with a company");

        JobPostingEntity j = new JobPostingEntity();
        j.recruiterId = recruiter.id;
        j.companyId = companyId;
        j.title = str(body.get("title"));
        j.description = str(body.get("description"));
        j.location = str(body.get("location"));
        j.employmentType = str(body.get("employmentType"));
        j.minExperienceYears = intOrNull(body.get("minExperienceYears"));
        j.maxExperienceYears = intOrNull(body.get("maxExperienceYears"));
        j.status = JobStatus.OPEN;
        j = jobRepo.save(j);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills = (List<Map<String, Object>>) body.get("skills");
        if (skills != null) {
            for (Map<String, Object> s : skills) {
                String name = str(s.get("name"));
                if (name == null || name.isBlank()) continue;
                JobSkillEntity js = new JobSkillEntity();
                js.jobId = j.id;
                js.rawSkillText = name.trim();
                js.normalizedSkillText = TextNormalizer.skillKey(name);
                String imp = str(s.get("importance"));
                js.importance = "NICE_TO_HAVE".equalsIgnoreCase(imp)
                        ? com.resumatch.common.SkillImportance.NICE_TO_HAVE
                        : com.resumatch.common.SkillImportance.MUST_HAVE;
                Object w = s.get("weight");
                js.weight = w == null ? BigDecimal.ONE : new BigDecimal(w.toString());
                skillRepo.save(js);
            }
        }
        return j;
    }

    @Transactional
    public JobPostingEntity updateJob(UserEntity recruiter, Long jobId, Map<String, Object> body) {
        JobPostingEntity j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        if (!j.recruiterId.equals(recruiter.id) && !"ADMIN".equals(recruiter.role.name()))
            throw new SecurityException("Cannot edit another recruiter's job");
        if (body.containsKey("title")) j.title = str(body.get("title"));
        if (body.containsKey("description")) j.description = str(body.get("description"));
        if (body.containsKey("location")) j.location = str(body.get("location"));
        if (body.containsKey("employmentType")) j.employmentType = str(body.get("employmentType"));
        if (body.containsKey("minExperienceYears")) j.minExperienceYears = intOrNull(body.get("minExperienceYears"));
        if (body.containsKey("maxExperienceYears")) j.maxExperienceYears = intOrNull(body.get("maxExperienceYears"));
        j.updatedAt = java.time.Instant.now();
        if (body.containsKey("skills")) {
            skillRepo.deleteByJobId(j.id);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skills = (List<Map<String, Object>>) body.get("skills");
            if (skills != null) {
                for (Map<String, Object> s : skills) {
                    String name = str(s.get("name"));
                    if (name == null || name.isBlank()) continue;
                    JobSkillEntity js = new JobSkillEntity();
                    js.jobId = j.id;
                    js.rawSkillText = name.trim();
                    js.normalizedSkillText = TextNormalizer.skillKey(name);
                    String imp = str(s.get("importance"));
                    js.importance = "NICE_TO_HAVE".equalsIgnoreCase(imp)
                            ? com.resumatch.common.SkillImportance.NICE_TO_HAVE
                            : com.resumatch.common.SkillImportance.MUST_HAVE;
                    Object w = s.get("weight");
                    js.weight = w == null ? BigDecimal.ONE : new BigDecimal(w.toString());
                    skillRepo.save(js);
                }
            }
        }
        return jobRepo.save(j);
    }

    @Transactional
    public JobPostingEntity setStatus(UserEntity actor, Long jobId, JobStatus status) {
        JobPostingEntity j = jobRepo.findById(jobId).orElseThrow(() -> new java.util.NoSuchElementException("Job not found"));
        if (!j.recruiterId.equals(actor.id) && !"ADMIN".equals(actor.role.name()))
            throw new SecurityException("Cannot change another recruiter's job");
        j.status = status;
        j.updatedAt = java.time.Instant.now();
        return jobRepo.save(j);
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
    private Integer intOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return Integer.parseInt(o.toString());
    }
}
