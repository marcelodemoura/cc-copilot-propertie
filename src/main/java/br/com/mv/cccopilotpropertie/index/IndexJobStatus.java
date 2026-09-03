package br.com.mv.cccopilotpropertie.index;

import java.time.LocalDateTime;
import java.util.UUID;

public record IndexJobStatus(
        UUID jobId,
        String tenantId,
        String knowledgeBase,
        String status, // SUBMITTED, SCANNING, INDEXING, COMPLETED, FAILED
        int totalFiles,
        int processedFiles,
        int totalChunks,
        int progressPercent,
        String currentFile,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static IndexJobStatus initial(UUID jobId, String tenantId, String knowledgeBase) {
        return new IndexJobStatus(
                jobId, tenantId, knowledgeBase, "SUBMITTED",
                0, 0, 0, 0, "", null, LocalDateTime.now(), null
        );
    }

    public IndexJobStatus withProgress(String status, int totalFiles, int processedFiles, int totalChunks, String currentFile) {
        int percent = totalFiles > 0 ? (int) Math.min(100, Math.round(((double) processedFiles / totalFiles) * 100)) : 0;
        return new IndexJobStatus(
                jobId, tenantId, knowledgeBase, status,
                totalFiles, processedFiles, totalChunks, percent, currentFile, null, startedAt, null
        );
    }

    public IndexJobStatus completed(int totalFiles, int totalChunks) {
        return new IndexJobStatus(
                jobId, tenantId, knowledgeBase, "COMPLETED",
                totalFiles, totalFiles, totalChunks, 100, "", null, startedAt, LocalDateTime.now()
        );
    }

    public IndexJobStatus failed(String error) {
        return new IndexJobStatus(
                jobId, tenantId, knowledgeBase, "FAILED",
                totalFiles, processedFiles, totalChunks, progressPercent, currentFile, error, startedAt, LocalDateTime.now()
        );
    }
}
