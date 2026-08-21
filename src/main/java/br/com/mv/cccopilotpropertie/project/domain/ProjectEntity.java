package br.com.mv.cccopilotpropertie.project.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_project", uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "name"}))
public class ProjectEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rootPath;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ProjectEntity() {
    }

    public ProjectEntity(String tenantId, String name, String rootPath) {
        this.tenantId = tenantId;
        this.name = name;
        this.rootPath = rootPath;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getRootPath() { return rootPath; }
    public Instant getCreatedAt() { return createdAt; }
}
