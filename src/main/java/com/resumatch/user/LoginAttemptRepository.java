package com.resumatch.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttemptEntity, Long> {
    @Query("select count(a) from LoginAttemptEntity a where a.identifierValue=:id and a.success=false and a.attemptedAt > :since")
    long countRecentFailures(@Param("id") String id, @Param("since") Instant since);

    @Query("select count(a) from LoginAttemptEntity a where a.ipAddress=:ip and a.success=false and a.attemptedAt > :since")
    long countRecentIpFailures(@Param("ip") String ip, @Param("since") Instant since);

    List<LoginAttemptEntity> findTop20ByIdentifierValueOrderByAttemptedAtDesc(String identifierValue);
}
