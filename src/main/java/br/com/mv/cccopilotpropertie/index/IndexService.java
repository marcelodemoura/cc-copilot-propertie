package br.com.mv.cccopilotpropertie.index;

import java.io.IOException;

public interface IndexService {

    IndexResult indexPath(String path, String knowledgeBase) throws IOException;

    IndexResult indexPath(String tenantId, String path, String knowledgeBase) throws IOException;

    IndexJobStatus indexAsync(String tenantId, String path, String knowledgeBase);

    java.util.Optional<IndexJobStatus> getJobStatus(java.util.UUID jobId);

    java.util.Optional<IndexJobStatus> getLatestJobStatus(String knowledgeBase);
}
