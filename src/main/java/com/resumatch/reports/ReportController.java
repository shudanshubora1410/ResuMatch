package com.resumatch.reports;

import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ReportController {

    private final ReportService reportService;
    private final AuthService authService;

    public ReportController(ReportService reportService, AuthService authService) {
        this.reportService = reportService;
        this.authService = authService;
    }

    @GetMapping("/api/seeker/reports/{versionId}/pdf")
    public ResponseEntity<byte[]> download(@PathVariable Long versionId, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "SEEKER");
        byte[] bytes = reportService.generateForVersion(u.id, versionId);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDispositionFormData("attachment", "resumatch-report-v" + versionId + ".pdf");
        return new ResponseEntity<>(bytes, h, 200);
    }
}
