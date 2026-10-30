package com.resumatch.skills;

import jakarta.persistence.*;

@Entity
@Table(name = "skill_aliases", uniqueConstraints = @UniqueConstraint(columnNames = "normalized_alias_text"))
public class SkillAliasEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "skill_id", nullable = false)
    public Long skillId;

    @Column(name = "alias_text", nullable = false, length = 200)
    public String aliasText;

    @Column(name = "normalized_alias_text", nullable = false, length = 200)
    public String normalizedAliasText;
}
