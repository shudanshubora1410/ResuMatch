package com.resumatch.skills;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkillAliasRepository extends JpaRepository<SkillAliasEntity, Long> {
    Optional<SkillAliasEntity> findByNormalizedAliasText(String normalized);
}
