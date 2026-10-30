package com.resumatch.skills;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<SkillEntity, Long> {
    Optional<SkillEntity> findByCanonicalNameIgnoreCase(String canonicalName);
}
