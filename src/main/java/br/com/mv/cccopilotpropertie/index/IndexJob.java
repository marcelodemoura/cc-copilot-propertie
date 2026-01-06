package br.com.mv.cccopilotpropertie.index;

import java.util.UUID;

public record IndexJob(UUID jobId, int fileCount, int chunkCount) {
}