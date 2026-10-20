package br.com.mv.cccopilotpropertie.index;


import br.com.mv.cccopilotpropertie.config.EmbeddingConfig;
import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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

        Map<String, String> existingHashes = repo.findExistingFileHashes(tenantId, knowledgeBase);
        Set<String> seenPaths = new HashSet<>();
        List<PendingChunk> pending = new ArrayList<>();

        for (Path file : scanner.scan(root)) {
            fileCount++;
            String pathStr = file.toString();
            seenPaths.add(pathStr);

            String content;
            try {
                content = Files.readString(file);
            } catch (Exception e) {
                continue;
            }

            String hash = computeSha256(content);
            if (hash.equals(existingHashes.get(pathStr))) {
                continue;
            }

            if (existingHashes.containsKey(pathStr)) {
                repo.deleteByPath(tenantId, knowledgeBase, pathStr);
            }

            for (String chunk : chunker.chunk(content)) {
                chunkCount++;
                pending.add(new PendingChunk(pathStr, chunk, hash));
                if (pending.size() >= BATCH_SIZE) {
                    flushBatch(pending, tenantId, knowledgeBase);
                }
            }
        }
        flushBatch(pending, tenantId, knowledgeBase);

        for (String oldPath : existingHashes.keySet()) {
            if (!seenPaths.contains(oldPath)) {
                repo.deleteByPath(tenantId, knowledgeBase, oldPath);
            }
        }

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

                Map<String, String> existingHashes = repo.findExistingFileHashes(tenantId, knowledgeBase);
                Set<String> seenPaths = new HashSet<>();

                int fileCount = 0;
                int chunkCount = 0;
                List<PendingChunk> pending = new ArrayList<>();

                for (Path file : files) {
                    if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                        jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                        return;
                    }

                    fileCount++;
                    String pathStr = file.toString();
                    seenPaths.add(pathStr);
                    String fileName = file.getFileName().toString();
                    updateProgress(jobId, "INDEXING", totalFiles, fileCount, chunkCount, fileName);

                    String content;
                    try {
                        content = Files.readString(file);
                    } catch (Exception e) {
                        continue;
                    }

                    String hash = computeSha256(content);
                    if (hash.equals(existingHashes.get(pathStr))) {
                        continue;
                    }

                    if (existingHashes.containsKey(pathStr)) {
                        repo.deleteByPath(tenantId, knowledgeBase, pathStr);
                    }

                    for (String chunk : chunker.chunk(content)) {
                        if (cancelledJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                            jobs.computeIfPresent(jobId, (id, current) -> current.cancelled("Indexação cancelada pelo usuário"));
                            return;
                        }

                        chunkCount++;
                        pending.add(new PendingChunk(pathStr, chunk, hash));
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

                for (String oldPath : existingHashes.keySet()) {
                    if (!seenPaths.contains(oldPath)) {
                        repo.deleteByPath(tenantId, knowledgeBase, oldPath);
                    }
                }

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
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
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
    private record PendingChunk(String path, String chunk, String hash) {}

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
                    vector,
                    item.hash()
            ));
        }

        repo.saveBatch(records);
        batch.clear();
    }

    private String computeSha256(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(content.hashCode());
        }
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

    @Override
    public void reindexSingleFile(String tenantId, String knowledgeBase, Path file) {
        if (file == null || !Files.exists(file) || Files.isDirectory(file)) {
            return;
        }
        try {
            String pathStr = file.toString();
            repo.deleteByPath(tenantId, knowledgeBase, pathStr);
            String content = Files.readString(file);
            String hash = computeSha256(content);
            List<String> chunks = chunker.chunk(content);
            List<PendingChunk> pending = new ArrayList<>();
            for (String ch : chunks) {
                pending.add(new PendingChunk(pathStr, ch, hash));
            }
            flushBatch(pending, tenantId, knowledgeBase);
        } catch (Exception ignored) {
        }
    }
}
