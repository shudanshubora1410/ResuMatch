package com.resumatch.notifications;

import com.resumatch.auth.AuthService;
import com.resumatch.common.ApiResponse;
import com.resumatch.common.AuthContext;
import com.resumatch.user.NotificationEntity;
import com.resumatch.user.NotificationRepository;
import com.resumatch.user.UserEntity;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository repo;
    private final AuthService authService;

    public NotificationController(NotificationRepository repo, AuthService authService) {
        this.repo = repo;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        List<NotificationEntity> recent = repo.findTop50ByUserIdOrderByCreatedAtDesc(u.id);
        long unread = repo.countByUserIdAndIsReadFalse(u.id);
        return ApiResponse.ok(Map.of("items", recent, "unread", unread));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ApiResponse<Object> markRead(@PathVariable Long id, HttpSession session) {
        UserEntity u = authService.requireUser(session);
        NotificationEntity n = repo.findById(id).orElseThrow();
        if (!n.userId.equals(u.id)) throw new SecurityException("Not yours");
        n.isRead = true;
        repo.save(n);
        return ApiResponse.ok("Marked as read", null);
    }

    @PostMapping("/read-all")
    @Transactional
    public ApiResponse<Object> markAll(HttpSession session) {
        UserEntity u = authService.requireUser(session);
        for (NotificationEntity n : repo.findTop50ByUserIdOrderByCreatedAtDesc(u.id)) {
            if (!n.isRead) { n.isRead = true; repo.save(n); }
        }
        return ApiResponse.ok("All marked", null);
    }
}
