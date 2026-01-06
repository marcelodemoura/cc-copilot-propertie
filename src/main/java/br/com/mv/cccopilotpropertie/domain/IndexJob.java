package br.com.mv.cccopilotpropertie.domain;

import java.time.Instant;
import java.util.UUID;

public class IndexJob {

    private final UUID id;
    private final int fileCount;
    private final int chunkCount;
    private final Instant createdAt;

    public IndexJob(UUID id, int fileCount, int chunkCount) {
        this.id = id;
        this.fileCount = fileCount;
        this.chunkCount = chunkCount;
        this.createdAt = Instant.now();
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


