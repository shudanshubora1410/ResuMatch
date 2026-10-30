package com.resumatch.ai;

import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService gemini;
    private final AuthService authService;

    public AiController(GeminiService gemini, AuthService authService) {
        this.gemini = gemini;
        this.authService = authService;
    }

    @PostMapping("/suggest-description")
    public ApiResponse<Map<String, Object>> suggestDescription(@RequestBody Map<String, Object> body,
                                                               HttpSession session) {
        UserEntity u = authService.requireUser(session);
        AuthContext.requireRole(session, "RECRUITER");
        String title = (String) body.getOrDefault("title", "");
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) body.getOrDefault("skills", List.of());
        String type = (String) body.getOrDefault("employmentType", "Full-time");
        String description = gemini.suggestJobDescription(title, skills, type);
        return ApiResponse.ok("ok", Map.of(
                "description", description,
                "aiConfigured", gemini.isConfigured()));
    }
}
