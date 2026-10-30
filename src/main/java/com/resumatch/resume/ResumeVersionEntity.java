package com.resumatch.resume;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "resume_versions")
public class ResumeVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "resume_id", nullable = false)
    public Long resumeId;

    @Column(name = "version_number", nullable = false)
    public int versionNumber;

    @Column(name = "version_label", length = 120)
    public String versionLabel;

    @Column(name = "original_file_name", nullable = false, length = 200)
    public String originalFileName;

    @Column(name = "storage_path", nullable = false, length = 500)
    public String storagePath;

    @Column(name = "mime_type", length = 80)
    public String mimeType;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    public Instant uploadedAt = Instant.now();
}
