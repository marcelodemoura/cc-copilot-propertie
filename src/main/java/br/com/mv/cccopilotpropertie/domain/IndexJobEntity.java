package br.com.mv.cccopilotpropertie.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "index_job")
public class IndexJobEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IndexJobEntity() {
    }

    public IndexJobEntity(UUID id, int fileCount, int chunkCount, Instant createdAt) {
        this.id = id;
        this.fileCount = fileCount;
        this.chunkCount = chunkCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
