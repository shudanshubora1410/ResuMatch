package com.resumatch.common;

import com.resumatch.applications.ApplicationRepository;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tiny public endpoints. No auth required.
 * Used by the landing-page first-run banner to decide whether to nudge the visitor
 * toward the admin's "seed demo data" button.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final UserRepository userRepo;
    private final JobPostingRepository jobRepo;
    private final ApplicationRepository appRepo;

    public PublicController(UserRepository userRepo, JobPostingRepository jobRepo, ApplicationRepository appRepo) {
        this.userRepo = userRepo;
        this.jobRepo = jobRepo;
        this.appRepo = appRepo;
    }

    @GetMapping("/needs-seed")
    public Map<String, Object> needsSeed() {
        long seekers = userRepo.findByRole(Role.SEEKER).size();
        long jobs = jobRepo.count();
        long apps = appRepo.count();
        boolean empty = seekers < 2 || jobs < 1 || apps < 1;
        return Map.of("empty", empty, "seekers", seekers, "jobs", jobs, "applications", apps);
    }
}
