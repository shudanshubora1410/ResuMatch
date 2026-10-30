package com.resumatch.notifications;

import com.resumatch.user.NotificationEntity;
import com.resumatch.user.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Abstraction over email delivery. For development / demo we log the
 * email content and record it in the notifications table so the in-app
 * bell has data to show.
 */
@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final NotificationRepository notifications;

    public EmailService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public void send(Long userId, String subject, String body) {
        try {
            log.info("[EMAIL] to userId={} subject='{}' body='{}'", userId, subject, body);
            NotificationEntity n = new NotificationEntity();
            n.userId = userId;
            n.type = "EMAIL";
            n.channel = "IN_APP";
            n.subject = subject;
            n.body = body;
            n.sendStatus = "SENT";
            n.sentAt = Instant.now();
            notifications.save(n);
        } catch (Exception e) {
            log.warn("Failed to log email for user {}", userId, e);
        }
    }
}
