package com.resumatch.resume;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumatch.applications.ApplicationRepository;
import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.jobs.JobPostingRepository;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Lets a recruiter view or download the resume of a seeker who applied
 * to one of their jobs. Authorization is enforced at the controller
 * level: the recruiter must own the job the seeker applied to.
 */
@RestController
@RequestMapping("/api/recruiter")
public class ResumeViewController {

    private final ApplicationRepository applicationRepo;
    private final JobPostingRepository jobRepo;
    private final AuthService authService;
    private final ResumeVersionRepository versionRepo;
    private final ResumeExtractionRepository extractionRepo;
    private final ObjectMapper json = new ObjectMapper();

    public ResumeViewController(ApplicationRepository applicationRepo,
                                JobPostingRepository jobRepo,
                                AuthService authService,
                                ResumeVersionRepository versionRepo,
                                ResumeExtractionRepository extractionRepo) {
        this.applicationRepo = applicationRepo;
        this.jobRepo = jobRepo;
        this.authService = authService;
        this.versionRepo = versionRepo;
        this.extractionRepo = extractionRepo;
    }

    @GetMapping("/applications/{applicationId}/resume-preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable Long applicationId, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        var app = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found"));
        var job = jobRepo.findById(app.jobId).orElseThrow();
        if (!job.recruiterId.equals(u.id)) throw new SecurityException("Not your job's application");

        var v = versionRepo.findById(app.resumeVersionId).orElseThrow();
        var ex = extractionRepo.findFirstByResumeVersionIdOrderByCreatedAtDesc(v.id).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId);
        out.put("seekerId", app.seekerId);
        out.put("versionId", v.id);
        out.put("versionNumber", v.versionNumber);
        out.put("versionLabel", v.versionLabel);
        out.put("originalFileName", v.originalFileName);
        out.put("fileSize", v.fileSize);
        out.put("mimeType", v.mimeType);
        out.put("extractedName", ex == null ? null : ex.extractedName);
        out.put("extractedEmail", ex == null ? null : ex.extractedEmail);
        out.put("extractedPhone", ex == null ? null : ex.extractedPhone);
        out.put("experienceYears", ex == null ? 0 : ex.experienceYears);
        out.put("skills", ex == null ? List.of() : parseSkills(ex.skillsJson));
        out.put("downloadUrl", "/api/recruiter/applications/" + applicationId + "/resume");
        return ApiResponse.ok(out);
    }

    @GetMapping("/applications/{applicationId}/resume")
    public ResponseEntity<Resource> download(@PathVariable Long applicationId, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        var app = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found"));
        var job = jobRepo.findById(app.jobId).orElseThrow();
        if (!job.recruiterId.equals(u.id)) throw new SecurityException("Not your job's application");

        var v = versionRepo.findById(app.resumeVersionId).orElseThrow();
        Path path = Paths.get(v.storagePath);
        if (!Files.exists(path)) throw new NoSuchElementException("File missing on disk");
        MediaType type;
        try {
            type = v.mimeType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(v.mimeType);
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        HttpHeaders h = new HttpHeaders();
        h.setContentDispositionFormData("inline", v.originalFileName == null ? "resume" : v.originalFileName);
        return ResponseEntity.ok().headers(h).contentType(type).body(new FileSystemResource(path.toFile()));
    }

    private List<String> parseSkills(String s) {
        if (s == null || s.isBlank()) return List.of();
        try {
            return json.readValue(s, new TypeReference<>() {});
        } catch (Exception e) { return List.of(); }
    }
}
