package br.com.mv.cccopilotpropertie.index;


import br.com.mv.cccopilotpropertie.embedding.EmbeddingService;
import br.com.mv.cccopilotpropertie.vector.EmbeddingRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class DefaultIndexService implements IndexService {

    private final FileScannerService scanner;
    private final ChunkService chunker;
    private final EmbeddingService embedder;
    private final EmbeddingRepository repo;

    public DefaultIndexService(FileScannerService scanner,
                               ChunkService chunker,
                               EmbeddingService embedder,
                               EmbeddingRepository repo) {
        this.scanner = scanner;
        this.chunker = chunker;
        this.embedder = embedder;
        this.repo = repo;
    }

    @Override
    public IndexResult indexPath(String rootPath) throws IOException {

        Path root = Path.of(rootPath).normalize().toAbsolutePath();
        UUID jobId = UUID.randomUUID();

        int fileCount = 0;
        int chunkCount = 0;

        for (Path file : scanner.scan(root)) {
            fileCount++;
            String content = Files.readString(file);

            for (String chunk : chunker.chunk(content)) {
                chunkCount++;
                repo.save(jobId, file.toString(), chunk, embedder.embed(chunk));
            }
        }

        return new IndexResult(jobId, fileCount, chunkCount);
    }
}