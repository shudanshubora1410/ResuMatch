package com.resumatch.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class CompanyEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "company_name", nullable = false, length = 200)
    public String companyName;

    @Column(name = "company_description", length = 2000)
    public String companyDescription;

    @Column(length = 300)
    public String website;

    @Column(length = 100)
    public String location;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();
}
