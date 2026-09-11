package br.com.mv.cccopilotpropertie.index;


import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultIndexService implements IndexService {

    private final FileScannerService scanner;
    private final ChunkService chunker;
    private final EmbeddingService embedder;
    private final EmbeddingRepository repo;
    private final Path allowedBasePath;
    private final java.util.concurrent.ConcurrentHashMap<UUID, IndexJobStatus> jobs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, UUID> latestJobByKb = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> cancelledJobs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, java.util.concurrent.Future<?>> runningFutures = new java.util.concurrent.ConcurrentHashMap<>();

    public DefaultIndexService(
            FileScannerService scanner,
            ChunkService chunker,
            EmbeddingService embedder,
            EmbeddingRepository repo,
            @Value("${indexer.base-path}") String allowedBasePath
    ) {
        this.scanner = scanner;
        this.chunker = chunker;
        this.embedder = embedder;
        this.repo = repo;
        this.allowedBasePath = Path.of(allowedBasePath).toAbsolutePath().normalize();
    }

    @Override
    public IndexResult indexPath(String rootPath, String knowledgeBase) throws IOException {
        return indexPath("default", rootPath, knowledgeBase);
    }

    @Override
    public IndexResult indexPath(String tenantId, String rootPath, String knowledgeBase) throws IOException {

        Path root = Path.of(rootPath).normalize().toAbsolutePath();

        if (!root.startsWith(allowedBasePath)) {
            throw new IllegalArgumentException("Caminho fora da área permitida para indexação: " + root);
        }

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Caminho não existe: " + root);
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Caminho não é um diretório: " + root);
        }

        UUID jobId = UUID.randomUUID();
        int fileCount = 0;
        int chunkCount = 0;

        repo.deleteByKnowledgeBase(tenantId, knowledgeBase);

        List<PendingChunk> pending = new ArrayList<>();
        for (Path file : scanner.scan(root)) {
            fileCount++;

            String content;
            try {
                content = Files.readString(file);
            } catch (Exception e) {
                continue;
            }

            for (String chunk : chunker.chunk(content)) {
                chunkCount++;
                pending.add(new PendingChunk(file.toString(), chunk));
                if (pending.size() >= BATCH_SIZE) {
                    flushBatch(pending, tenantId, knowledgeBase);
                }
            }
        }
        flushBatch(pending, tenantId, knowledgeBase);

        return new IndexResult(jobId, fileCount, chunkCount);
    }

    @Override
    public IndexJobStatus indexAsync(String tenantId, String rootPath, String knowledgeBase) {
        Path root = Path.of(rootPath).normalize().toAbsolutePath();

        if (!root.startsWith(allowedBasePath)) {
            throw new IllegalArgumentException("Caminho fora da área permitida para indexação: " + root);
        }

        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Caminho não existe: " + root);
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Caminho não é um diretório: " + root);
        }

        UUID jobId = UUID.randomUUID();
        IndexJobStatus initial = IndexJobStatus.initial(jobId, tenantId, knowledgeBase);
        jobs.put(jobId, initial);
        latestJobByKb.put(knowledgeBase, jobId);

        java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                    jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                    return;
                }

                updateProgress(jobId, "SCANNING", 0, 0, 0, "Varrendo arquivos do projeto...");

                List<Path> files = scanner.scan(root);
                int totalFiles = files.size();

                if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                    jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                    return;
                }

                repo.deleteByKnowledgeBase(tenantId, knowledgeBase);

                int fileCount = 0;
                int chunkCount = 0;
                List<PendingChunk> pending = new ArrayList<>();

                for (Path file : files) {
                    if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                        jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                        return;
                    }

                    fileCount++;
                    String fileName = file.getFileName().toString();
                    updateProgress(jobId, "INDEXING", totalFiles, fileCount, chunkCount, fileName);

                    String content;
                    try {
                        content = Files.readString(file);
                    } catch (Exception e) {
                        continue;
                    }

                    for (String chunk : chunker.chunk(content)) {
                        if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                            jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                            return;
                        }

                        chunkCount++;
                        pending.add(new PendingChunk(file.toString(), chunk));
                        if (pending.size() >= BATCH_SIZE) {
                            flushBatch(pending, tenantId, knowledgeBase);
                            if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                                jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                                return;
                            }
                            updateProgress(jobId, "INDEXING", totalFiles, fileCount, chunkCount, fileName);
                        }
                    }
                }
                if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                    jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                    return;
                }

                flushBatch(pending, tenantId, knowledgeBase);

                if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted() || (jobs.containsKey(jobId) && "CANCELLED".equals(jobs.get(jobId).status()))) {
                    jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                    return;
                }

                final int finalChunkCount = chunkCount;
                jobs.computeIfPresent(jobId, (id, current) -> {
                    if ("CANCELLED".equals(current.status())) return current;
                    return current.completed(totalFiles, finalChunkCount);
                });
            } catch (Exception e) {
                if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted() || (jobs.containsKey(jobId) && "CANCELLED".equals(jobs.get(jobId).status()))) {
                    jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                } else {
                    jobs.computeIfPresent(jobId, (id, current) -> {
                        if ("CANCELLED".equals(current.status())) return current;
                        return current.failed(e.getMessage() != null ? e.getMessage() : "Erro inesperado");
                    });
                }
            } finally {
                runningFutures.remove(jobId);
                cancelledJobs.remove(jobId);
            }
        });

        runningFutures.put(jobId, future);

        return initial;
    }

    private void updateProgress(UUID jobId, String stage, int total, int processed, int chunks, String file) {
        if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
            return;
        }
        jobs.computeIfPresent(jobId, (id, current) -> {
            if ("CANCELLED".equals(current.status())) {
                return current;
            }
            return current.withProgress(stage, total, processed, chunks, file);
        });
    }

    private static final int BATCH_SIZE = 100;
    private record PendingChunk(String path, String chunk) {}

    private void flushBatch(
            List<PendingChunk> batch,
            String tenantId,
            String knowledgeBase
    ) {
        if (batch.isEmpty()) return;

        List<String> texts = batch.stream()
                .map(PendingChunk::chunk)
                .toList();

        List<float[]> vectors = embedder.embedBatch(texts);

        List<EmbeddingRepository.EmbeddingRecord> records = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            PendingChunk item = batch.get(i);
            float[] vector = i < vectors.size() ? vectors.get(i) : new float[EmbeddingConfig.DIMENSION];
            records.add(new EmbeddingRepository.EmbeddingRecord(
                    UUID.randomUUID(),
                    tenantId,
                    knowledgeBase,
                    item.path(),
                    item.chunk(),
                    vector
            ));
        }

        repo.saveBatch(records);
        batch.clear();
    }

    @Override
    public java.util.Optional<IndexJobStatus> getJobStatus(UUID jobId) {
        return java.util.Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public java.util.Optional<IndexJobStatus> getLatestJobStatus(String knowledgeBase) {
        UUID latest = latestJobByKb.get(knowledgeBase);
        return latest != null ? getJobStatus(latest) : java.util.Optional.empty();
    }

    @Override
    public boolean cancelJob(UUID jobId) {
        IndexJobStatus current = jobs.get(jobId);
        if (current == null) {
            return false;
        }
        if ("COMPLETED".equals(current.status()) || "FAILED".equals(current.status()) || "CANCELLED".equals(current.status())) {
            return false;
        }

        cancelledJobs.add(jobId);
        jobs.put(jobId, current.cancelled("Indexação cancelada pelo usuário"));

        var future = runningFutures.remove(jobId);
        if (future != null) {
            future.cancel(true);
        }
        return true;
    }

    @Override
    public boolean cancelLatestJob(String knowledgeBase) {
        UUID latest = latestJobByKb.get(knowledgeBase);
        return latest != null && cancelJob(latest);
    }
}
