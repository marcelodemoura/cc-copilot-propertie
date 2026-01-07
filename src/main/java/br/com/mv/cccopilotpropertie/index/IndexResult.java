package br.com.mv.cccopilotpropertie.index;

import java.util.UUID;

public record IndexResult(
        UUID jobId,
        int fileCount,
        int chunkCount) {
}

