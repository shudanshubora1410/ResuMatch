package com.resumatch.skills;

import jakarta.persistence.*;

@Entity
@Table(name = "skills", uniqueConstraints = @UniqueConstraint(columnNames = "canonical_name"))
public class SkillEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "canonical_name", nullable = false, length = 120)
    public String canonicalName;

    @Column(length = 60)
    public String category;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
