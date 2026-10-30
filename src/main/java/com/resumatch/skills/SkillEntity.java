package com.resumatch.skills;

import jakarta.persistence.*;

@Entity
@Table(name = "skills", uniqueConstraints = @UniqueConstraint(columnNames = "canonical_name"))
public class SkillEntity {
    /**
     * IDENTITY (auto-increment), not SEQUENCE.
     *
     * <p>Note on batching: with {@code GenerationType.IDENTITY} Hibernate has to
     * fetch the generated key for every INSERT before sending the next one, so
     * JDBC batching is bypassed for this entity. That is acceptable here
     * because {@code SkillSeed} only writes 48 rows - and more importantly,
     * a SEQUENCE strategy does not work on this deployment: the managed MySQL
     * host sets {@code sql_require_primary_key=ON}, which blocks Hibernate from
     * creating the sequence table (sequence tables have no primary key).
     *
     * <p>The 110 alias rows - which are the real bottleneck - use a pooled
     * SEQUENCE with an explicit table that is created by the migration
     * script, so they DO batch.
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "canonical_name", nullable = false, length = 120)
    public String canonicalName;

    @Column(length = 60)
    public String category;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;
}
