package com.resumatch.auth;

import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register/seeker")
    public ApiResponse<Map<String, Object>> registerSeeker(@RequestBody Map<String, String> body, HttpSession session) {
        UserEntity u = auth.registerSeeker(
                body.get("fullName"), body.get("email"), body.get("phone"), body.get("password"));
        auth.loginSession(session, u);
        return ApiResponse.ok("Account created", Map.of(
                "userId", u.id, "role", u.role.name(), "displayName", u.displayName,
                "redirect", roleHome(u.role.name())));
    }

    @PostMapping("/register/recruiter")
    public ApiResponse<Map<String, Object>> registerRecruiter(@RequestBody Map<String, String> body, HttpSession session) {
        UserEntity u = auth.registerRecruiter(
                body.get("companyName"), body.get("recruiterName"), body.get("email"),
                body.get("phone"), body.get("password"));
        auth.loginSession(session, u);
        return ApiResponse.ok("Account created", Map.of(
                "userId", u.id, "role", u.role.name(), "displayName", u.displayName,
                "redirect", roleHome(u.role.name())));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                 HttpSession session,
                                                 HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        UserEntity u = auth.login(body.get("identifier"), body.get("password"), ip);
        if (u.twoFactorEnabled) {
            // require OTP before completing login
            session.setAttribute("pending2FA", u.id);
            return ApiResponse.ok("2FA required", Map.of("twoFactorRequired", true, "userId", u.id));
        }
        auth.loginSession(session, u);
        return ApiResponse.ok("Login successful", Map.of(
                "userId", u.id, "role", u.role.name(), "displayName", u.displayName,
                "redirect", roleHome(u.role.name())));
    }

    @PostMapping("/logout")
    public ApiResponse<Object> logout(HttpSession session) {
        AuthContext.logout(session);
        return ApiResponse.ok("Logged out", null);
    }

    @GetMapping("/me")
    public ApiResponse<Object> me(HttpSession session) {
        try {
            Long id = AuthContext.userId(session);
            String role = AuthContext.role(session);
            return ApiResponse.ok(Map.of("userId", id, "role", role,
                    "displayName", session.getAttribute(AuthContext.SESSION_DISPLAY_NAME)));
        } catch (SecurityException e) {
            return ApiResponse.error("Not authenticated");
        }
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Object> forgot(@RequestBody Map<String, String> body) {
        auth.startPasswordReset(body.get("email"));
        return ApiResponse.ok("If the email exists, a reset token has been sent.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Object> reset(@RequestBody Map<String, String> body) {
        auth.confirmPasswordReset(body.get("token"), body.get("newPassword"));
        return ApiResponse.ok("Password updated", null);
    }

    private String roleHome(String role) {
        return switch (role) {
            case "SEEKER" -> "/pages/seeker-dashboard.html";
            case "RECRUITER" -> "/pages/recruiter-dashboard.html";
            case "ADMIN" -> "/pages/admin-dashboard.html";
            default -> "/";
        };
    }
}
