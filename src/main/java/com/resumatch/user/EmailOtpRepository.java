package com.resumatch.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmailOtpRepository extends JpaRepository<EmailOtpEntity, Long> {
    List<EmailOtpEntity> findTop10ByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, String purpose);
}
