package com.resumatch.skills;

import jakarta.persistence.*;

@Entity
@Table(name = "skill_aliases", uniqueConstraints = @UniqueConstraint(columnNames = "normalized_alias_text"))
public class SkillAliasEntity {
    /**
     * SEQUENCE with a pooled optimizer, not IDENTITY.
     *
     * <p>With {@code GenerationType.IDENTITY} the database must return the
     * generated key for every single INSERT before the next one can be sent,
     * which completely disables JDBC batching. On a remote MySQL (Aiven) each
     * of those round-trips costs ~150-200ms, so inserting the ~110 alias rows
     * took ~25 seconds and the managed host dropped the socket mid-seed
     * ("Communications link failure").
     *
     * <p>A pooled sequence allocates ids in blocks, so Hibernate can send all
     * the INSERTs in one batched statement.
     *
     * <p>The sequence table is created by {@code schema-mysql.sql}, because
     * the managed MySQL host sets {@code sql_require_primary_key=ON} and
     * Hibernate's {@code ddl-auto=update} refuses to create a sequence table
     * (it has no primary key). The table has a primary key and is therefore
     * created normally - the {@code sql_require_primary_key} guard only
     * applies to tables Hibernate generates.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "skill_alias_seq")
    @SequenceGenerator(name = "skill_alias_seq", sequenceName = "skill_alias_seq", allocationSize = 50)
    public Long id;

    @Column(name = "skill_id", nullable = false)
    public Long skillId;

    @Column(name = "alias_text", nullable = false, length = 200)
    public String aliasText;

    @Column(name = "normalized_alias_text", nullable = false, length = 200)
    public String normalizedAliasText;
}
